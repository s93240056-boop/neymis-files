package com.neymis.files;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {

    private File current;
    private File selected;
    private final List<File> files = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView pathView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pathView = findViewById(R.id.path);
        ListView listView = findViewById(R.id.list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);

        current = Environment.getExternalStorageDirectory();
        show(current);

        listView.setOnItemClickListener((parent, view, position, id) -> {
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
        if (list != null) files.addAll(Arrays.asList(list));
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        names.clear();
        names.add("..");
        for (File f : files) names.add(label(f));
        adapter.notifyDataSetChanged();
        pathView.setText(dir.getAbsolutePath());
    }

    private static String label(File file) {
        return (file.isDirectory() ? "[DIR] " : "") + file.getName();
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
        if (!(info instanceof AdapterView.AdapterContextMenuInfo)) return;
        int position = ((AdapterView.AdapterContextMenuInfo) info).position;
        if (position == 0) return;
        selected = files.get(position - 1);
        menu.setHeaderTitle(selected.getName());
        menu.add(0, 1, 0, R.string.rename);
        menu.add(0, 2, 1, R.string.delete);
        if (selected.getName().toLowerCase().endsWith(".zip")) menu.add(0, 3, 2, R.string.extract);
        if (selected.getName().toLowerCase().endsWith(".apk")) menu.add(0, 4, 3, R.string.install);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (selected == null) return super.onContextItemSelected(item);
        switch (item.getItemId()) {
            case 1: rename(selected); return true;
            case 2: delete(selected); return true;
            case 3: extract(selected); return true;
            case 4: open(selected); return true;
            default: return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 10, 0, R.string.create_folder);
        menu.add(0, 11, 1, R.string.search);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 10) { createFolder(); return true; }
        if (item.getItemId() == 11) { search(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private interface TextAction {
        void run(String text);
    }

    private void prompt(int titleRes, String initial, TextAction action) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(initial == null ? "" : initial);
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok,
                        (dialog, which) -> action.run(input.getText().toString().trim()))
                .show();
    }

    private void createFolder() {
        prompt(R.string.create_folder, "", name -> {
            if (name.isEmpty()) return;
            if (new File(current, name).mkdirs()) show(current);
            else toast("Не удалось создать папку");
        });
    }

    private void rename(File file) {
        prompt(R.string.rename, file.getName(), name -> {
            if (name.isEmpty()) return;
            if (file.renameTo(new File(current, name))) show(current);
            else toast("Не удалось переименовать");
        });
    }

    private void delete(File file) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(file.getName())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (deleteRecursive(file)) { show(current); toast("Удалено"); }
                    else toast("Не удалось удалить");
                })
                .show();
    }

    private static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        return file.delete();
    }

    private void search() {
        prompt(R.string.search, "", query -> {
            if (query.isEmpty()) { show(current); return; }
            files.clear();
            File[] list = current.listFiles();
            if (list != null) {
                for (File f : list) {
                    if (f.getName().toLowerCase().contains(query.toLowerCase())) files.add(f);
                }
            }
            names.clear();
            names.add("..");
            for (File f : files) names.add(label(f));
            adapter.notifyDataSetChanged();
            pathView.setText("Поиск: " + query);
        });
    }

    private void extract(File zip) {
        File dest = new File(zip.getParentFile(), zip.getName().replaceAll("\\.zip$", ""));
        if (!dest.isDirectory() && !dest.mkdirs()) { toast("Не удалось создать папку"); return; }
        byte[] buffer = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(new java.io.FileInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(dest, entry.getName());
                if (!out.getCanonicalPath().startsWith(dest.getCanonicalPath() + File.separator)) {
                    toast("Недопустимый путь в архиве");
                    continue;
                }
                if (entry.isDirectory()) { out.mkdirs(); continue; }
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    int count;
                    while ((count = zis.read(buffer)) > 0) os.write(buffer, 0, count);
                }
            }
            show(current);
            toast("Распаковано: " + dest.getName());
        } catch (IOException e) {
            toast("Ошибка распаковки: " + e.getMessage());
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
