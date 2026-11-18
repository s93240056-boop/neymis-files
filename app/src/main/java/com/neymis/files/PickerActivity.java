package com.neymis.files;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.EditText;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PickerActivity extends AppCompatActivity {

    private File current;
    private final List<File> files = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView pathView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences("neymis_files", MODE_PRIVATE);
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
        setTitle("Выбрать файл");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        setContentView(root);

        EditText search = new EditText(this);
        search.setHint("Поиск файла");
        search.setTextColor(textColor());
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pathView = new TextView(this);
        pathView.setTextColor(subColor());
        pathView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        root.addView(pathView);

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        current = Environment.getExternalStorageDirectory();
        show(current);

        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0 && current.getParentFile() != null) {
                show(current.getParentFile());
                return;
            }
            if (position == 0) return;
            File file = files.get(position - 1);
            if (file.isDirectory()) {
                show(file);
                return;
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent result = new Intent();
            result.setData(uri);
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            result.setType(getContentResolver().getType(uri));
            setResult(RESULT_OK, result);
            finish();
        });

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                String query = s.toString().trim().toLowerCase();
                files.clear();
                File[] children = current.listFiles();
                if (children != null) {
                    for (File file : children) {
                        if (query.isEmpty() || file.getName().toLowerCase().contains(query)) files.add(file);
                    }
                }
                Collections.sort(files, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                names.clear();
                names.add("..");
                for (File file : files) names.add((file.isDirectory() ? "[DIR] " : "") + file.getName());
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void show(File dir) {
        current = dir;
        File[] children = dir.listFiles();
        files.clear();
        if (children != null) {
            for (File file : children) if (!file.getName().startsWith(".")) files.add(file);
        }
        Collections.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        names.clear();
        names.add("..");
        for (File file : files) names.add((file.isDirectory() ? "[DIR] " : "") + file.getName());
        adapter.notifyDataSetChanged();
        pathView.setText(dir.getAbsolutePath());
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
}
