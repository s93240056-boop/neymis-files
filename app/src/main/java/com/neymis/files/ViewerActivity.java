package com.neymis.files;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;

public class ViewerActivity extends AppCompatActivity {

    private File file;
    private String type;
    private FrameLayout content;

    private EditText editor;
    private ImageView image;
    private float scale = 1f;
    private float lastX;
    private float lastY;
    private ScaleGestureDetector detector;

    private MediaPlayer player;
    private ImageButton playButton;
    private SeekBar seekBar;
    private final Handler handler = new Handler();
    private Runnable progressTask;

    private VideoView video;

    private PdfRenderer pdf;
    private ImageView pdfImage;
    private TextView pdfPage;
    private int pageIndex;

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

        String path = getIntent().getStringExtra("path");
        file = new File(path == null ? "" : path);
        type = getIntent().getStringExtra("type");
        setTitle(file.getName());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        setContentView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageButton back = button(R.drawable.ic_back, v -> finish());
        toolbar.addView(back);

        TextView title = new TextView(this);
        title.setText(file.getName());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(textColor());
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        toolbar.addView(title);

        if ("text".equals(type)) {
            toolbar.addView(button(R.drawable.ic_edit, v -> toggleEditing()));
        }

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        switch (type == null ? "" : type) {
            case "text": showText(); break;
            case "image": showImage(); break;
            case "audio": showAudio(); break;
            case "video": showVideo(); break;
            case "pdf": showPdf(); break;
            default: showText(); break;
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics()));
    }

    private int textColor() {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, value, true);
        return value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT ? value.data : Color.WHITE;
    }

    private int accentColor() {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(androidx.appcompat.R.attr.colorAccent, value, true);
        return value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT
                ? value.data : Color.parseColor("#B39DDB");
    }

    private ImageButton button(int icon, View.OnClickListener listener) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setColorFilter(accentColor());
        button.setOnClickListener(listener);
        return button;
    }

    private void showText() {
        editor = new EditText(this);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setTextColor(textColor());
        editor.setBackgroundColor(Color.TRANSPARENT);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        editor.setFocusable(false);
        try {
            editor.setText(FileUtils.readText(file));
        } catch (IOException e) {
            editor.setText("Не удалось прочитать файл");
        }
        content.addView(editor, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void toggleEditing() {
        if (editor == null) return;
        if (editor.isFocusable()) {
            try {
                FileUtils.writeText(file, editor.getText().toString());
                editor.setFocusable(false);
                Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(this, "Не удалось сохранить", Toast.LENGTH_SHORT).show();
            }
        } else {
            editor.setFocusable(true);
            editor.setFocusableInTouchMode(true);
            editor.requestFocus();
            Toast.makeText(this, "Режим редактирования", Toast.LENGTH_SHORT).show();
        }
    }

    private void showImage() {
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        Bitmap bitmap = loadBitmap(file);
        if (bitmap == null) {
            Toast.makeText(this, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        image.setImageBitmap(bitmap);

        detector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        scale = Math.max(0.5f, Math.min(8f, scale * d.getScaleFactor()));
                        image.setScaleX(scale);
                        image.setScaleY(scale);
                        return true;
                    }
                });
        image.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            if (event.getPointerCount() == 1) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    lastX = event.getX();
                    lastY = event.getY();
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && scale > 1f) {
                    image.setTranslationX(image.getTranslationX() + event.getX() - lastX);
                    image.setTranslationY(image.getTranslationY() + event.getY() - lastY);
                    lastX = event.getX();
                    lastY = event.getY();
                }
            }
            return true;
        });

        content.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private Bitmap loadBitmap(File source) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth == 0 || bounds.outHeight == 0) return null;
        int sample = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / sample > 2400) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(source.getAbsolutePath(), options);
    }

    private void showAudio() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));

        playButton = button(R.drawable.ic_play, v -> toggleAudio());
        box.addView(playButton);

        seekBar = new SeekBar(this);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(seekBar);

        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            seekBar.setMax(player.getDuration());
            player.setOnCompletionListener(mp -> {
                playButton.setImageResource(R.drawable.ic_play);
                seekBar.setProgress(0);
            });
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                    if (fromUser && player != null) player.seekTo(value);
                }
                @Override public void onStartTrackingTouch(SeekBar seek) { }
                @Override public void onStopTrackingTouch(SeekBar seek) { }
            });
            progressTask = new Runnable() {
                @Override public void run() {
                    if (player != null && player.isPlaying()) seekBar.setProgress(player.getCurrentPosition());
                    handler.postDelayed(this, 400);
                }
            };
            handler.post(progressTask);
        } catch (IOException e) {
            Toast.makeText(this, "Не удалось открыть аудио", Toast.LENGTH_SHORT).show();
        }

        content.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void toggleAudio() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            playButton.setImageResource(R.drawable.ic_play);
        } else {
            player.start();
            playButton.setImageResource(R.drawable.ic_pause);
        }
    }

    private void showVideo() {
        video = new VideoView(this);
        video.setVideoURI(Uri.fromFile(file));
        MediaController controls = new MediaController(this);
        controls.setAnchorView(video);
        video.setMediaController(controls);
        video.start();
        content.addView(video, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showPdf() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        pdfImage = new ImageView(this);
        pdfImage.setAdjustViewBounds(true);
        pdfImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(pdfImage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.addView(button(R.drawable.ic_prev, v -> changePage(-1)));
        pdfPage = new TextView(this);
        pdfPage.setTextColor(textColor());
        pdfPage.setLayoutParams(new LinearLayout.LayoutParams(dp(90),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        pdfPage.setGravity(Gravity.CENTER);
        controls.addView(pdfPage);
        controls.addView(button(R.drawable.ic_next, v -> changePage(1)));
        box.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        try {
            pdf = new PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY));
            renderPage();
        } catch (IOException e) {
            Toast.makeText(this, "Не удалось открыть PDF", Toast.LENGTH_SHORT).show();
        }

        content.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void changePage(int delta) {
        if (pdf == null) return;
        int next = pageIndex + delta;
        if (next < 0 || next >= pdf.getPageCount()) return;
        pageIndex = next;
        renderPage();
    }

    private void renderPage() {
        if (pdf == null) return;
        PdfRenderer.Page page = pdf.openPage(pageIndex);
        Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        pdfImage.setImageBitmap(bitmap);
        pdfPage.setText((pageIndex + 1) + " / " + pdf.getPageCount());
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            if (progressTask != null) handler.removeCallbacks(progressTask);
            player.release();
            player = null;
        }
        if (video != null) video.stopPlayback();
        if (pdf != null) {
            try {
                pdf.close();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }
}
