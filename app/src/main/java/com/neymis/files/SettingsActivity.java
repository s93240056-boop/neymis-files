package com.neymis.files;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS = "neymis_files";
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        applyTheme();
        super.onCreate(savedInstanceState);
        setTitle("Настройки");

        ScrollView scroll = new ScrollView(this);
        setContentView(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(root);

        TextView header = new TextView(this);
        header.setText("Настройки");
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        header.setTextColor(textColor());
        header.setPadding(0, dp(8), 0, dp(16));
        root.addView(header);

        category(root, "ВНЕШНИЙ ВИД");
        row(root, "Тема", themeName(), v -> chooseTheme());
        row(root, "Сортировка", sortName(), v -> chooseSort());
        toggle(root, "Показывать размер в списке", "show_size", true, null);
        toggle(root, "Скрытые файлы", "hidden", false, (button, checked) -> Toast.makeText(
                this, checked ? "Скрытые файлы видны" : "Скрытые файлы скрыты", Toast.LENGTH_SHORT).show());

        category(root, "ПРОСМОТР");
        toggle(root, "Встроенный просмотр файлов", "internal_viewer", true,
                (button, checked) -> Toast.makeText(this,
                        checked ? "Файлы открываются внутри приложения" : "Файлы открываются другими приложениями",
                        Toast.LENGTH_SHORT).show());

        category(root, "ХРАНИЛИЩЕ");
        row(root, "Память", storageText(), v -> storageInfo());
        row(root, "Папка приложения", getFilesDir().getAbsolutePath(), v -> toast(getFilesDir().getAbsolutePath()));

        category(root, "О ПРИЛОЖЕНИИ");
        row(root, "Версия", "1.9", null);
        row(root, "Лицензия", "MIT", v -> toast("Лицензия MIT — исходный код открыт"));
        row(root, "Исходный код", "GitHub", v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/s93240056-boop/neymis-files"))));
    }

    private void applyTheme() {
        int theme = 0;
        try {
            theme = Integer.parseInt(prefs.getString("theme", "0"));
        } catch (Exception ignored) {
        }
        if (theme == 1) setTheme(R.style.AppTheme_Light);
        else if (theme == 2) setTheme(R.style.AppTheme_Dark);
        else if (theme == 3) setTheme(R.style.AppTheme_System);
        else setTheme(R.style.AppTheme_Gradient);
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

    private GradientDrawable cardBackground() {
        int theme = 0;
        try {
            theme = Integer.parseInt(prefs.getString("theme", "0"));
        } catch (Exception ignored) {
        }
        int color = theme == 1 ? Color.parseColor("#FFFFFFFF")
                : theme == 2 ? Color.parseColor("#1E1E24")
                : Color.parseColor("#33FFFFFF");
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(16));
        return drawable;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        return view;
    }

    private void category(LinearLayout root, String name) {
        TextView view = text(name, 14, accentColor());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);
        params.bottomMargin = dp(8);
        view.setLayoutParams(params);
        root.addView(view);
    }

    private void row(LinearLayout root, String title, String value, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cardBackground());
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);

        TextView name = text(title, 16, textColor());
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(name);
        TextView detail = text(value, 13, subColor());
        card.addView(detail);
        if (listener != null) card.setOnClickListener(listener);
        root.addView(card);
    }

    private void toggle(LinearLayout root, String title, String key, boolean defaultValue,
                        CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cardBackground());
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);

        TextView name = text(title, 16, textColor());
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(name);
        CheckBox check = new CheckBox(this);
        check.setChecked(prefs.getBoolean(key, defaultValue));
        check.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            if (listener != null) listener.onCheckedChanged(button, checked);
        });
        card.addView(check);
        card.setOnClickListener(v -> check.setChecked(!check.isChecked()));
        root.addView(card);
    }

    private String themeName() {
        try {
            int theme = Integer.parseInt(prefs.getString("theme", "0"));
            if (theme == 1) return "Белая";
            if (theme == 2) return "Тёмная";
            if (theme == 3) return "Системная (Material You)";
        } catch (Exception ignored) {
        }
        return "Фиолетово-синий градиент";
    }

    private String sortName() {
        String[] by = {"имени", "размеру", "дате"};
        int sort = prefs.getInt("sort", 2);
        boolean asc = prefs.getBoolean("asc", false);
        return "По " + by[Math.max(0, Math.min(2, sort))] + (asc ? " ↑" : " ↓");
    }

    private void chooseTheme() {
        String[] variants = {"Фиолетово-синий градиент", "Белая", "Тёмная", "Системная (Material You)"};
        new AlertDialog.Builder(this).setTitle("Тема").setItems(variants, (dialog, which) -> {
            prefs.edit().putString("theme", String.valueOf(which)).apply();
            recreate();
        }).show();
    }

    private void chooseSort() {
        String[] variants = {"Имя ↑", "Имя ↓", "Размер ↑", "Размер ↓", "Дата ↑", "Дата ↓"};
        new AlertDialog.Builder(this).setTitle("Сортировка").setItems(variants, (dialog, which) -> {
            prefs.edit().putInt("sort", which / 2).putBoolean("asc", which % 2 == 0).apply();
            recreate();
        }).show();
    }

    private String storageText() {
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        return FileUtils.human(stat.getAvailableBytes()) + " свободно";
    }

    private void storageInfo() {
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        long total = stat.getTotalBytes();
        long available = stat.getAvailableBytes();
        String message = "Всего: " + FileUtils.human(total)
                + "\nСвободно: " + FileUtils.human(available)
                + "\nЗанято: " + FileUtils.human(total - available);
        new AlertDialog.Builder(this).setTitle("Память").setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
