package com.neymis.files;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.Set;

final class FileListAdapter extends BaseAdapter {

    interface MenuListener {
        void onMenu(File file);
    }

    interface CheckListener {
        void onCheck();
    }

    static final class Row {
        final String header;
        final File file;
        final boolean isHeader;

        private Row(String header, File file, boolean isHeader) {
            this.header = header;
            this.file = file;
            this.isHeader = isHeader;
        }

        static Row section(String header) {
            return new Row(header, null, true);
        }

        static Row item(File file) {
            return new Row(null, file, false);
        }
    }

    private final Context context;
    private final List<Row> rows;
    private final Set<File> selected;
    private final MenuListener menuListener;
    private final CheckListener checkListener;
    private final boolean selectionMode;
    private final int textColor;
    private final int subColor;
    private final int accentColor;

    FileListAdapter(Context context, List<Row> rows, Set<File> selected,
                    MenuListener menuListener, CheckListener checkListener,
                    boolean selectionMode) {
        this.context = context;
        this.rows = rows;
        this.selected = selected;
        this.menuListener = menuListener;
        this.checkListener = checkListener;
        this.selectionMode = selectionMode;
        int[] colors = resolveColors(context);
        this.textColor = colors[0];
        this.subColor = colors[1];
        this.accentColor = colors[2];
    }

    private static int[] resolveColors(Context context) {
        int text = Color.WHITE, sub = Color.LTGRAY, accent = Color.parseColor("#B39DDB");
        text = resolve(context, android.R.attr.textColorPrimary, text);
        sub = resolve(context, android.R.attr.textColorSecondary, sub);
        accent = resolve(context, androidx.appcompat.R.attr.colorAccent, accent);
        return new int[]{text, sub, accent};
    }

    private static int resolve(Context context, int attr, int fallback) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) return fallback;
        if (value.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) return value.data;
        if (value.type == android.util.TypedValue.TYPE_REFERENCE) {
            try {
                return androidx.core.content.ContextCompat.getColor(context, value.resourceId);
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Row getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row = rows.get(position);
        return row.isHeader ? headerView(row.header) : fileView(row.file);
    }

    private View headerView(String title) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(8));

        TextView text = new TextView(context);
        text.setText(title);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(accentColor);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(text);

        TextView check = new TextView(context);
        check.setText(selectionMode ? "Отменить" : "☑");
        check.setTextSize(selectionMode ? TypedValue.COMPLEX_UNIT_SP
                : TypedValue.COMPLEX_UNIT_SP, selectionMode ? 13 : 20);
        check.setTextColor(accentColor);
        check.setOnClickListener(v -> checkListener.onCheck());
        box.addView(check);
        return box;
    }

    private View fileView(File file) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(16), dp(8), dp(8), dp(8));
        if (selected.contains(file)) {
            box.setBackgroundColor(Color.parseColor("#22B39DDB"));
        } else {
            box.setBackgroundColor(Color.TRANSPARENT);
        }

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int size = dp(52);
        icon.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        Bitmap preview = thumbnail(file);
        if (preview != null) {
            icon.setImageBitmap(preview);
        } else {
            icon.setImageResource(file.getName().toLowerCase().endsWith(".apk")
                    ? android.R.drawable.sym_def_app_icon : R.drawable.ic_doc);
            icon.setColorFilter(subColor);
            icon.setBackgroundColor(Color.parseColor("#22000000"));
            icon.setPadding(dp(10), dp(10), dp(10), dp(10));
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        }
        box.addView(icon);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        texts.setPadding(dp(14), 0, dp(8), 0);

        TextView name = new TextView(context);
        name.setText(file.getName());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTextColor(textColor);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name);

        TextView detail = new TextView(context);
        detail.setText(FileUtils.human(file.isDirectory() ? FileUtils.size(file) : file.length())
                + "  ·  " + timeAgo(file.lastModified()));
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        detail.setTextColor(subColor);
        texts.addView(detail);
        box.addView(texts);

        ImageButton menu = new ImageButton(context);
        menu.setImageResource(R.drawable.ic_menu_dots);
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setColorFilter(subColor);
        menu.setOnClickListener(v -> menuListener.onMenu(file));
        box.addView(menu);
        return box;
    }

    static String timeAgo(long time) {
        long minutes = (System.currentTimeMillis() - time) / 60000;
        if (minutes < 1) return "только что";
        if (minutes < 60) return minutes + " мин назад";
        long hours = minutes / 60;
        if (hours < 24) return hours + " ч назад";
        long days = hours / 24;
        if (days == 1) return "вчера";
        if (days < 7) return days + " дн назад";
        return new java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
                .format(new java.util.Date(time));
    }

    static String dayHeader(long time) {
        long days = (System.currentTimeMillis() - time) / 86400000;
        if (days <= 0) return "Сегодня";
        if (days == 1) return "Вчера";
        return new java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
                .format(new java.util.Date(time));
    }

    private static Bitmap thumbnail(File file) {
        String name = file.getName().toLowerCase();
        if (!(name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".webp"))) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth == 0) return null;
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 200) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, context.getResources().getDisplayMetrics()));
    }
}
