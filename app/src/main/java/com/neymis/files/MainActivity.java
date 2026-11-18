package com.neymis.files;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "neymis_files";
    private static final String KEY_THEME = "theme";
    private static final String KEY_SORT = "sort";
    private static final String KEY_ASC = "asc";
    private static final String KEY_HIDDEN = "hidden";

    private SharedPreferences prefs;
    private File current;
    private File selectedFile;
    private final List<File> files = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final Set<File> selected = new HashSet<>();
    private final List<File> clipboard = new ArrayList<>();
    private boolean cut = false;
    private boolean selectionMode = false;
    private int sortBy;
    private boolean sortAsc;
    private boolean showHidden;
    private ArrayAdapter<String> adapter;
    private TextView pathView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        applyTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sortBy = prefs.getInt(KEY_SORT, 0);
        sortAsc = prefs.getBoolean(KEY_ASC, true);
        showHidden = prefs.getBoolean(KEY_HIDDEN, false);

        pathView = findViewById(R.id.path);
        ListView listView = findViewById(R.id.list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);

        current = Environment.getExternalStorageDirectory();
        show(current);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (selectionMode && position > 0) {
                toggleSelected(files.get(position - 1));
                return;
            }
            if (position == 0) {
                File parentFile = current.getParentFile();
                if (parentFile != null) show(parentFile);
                return;
            }
            File file = files.get(position - 1);
            if (file.isDirectory()) show(file);
            else open(file);
        });

        registerForContextMenu(listView);
        requestStorageAccess();
    }

    private void applyTheme() {
        int theme = 0;
        try {
            theme = Integer.parseInt(prefs.getString(KEY_THEME, "0"));
        } catch (Exception ignored) {
        }
        if (theme == 1) setTheme(R.style.AppTheme_Light);
        else if (theme == 2) setTheme(R.style.AppTheme_Dark);
        else setTheme(R.style.AppTheme_Gradient);
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) return;
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void show(File dir) {
        current = dir;
        File[] list = dir.listFiles();
        files.clear();
        if (list != null) {
            for (File file : list) {
                if (!showHidden && file.getName().startsWith(".")) continue;
                files.add(file);
            }
        }
        sortFiles();
        refreshNames();
        pathView.setText(dir.getAbsolutePath());
    }

    private void sortFiles() {
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                int result;
                if (sortBy == 1) result = Long.compare(FileUtils.size(a), FileUtils.size(b));
                else if (sortBy == 2) result = Long.compare(a.lastModified(), b.lastModified());
                else result = a.getName().compareToIgnoreCase(b.getName());
                return sortAsc ? result : -result;
            }
        });
    }

    private void refreshNames() {
        names.clear();
        names.add("..");
        for (File file : files) {
            String mark = selected.contains(file) ? "✓ " : "";
            names.add(mark + (file.isDirectory() ? "[DIR] " : "") + file.getName());
        }
        adapter.notifyDataSetChanged();
    }

    private void toggleSelected(File file) {
        if (selected.contains(file)) selected.remove(file);
        else selected.add(file);
        refreshNames();
    }

    private void open(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mime(file.getName()));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (Exception e) {
            toast("Нет приложения для этого файла");
        }
    }

    private static String mime(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".txt") || lower.endsWith(".md")) return "text/plain";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "*/*";
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, v, info);
        if (selectionMode || !(info instanceof AdapterView.AdapterContextMenuInfo)) return;
        int position = ((AdapterView.AdapterContextMenuInfo) info).position;
        if (position == 0) return;
        selectedFile = files.get(position - 1);
        menu.setHeaderTitle(selectedFile.getName());
        menu.add(0, 1, 0, "Открыть");
        menu.add(0, 2, 1, "Поделиться");
        menu.add(0, 3, 2, "Копировать");
        menu.add(0, 4, 3, "Вырезать");
        menu.add(0, 5, 4, "Переименовать");
        menu.add(0, 6, 5, "Удалить");
        menu.add(0, 7, 6, "Архивировать в ZIP");
        menu.add(0, 8, 7, "Свойства");
        if (selectedFile.isFile() && isText(selectedFile.getName())) menu.add(0, 9, 8, "Редактировать");
        if (selectedFile.getName().toLowerCase().endsWith(".zip")) menu.add(0, 10, 9, "Распаковать");
        if (selectedFile.getName().toLowerCase().endsWith(".apk")) menu.add(0, 11, 10, "Установить APK");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (selectedFile == null) return super.onContextItemSelected(item);
        File file = selectedFile;
        switch (item.getItemId()) {
            case 1: open(file); return true;
            case 2: share(file); return true;
            case 3: setClipboard(file, false); return true;
            case 4: setClipboard(file, true); return true;
            case 5: rename(file); return true;
            case 6: delete(file); return true;
            case 7: zipFiles(Arrays.asList(file)); return true;
            case 8: properties(file); return true;
            case 9: editText(file); return true;
            case 10: unzip(file); return true;
            case 11: open(file); return true;
            default: return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 10, 0, "Создать папку").setVisible(true);
        menu.add(0, 11, 1, "Создать файл");
        menu.add(0, 12, 2, "Поиск здесь");
        menu.add(0, 13, 3, "Поиск везде");
        menu.add(0, 14, 4, "Сортировка");
        menu.add(0, 15, 5, "Скрытые файлы");
        menu.add(0, 16, 6, "Избранное");
        menu.add(0, 17, 7, "Память");
        menu.add(0, 18, 8, "Приложения");
        menu.add(0, 19, 9, "Тема");
        menu.add(0, 20, 10, "Выбрать несколько");
        menu.add(0, 22, 11, "Вставить");
        menu.add(1, 30, 12, "Копировать");
        menu.add(1, 31, 13, "Вырезать");
        menu.add(1, 32, 14, "Удалить");
        menu.add(1, 33, 15, "Архивировать в ZIP");
        menu.add(1, 34, 16, "Поделиться");
        menu.add(1, 35, 17, "Отменить выбор");
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        for (int i = 10; i <= 22; i++) {
            MenuItem item = menu.findItem(i);
            if (item != null) {
                boolean visible = !(i == 22 && clipboard.isEmpty());
                item.setVisible(!selectionMode && visible);
            }
        }
        for (int i = 30; i <= 35; i++) {
            MenuItem item = menu.findItem(i);
            if (item != null) item.setVisible(selectionMode);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 10: createFolder(); return true;
            case 11: createFile(); return true;
            case 12: search(false); return true;
            case 13: search(true); return true;
            case 14: chooseSort(); return true;
            case 15: toggleHidden(); return true;
            case 16: favorites(); return true;
            case 17: storageInfo(); return true;
            case 18: appManager(); return true;
            case 19: chooseTheme(); return true;
            case 20: setSelectionMode(true); return true;
            case 22: paste(); return true;
            case 30: copySelection(false); return true;
            case 31: copySelection(true); return true;
            case 32: deleteSelection(); return true;
            case 33: zipFiles(new ArrayList<>(selected)); return true;
            case 34: shareSelection(); return true;
            case 35: setSelectionMode(false); return true;
            default: return super.onOptionsItemSelected(item);
        }
    }

    private void setSelectionMode(boolean on) {
        selectionMode = on;
        selected.clear();
        refreshNames();
        invalidateOptionsMenu();
        if (on) toast("Выбери файлы, затем действие в меню");
    }

    private interface TextAction {
        void run(String text);
    }

    private void prompt(String title, String initial, TextAction action) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(initial == null ? "" : initial);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("OK", (dialog, which) -> action.run(input.getText().toString().trim()))
                .show();
    }

    private void createFolder() {
        prompt("Новая папка", "", name -> {
            if (name.isEmpty()) return;
            if (new File(current, name).mkdirs()) show(current);
            else toast("Не удалось создать папку");
        });
    }

    private void createFile() {
        prompt("Новый файл", "", name -> {
            if (name.isEmpty()) return;
            try {
                File file = new File(current, name);
                if (file.createNewFile()) { show(current); editText(file); }
                else toast("Файл уже существует");
            } catch (IOException e) {
                toast("Не удалось создать файл");
            }
        });
    }

    private void rename(File file) {
        prompt("Переименовать", file.getName(), name -> {
            if (name.isEmpty()) return;
            if (file.renameTo(new File(current, name))) show(current);
            else toast("Не удалось переименовать");
        });
    }

    private void delete(File file) {
        confirm("Удалить " + file.getName() + "?", () -> {
            if (FileUtils.delete(file)) { show(current); toast("Удалено"); }
            else toast("Не удалось удалить");
        });
    }

    private void deleteSelection() {
        if (selected.isEmpty()) return;
        confirm("Удалить выбранные (" + selected.size() + ")?", () -> {
            int ok = 0;
            for (File file : selected) if (FileUtils.delete(file)) ok++;
            toast("Удалено: " + ok);
            setSelectionMode(false);
            show(current);
        });
    }

    private void confirm(String message, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle("Подтверждение")
                .setMessage(message)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("OK", (dialog, which) -> action.run())
                .show();
    }

    private void setClipboard(File file, boolean cutMode) {
        clipboard.clear();
        clipboard.add(file);
        cut = cutMode;
        invalidateOptionsMenu();
        toast((cutMode ? "Вырезано" : "Скопировано") + ": " + file.getName());
    }

    private void copySelection(boolean cutMode) {
        if (selected.isEmpty()) return;
        clipboard.clear();
        clipboard.addAll(selected);
        cut = cutMode;
        invalidateOptionsMenu();
        setSelectionMode(false);
        toast((cutMode ? "Вырезано" : "Скопировано") + ": " + clipboard.size());
    }

    private void paste() {
        if (clipboard.isEmpty()) return;
        int ok = 0;
        for (File source : clipboard) {
            File target = FileUtils.uniqueDestination(current, source.getName());
            boolean done = cut ? FileUtils.move(source, target) : copyQuietly(source, target);
            if (done) ok++;
        }
        boolean wasCut = cut;
        clipboard.clear();
        cut = false;
        invalidateOptionsMenu();
        show(current);
        toast((wasCut ? "Перемещено" : "Скопировано") + ": " + ok);
    }

    private boolean copyQuietly(File source, File target) {
        try {
            return FileUtils.copy(source, target);
        } catch (IOException e) {
            return false;
        }
    }

    private void zipFiles(List<File> sources) {
        if (sources.isEmpty()) return;
        prompt("Имя архива", "archive.zip", name -> {
            if (!name.toLowerCase().endsWith(".zip")) name += ".zip";
            File output = FileUtils.uniqueDestination(current, name);
            try {
                FileUtils.zip(sources, output);
                setSelectionMode(false);
                show(current);
                toast("Архив создан: " + output.getName());
            } catch (IOException e) {
                toast("Не удалось создать архив");
            }
        });
    }

    private void unzip(File file) {
        File dest = FileUtils.uniqueDestination(file.getParentFile(),
                file.getName().replaceAll("\\.zip$", ""));
        try {
            int count = FileUtils.unzip(file, dest);
            if (count < 0) toast("Не удалось создать папку");
            else { show(current); toast("Распаковано файлов: " + count); }
        } catch (IOException e) {
            toast("Ошибка распаковки: " + e.getMessage());
        }
    }

    private void share(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mime(file.getName()));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Поделиться"));
    }

    private void shareSelection() {
        if (selected.isEmpty()) return;
        if (selected.size() == 1) { share(selected.iterator().next()); return; }
        ArrayList<Uri> uris = new ArrayList<>();
        for (File file : selected) {
            uris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file));
        }
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("*/*");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Поделиться"));
    }

    private void properties(File file) {
        String modified = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(new Date(file.lastModified()));
        String text = "Имя: " + file.getName()
                + "\nПуть: " + file.getAbsolutePath()
                + "\nТип: " + (file.isDirectory() ? "папка" : "файл")
                + "\nРазмер: " + FileUtils.human(FileUtils.size(file))
                + "\nИзменён: " + modified
                + "\nЧтение: " + file.canRead()
                + "\nЗапись: " + file.canWrite();
        new AlertDialog.Builder(this).setTitle("Свойства").setMessage(text)
                .setPositiveButton("OK", null).show();
    }

    private static boolean isText(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md")
                || lower.endsWith(".log") || lower.endsWith(".json")
                || lower.endsWith(".xml") || lower.endsWith(".csv");
    }

    private void editText(File file) {
        String content;
        try {
            content = FileUtils.readText(file);
        } catch (IOException e) {
            toast("Не удалось прочитать файл");
            return;
        }
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(8);
        input.setText(content);
        new AlertDialog.Builder(this).setTitle("Редактор: " + file.getName())
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    try {
                        FileUtils.writeText(file, input.getText().toString());
                        toast("Сохранено");
                    } catch (IOException e) {
                        toast("Не удалось сохранить");
                    }
                }).show();
    }

    private void search(boolean everywhere) {
        prompt("Поиск", "", query -> {
            if (query.isEmpty()) return;
            File root = everywhere ? Environment.getExternalStorageDirectory() : current;
            List<File> result = FileUtils.search(root, query, 300);
            files.clear();
            files.addAll(result);
            names.clear();
            names.add("..");
            for (File file : files) {
                names.add((file.isDirectory() ? "[DIR] " : "") + file.getName());
            }
            adapter.notifyDataSetChanged();
            pathView.setText("Результаты поиска: " + query + " (" + result.size() + ")");
        });
    }

    private void chooseSort() {
        String[] variants = {"Имя ↑", "Имя ↓", "Размер ↑", "Размер ↓", "Дата ↑", "Дата ↓"};
        new AlertDialog.Builder(this).setTitle("Сортировка").setItems(variants, (dialog, which) -> {
            sortBy = which / 2;
            sortAsc = which % 2 == 0;
            prefs.edit().putInt(KEY_SORT, sortBy).putBoolean(KEY_ASC, sortAsc).apply();
            show(current);
        }).show();
    }

    private void toggleHidden() {
        showHidden = !showHidden;
        prefs.edit().putBoolean(KEY_HIDDEN, showHidden).apply();
        toast(showHidden ? "Скрытые файлы видны" : "Скрытые файлы скрыты");
        show(current);
    }

    private void favorites() {
        File root = Environment.getExternalStorageDirectory();
        final File[] places = {
                root,
                new File(root, "Download"),
                new File(root, "DCIM"),
                new File(root, "Pictures"),
                new File(root, "Documents"),
                new File(root, "Music"),
                new File(root, "Movies"),
                getExternalFilesDir(null)
        };
        String[] labels = new String[places.length];
        for (int i = 0; i < places.length; i++) {
            labels[i] = places[i] == null ? "—" : places[i].getAbsolutePath();
        }
        new AlertDialog.Builder(this).setTitle("Избранное").setItems(labels, (dialog, which) -> {
            if (places[which] != null) show(places[which]);
        }).show();
    }

    private void storageInfo() {
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        long total = stat.getTotalBytes();
        long available = stat.getAvailableBytes();
        String text = "Всего: " + FileUtils.human(total)
                + "\nСвободно: " + FileUtils.human(available)
                + "\nЗанято: " + FileUtils.human(total - available)
                + "\nПуть: " + path.getAbsolutePath();
        new AlertDialog.Builder(this).setTitle("Память").setMessage(text)
                .setPositiveButton("OK", null).show();
    }

    private void appManager() {
        List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
        Collections.sort(apps, (a, b) -> {
            String la = getPackageManager().getApplicationLabel(a).toString();
            String lb = getPackageManager().getApplicationLabel(b).toString();
            return la.compareToIgnoreCase(lb);
        });
        final List<ApplicationInfo> limited = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if (limited.size() >= 200) break;
            limited.add(app);
        }
        String[] labels = new String[limited.size()];
        for (int i = 0; i < limited.size(); i++) {
            labels[i] = getPackageManager().getApplicationLabel(limited.get(i)).toString();
        }
        new AlertDialog.Builder(this).setTitle("Приложения (" + limited.size() + ")")
                .setItems(labels, (dialog, which) -> appActions(limited.get(which))).show();
    }

    private void appActions(ApplicationInfo app) {
        String label = getPackageManager().getApplicationLabel(app).toString();
        String[] actions = {"Сохранить APK в Download", "Открыть приложение"};
        new AlertDialog.Builder(this).setTitle(label).setItems(actions, (dialog, which) -> {
            if (which == 0) {
                File dir = new File(Environment.getExternalStorageDirectory(), "NeymisFiles/APK");
                dir.mkdirs();
                File target = FileUtils.uniqueDestination(dir, app.packageName + ".apk");
                if (copyQuietly(new File(app.sourceDir), target)) toast("APK сохранён: " + target.getName());
                else toast("Не удалось сохранить APK");
            } else {
                Intent launch = getPackageManager().getLaunchIntentForPackage(app.packageName);
                if (launch != null) startActivity(launch);
                else toast("Приложение нельзя запустить");
            }
        }).show();
    }

    private void chooseTheme() {
        String[] variants = {"Фиолетово-синий градиент", "Белая", "Тёмная"};
        new AlertDialog.Builder(this).setTitle("Тема приложения")
                .setItems(variants, (dialog, which) -> {
                    prefs.edit().putString(KEY_THEME, String.valueOf(which)).apply();
                    recreate();
                }).show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
