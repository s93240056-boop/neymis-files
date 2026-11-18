package com.neymis.files;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrashActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private Trash trash;
    private LinearLayout rootView;

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
        setTitle("Корзина");

        trash = new Trash(prefs);
        trash.purgeOld();

        ScrollView scroll = new ScrollView(this);
        setContentView(scroll);
        rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(rootView);
        render();
    }

    private void render() {
        rootView.removeAllViews();
        TextView title = new TextView(this);
        title.setText("Корзина");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTextColor(textColor());
        rootView.addView(title);

        TextView note = new TextView(this);
        note.setText("Файлы хранятся 30 дней, затем удаляются автоматически");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setTextColor(subColor());
        note.setPadding(0, dp(4), 0, dp(12));
        rootView.addView(note);

        List<Trash.Entry> entries = trash.entries();
        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Корзина пуста");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            empty.setTextColor(subColor());
            empty.setPadding(0, dp(24), 0, dp(24));
            rootView.addView(empty);
            return;
        }

        for (Trash.Entry entry : entries) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(cardBackground());
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(10);
            card.setLayoutParams(params);

            TextView name = new TextView(this);
            name.setText(entry.name);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            name.setTextColor(textColor());
            card.addView(name);

            String date = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(new Date(entry.time));
            TextView path = new TextView(this);
            path.setText(entry.originalPath + "\n" + date);
            path.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            path.setTextColor(subColor());
            card.addView(path);

            LinearLayout buttons = new LinearLayout(this);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setPadding(0, dp(8), 0, 0);
            buttons.addView(action("Восстановить", v -> {
                trash.restore(entry);
                toast("Восстановлено");
                render();
            }));
            buttons.addView(action("Удалить навсегда", v -> {
                trash.remove(entry);
                toast("Удалено");
                render();
            }));
            card.addView(buttons);
            rootView.addView(card);
        }

        TextView empty = new TextView(this);
        empty.setText("Очистить корзину");
        empty.setTextColor(accentColor());
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        empty.setPadding(dp(8), dp(18), dp(8), dp(18));
        empty.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Очистить корзину?")
                .setMessage("Все файлы будут удалены без возможности восстановления")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Очистить", (dialog, which) -> {
                    trash.empty();
                    toast("Корзина очищена");
                    render();
                }).show());
        rootView.addView(empty);
    }

    private TextView action(String text, View.OnClickListener listener) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(accentColor());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setPadding(0, dp(6), dp(24), dp(6));
        view.setOnClickListener(listener);
        return view;
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
