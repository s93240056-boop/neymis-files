package com.neymis.files;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CleanerActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private File root;
    private LinearLayout rootView;
    private Trash trash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("neymis_files", MODE_PRIVATE);
        int theme = 0;
        try {
            theme = Integer.parseInt(prefs.getString("theme", "0"));
        } catch (Exception ignored) {
        }
        if (theme == 1) setTheme(R.style.AppTheme_Light);
        else if (theme == 2) setTheme(R.style.AppTheme_Dark);
        else if (theme == 3) setTheme(R.style.AppTheme_System);
        else setTheme(R.style.AppTheme_Gradient);
        super.onCreate(savedInstanceState);
        setTitle("Очистка памяти");

        root = Environment.getExternalStorageDirectory();
        trash = new Trash(prefs);

        ScrollView scroll = new ScrollView(this);
        setContentView(scroll);
        rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(rootView);

        TextView title = new TextView(this);
        title.setText("Очистка памяти");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTextColor(textColor());
        rootView.addView(title);

        card("Кэш и временные файлы", cacheInfo(), "Очистить кэш", v -> cleanCache());
        card("Дубликаты файлов", duplicateInfo(), "Удалить копии", v -> cleanDuplicates());
        card("Самые крупные файлы", largeInfo(), "Показать", v -> showLarge());
        card("Старые скриншоты", screenshotInfo(), "Удалить старые", v -> cleanScreenshots());
        card("Медиа мессенджеров", messengerInfo(), "Показать", v -> showMessengers());
    }

    private void card(String title, String subtitle, String action, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(cardBackground());
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        box.setLayoutParams(params);

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        name.setTextColor(textColor());
        box.addView(name);

        TextView detail = new TextView(this);
        detail.setText(subtitle);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        detail.setTextColor(subColor());
        detail.setPadding(0, dp(4), 0, dp(10));
        box.addView(detail);

        TextView button = new TextView(this);
        button.setText(action);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTextColor(accentColor());
        button.setPadding(0, dp(6), 0, 0);
        button.setOnClickListener(listener);
        box.addView(button);

        rootView.addView(box);
    }

    private List<File> scan(File dir, int depth) {
        List<File> result = new ArrayList<>();
        if (depth <= 0 || !dir.isDirectory()) return result;
        File[] children = dir.listFiles();
        if (children == null) return result;
        for (File child : children) {
            if (child.getName().startsWith(".")) continue;
            result.add(child);
            if (child.isDirectory()) result.addAll(scan(child, depth - 1));
        }
        return result;
    }

    private List<File> cacheFolders() {
        List<File> result = new ArrayList<>();
        for (File file : scan(root, 4)) {
            if (!file.isDirectory()) continue;
            String name = file.getName().toLowerCase();
            if (name.equals("cache") || name.equals(".cache") || name.equals("tmp")) result.add(file);
        }
        return result;
    }

    private String cacheInfo() {
        long total = 0;
        for (File dir : cacheFolders()) total += FileUtils.size(dir);
        return cacheFolders().size() + " папок · " + FileUtils.human(total);
    }

    private void cleanCache() {
        final List<File> folders = cacheFolders();
        if (folders.isEmpty()) { toast("Кэш не найден"); return; }
        confirm("Удалить " + folders.size() + " папок кэша?", () -> {
            int ok = 0;
            for (File dir : folders) if (FileUtils.delete(dir)) ok++;
            toast("Удалено папок: " + ok);
            recreate();
        });
    }

    private Map<String, List<File>> duplicateGroups() {
        List<File> candidates = new ArrayList<>();
        for (String folder : new String[]{"Download", "DCIM", "Pictures", "Documents"}) {
            candidates.addAll(scan(new File(root, folder), 4));
        }
        Map<Long, List<File>> bySize = new HashMap<>();
        for (File file : candidates) {
            if (!file.isFile() || file.length() < 102400) continue;
            bySize.computeIfAbsent(file.length(), k -> new ArrayList<>()).add(file);
        }
        Map<String, List<File>> groups = new HashMap<>();
        for (List<File> sameSize : bySize.values()) {
            if (sameSize.size() < 2) continue;
            for (File file : sameSize) {
                String hash = md5(file);
                if (hash == null) continue;
                groups.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);
            }
        }
        Map<String, List<File>> real = new HashMap<>();
        for (Map.Entry<String, List<File>> entry : groups.entrySet()) {
            if (entry.getValue().size() > 1) real.put(entry.getKey(), entry.getValue());
        }
        return real;
    }

    private static String md5(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = in.read(buffer)) > 0) digest.update(buffer, 0, count);
            }
            byte[] raw = digest.digest();
            StringBuilder builder = new StringBuilder();
            for (byte b : raw) builder.append(String.format("%02x", b));
            return builder.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String duplicateInfo() {
        Map<String, List<File>> groups = duplicateGroups();
        long wasted = 0;
        for (List<File> group : groups.values()) {
            long size = group.get(0).length();
            wasted += size * (group.size() - 1);
        }
        return groups.size() + " групп · можно освободить " + FileUtils.human(wasted);
    }

    private void cleanDuplicates() {
        Map<String, List<File>> groups = duplicateGroups();
        if (groups.isEmpty()) { toast("Дубликаты не найдены"); return; }
        confirm("Удалить копии, оставив по одному файлу из каждой группы?", () -> {
            int removed = 0;
            for (List<File> group : groups.values()) {
                for (int i = 1; i < group.size(); i++) {
                    trash.put(group.get(i));
                    removed++;
                }
            }
            toast("В корзину перемещено: " + removed);
            recreate();
        });
    }

    private List<File> largeFiles() {
        List<File> files = new ArrayList<>();
        for (String folder : new String[]{"Download", "DCIM", "Movies", "Pictures", "Music", "Documents"}) {
            files.addAll(scan(new File(root, folder), 5));
        }
        Collections.sort(files, (a, b) -> Long.compare(b.length(), a.length()));
        List<File> result = new ArrayList<>();
        for (File file : files) {
            if (file.isFile()) result.add(file);
            if (result.size() >= 20) break;
        }
        return result;
    }

    private String largeInfo() {
        List<File> files = largeFiles();
        if (files.isEmpty()) return "Файлы не найдены";
        return "Топ-20 · самый большой: " + FileUtils.human(files.get(0).length());
    }

    private void showLarge() {
        final List<File> files = largeFiles();
        String[] labels = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            labels[i] = files.get(i).getName() + " · " + FileUtils.human(files.get(i).length());
        }
        new AlertDialog.Builder(this).setTitle("Крупные файлы").setItems(labels, (dialog, which) -> {
            File file = files.get(which);
            String[] actions = {"Удалить в корзину", "Поделиться", "Свойства"};
            new AlertDialog.Builder(this).setTitle(file.getName()).setItems(actions, (d, w) -> {
                if (w == 0) { trash.put(file); toast("Перемещено в корзину"); recreate(); }
                else if (w == 1) {
                    android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                            this, getPackageName() + ".fileprovider", file);
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    intent.setType("*/*");
                    intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
                    intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(android.content.Intent.createChooser(intent, "Поделиться"));
                } else {
                    toast(file.getAbsolutePath() + "\n" + FileUtils.human(file.length()));
                }
            }).show();
        }).show();
    }

    private List<File> oldScreenshots() {
        long limit = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
        List<File> result = new ArrayList<>();
        for (String folder : new String[]{"DCIM/Screenshots", "Pictures/Screenshots"}) {
            File[] children = new File(root, folder).listFiles();
            if (children == null) continue;
            for (File file : children) if (file.isFile() && file.lastModified() < limit) result.add(file);
        }
        return result;
    }

    private String screenshotInfo() {
        List<File> files = oldScreenshots();
        return files.size() + " скриншотов старше 30 дней";
    }

    private void cleanScreenshots() {
        final List<File> files = oldScreenshots();
        if (files.isEmpty()) { toast("Старых скриншотов нет"); return; }
        confirm("Удалить " + files.size() + " старых скриншотов в корзину?", () -> {
            for (File file : files) trash.put(file);
            toast("Перемещено в корзину: " + files.size());
            recreate();
        });
    }

    private List<File> messengerMedia() {
        List<File> result = new ArrayList<>();
        for (String folder : new String[]{
                "Android/media/com.whatsapp/WhatsApp/Media",
                "WhatsApp/Media",
                "Telegram/Telegram Images",
                "Telegram/Telegram Video"}) {
            File dir = new File(root, folder);
            if (dir.isDirectory()) result.add(dir);
        }
        return result;
    }

    private String messengerInfo() {
        List<File> dirs = messengerMedia();
        long total = 0;
        for (File dir : dirs) total += FileUtils.size(dir);
        return dirs.size() + " папок · " + FileUtils.human(total);
    }

    private void showMessengers() {
        final List<File> dirs = messengerMedia();
        if (dirs.isEmpty()) { toast("Папки мессенджеров не найдены"); return; }
        String[] labels = new String[dirs.size()];
        for (int i = 0; i < dirs.size(); i++) {
            labels[i] = dirs.get(i).getName() + " · " + FileUtils.human(FileUtils.size(dirs.get(i)));
        }
        new AlertDialog.Builder(this).setTitle("Медиа мессенджеров").setItems(labels, (dialog, which) -> {
            File dir = dirs.get(which);
            confirm("Удалить " + dir.getName() + " в корзину?", () -> {
                trash.put(dir);
                toast("Перемещено в корзину");
                recreate();
            });
        }).show();
    }

    private void confirm(String message, Runnable action) {
        new AlertDialog.Builder(this).setTitle("Очистка").setMessage(message)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("OK", (dialog, which) -> action.run()).show();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics()));
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attr, value, true);
        return value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT ? value.data : fallback;
    }

    private int textColor() {
        return resolveColor(android.R.attr.textColorPrimary, Color.WHITE);
    }

    private int subColor() {
        return resolveColor(android.R.attr.textColorSecondary, Color.LTGRAY);
    }

    private int accentColor() {
        return resolveColor(androidx.appcompat.R.attr.colorAccent, Color.parseColor("#B39DDB"));
    }

    private android.graphics.drawable.GradientDrawable cardBackground() {
        int theme = 0;
        try {
            theme = Integer.parseInt(prefs.getString("theme", "0"));
        } catch (Exception ignored) {
        }
        int color = theme == 1 ? Color.parseColor("#FFFFFFFF")
                : theme == 2 ? Color.parseColor("#1E1E24")
                : Color.parseColor("#33FFFFFF");
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(16));
        return drawable;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
