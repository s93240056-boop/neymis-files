package com.neymis.files;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SafeActivity extends AppCompatActivity {

    private static final String KEYSTORE_ALIAS = "neymis_safe_biometric";
    private static final String NAMES_KEY = "safe_names";

    private SharedPreferences prefs;
    private File folder;
    private String pin;
    private LinearLayout listBox;
    private TextView status;

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
        setTitle("Безопасная папка");

        folder = new File(getFilesDir(), "safe");
        folder.mkdirs();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        setContentView(root);

        status = new TextView(this);
        status.setTextColor(textColor());
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setText("Безопасная папка заблокирована");
        root.addView(status);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (!Crypto.hasPin(prefs)) setupPin();
        else unlock();
    }

    private void unlock() {
        if (canUseBiometric() && prefs.getBoolean("safe_biometric", false)) {
            unlockWithBiometric();
            return;
        }
        askPin("Введите PIN", false);
    }

    private boolean canUseBiometric() {
        return BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void unlockWithBiometric() {
        Cipher cipher;
        try {
            cipher = biometricCipher(Cipher.DECRYPT_MODE);
        } catch (Exception e) {
            askPin("Введите PIN", false);
            return;
        }
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        try {
                            BiometricPrompt.CryptoObject crypto = result.getCryptoObject();
                            byte[] raw = crypto.getCipher().doFinal(
                                    Base64.decode(prefs.getString("safe_pin_enc", ""), Base64.NO_WRAP));
                            onUnlocked(new String(raw, "UTF-8"));
                        } catch (Exception e) {
                            askPin("Введите PIN", false);
                        }
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence message) {
                        askPin("Введите PIN", false);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        toast("Биометрия не распознана");
                    }
                });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Безопасная папка")
                .setSubtitle("Подтвердите вход")
                .setNegativeButtonText("По PIN")
                .build();
        prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
    }

    private Cipher biometricCipher(int mode) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(KEYSTORE_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build());
            generator.generateKey();
        }
        SecretKey key = (SecretKey) store.getKey(KEYSTORE_ALIAS, null);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        if (mode == Cipher.ENCRYPT_MODE) {
            cipher.init(mode, key);
        } else {
            byte[] iv = Base64.decode(prefs.getString("safe_pin_iv", ""), Base64.NO_WRAP);
            cipher.init(mode, key, new GCMParameterSpec(128, iv));
        }
        return cipher;
    }

    private void setupPin() {
        EditText first = new EditText(this);
        first.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        first.setHint("Новый PIN (от 4 цифр)");
        EditText second = new EditText(this);
        second.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        second.setHint("Повторите PIN");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(first);
        box.addView(second);
        new AlertDialog.Builder(this).setTitle("Создание PIN").setView(box)
                .setCancelable(false)
                .setPositiveButton("Готово", (dialog, which) -> {
                    String value = first.getText().toString().trim();
                    if (value.length() < 4 || !value.equals(second.getText().toString().trim())) {
                        toast("PIN не совпадает или слишком короткий");
                        setupPin();
                        return;
                    }
                    Crypto.setPin(prefs, value);
                    offerBiometric(value);
                }).show();
    }

    private void offerBiometric(String value) {
        if (!canUseBiometric()) {
            onUnlocked(value);
            return;
        }
        new AlertDialog.Builder(this).setTitle("Биометрия")
                .setMessage("Использовать отпечаток или лицо для входа?")
                .setNegativeButton("Нет", (dialog, which) -> onUnlocked(value))
                .setPositiveButton("Да", (dialog, which) -> {
                    try {
                        Cipher cipher = biometricCipher(Cipher.ENCRYPT_MODE);
                        byte[] encrypted = cipher.doFinal(value.getBytes("UTF-8"));
                        byte[] iv = cipher.getIV();
                        prefs.edit()
                                .putString("safe_pin_enc", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                                .putString("safe_pin_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                                .putBoolean("safe_biometric", true)
                                .apply();
                    } catch (Exception e) {
                        toast("Не удалось включить биометрию");
                    }
                    onUnlocked(value);
                }).show();
    }

    private void askPin(String title, boolean verifyOld) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this).setTitle(title).setView(input).setCancelable(false)
                .setPositiveButton("Войти", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (Crypto.checkPin(prefs, value)) onUnlocked(value);
                    else {
                        toast("Неверный PIN");
                        askPin(title, verifyOld);
                    }
                }).show();
    }

    private void onUnlocked(String value) {
        pin = value;
        status.setText("Разблокировано · файлов: " + readNames().size());
        render();
    }

    private List<String[]> readNames() {
        List<String[]> result = new ArrayList<>();
        String raw = prefs.getString(NAMES_KEY, "");
        for (String line : raw.split("\n")) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|", 2);
            if (parts.length == 2) result.add(parts);
        }
        return result;
    }

    private void writeNames(List<String[]> names) {
        StringBuilder builder = new StringBuilder();
        for (String[] pair : names) builder.append(pair[0]).append('|').append(pair[1]).append('\n');
        prefs.edit().putString(NAMES_KEY, builder.toString()).apply();
    }

    private void render() {
        listBox.removeAllViews();
        List<String[]> names = readNames();
        for (String[] pair : names) {
            TextView row = new TextView(this);
            row.setText("🔒 " + pair[1]);
            row.setTextColor(textColor());
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            row.setPadding(dp(8), dp(14), dp(8), dp(14));
            row.setOnClickListener(v -> fileActions(pair));
            listBox.addView(row);
        }
        TextView add = new TextView(this);
        add.setText("＋  Добавить файл");
        add.setTextColor(accentColor());
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        add.setPadding(dp(8), dp(18), dp(8), dp(18));
        add.setOnClickListener(v -> pickFile());
        listBox.addView(add);
    }

    private File encryptedFile(String encoded) {
        return new File(folder, encoded + ".enc");
    }

    private void pickFile() {
        File dir = new File(android.os.Environment.getExternalStorageDirectory(), "Download");
        File[] children = dir.listFiles();
        List<File> candidates = new ArrayList<>();
        if (children != null) {
            for (File file : children) if (file.isFile()) candidates.add(file);
        }
        final List<File> items = candidates;
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) labels[i] = items.get(i).getName() + " · "
                + FileUtils.human(items.get(i).length());
        new AlertDialog.Builder(this).setTitle("Выберите файл").setItems(labels, (dialog, which) -> {
            File source = items.get(which);
            try {
                byte[] data = readAll(source);
                byte[] encrypted = Crypto.encrypt(pin, prefs, data);
                String encoded = Base64.encodeToString(source.getName().getBytes("UTF-8"), Base64.NO_WRAP);
                try (FileOutputStream out = new FileOutputStream(encryptedFile(encoded))) {
                    out.write(encrypted);
                }
                List<String[]> names = readNames();
                names.add(new String[]{encoded, source.getName()});
                writeNames(names);
                toast("Файл перемещён в сейф");
                render();
            } catch (Exception e) {
                toast("Не удалось зашифровать файл");
            }
        }).show();
    }

    private static byte[] readAll(File file) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private void fileActions(String[] pair) {
        String[] actions = {"Открыть", "Извлечь обратно в Загрузки", "Удалить из сейфа"};
        new AlertDialog.Builder(this).setTitle(pair[1]).setItems(actions, (dialog, which) -> {
            if (which == 0) openEncrypted(pair);
            else if (which == 1) export(pair);
            else removeFromSafe(pair);
        }).show();
    }

    private void openEncrypted(String[] pair) {
        try {
            byte[] encrypted = readAll(encryptedFile(pair[0]));
            byte[] plain = Crypto.decrypt(pin, prefs, encrypted);
            File temp = new File(getCacheDir(), pair[1]);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(plain);
            }
            String type = viewerType(pair[1]);
            if (type == null) {
                toast("Этот тип файла нельзя открыть внутри");
                return;
            }
            android.content.Intent intent = new android.content.Intent(this, ViewerActivity.class);
            intent.putExtra("path", temp.getAbsolutePath());
            intent.putExtra("type", type);
            startActivity(intent);
        } catch (Exception e) {
            toast("Не удалось расшифровать файл");
        }
    }

    private String viewerType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json")
                || lower.endsWith(".xml") || lower.endsWith(".csv") || lower.endsWith(".log")) return "text";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image";
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg")) return "audio";
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv")) return "video";
        if (lower.endsWith(".pdf")) return "pdf";
        return null;
    }

    private void export(String pair[]) {
        try {
            byte[] plain = Crypto.decrypt(pin, prefs, readAll(encryptedFile(pair[0])));
            File target = new File(android.os.Environment.getExternalStorageDirectory(),
                    "Download/" + pair[1]);
            target = FileUtils.uniqueDestination(target.getParentFile(), target.getName());
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(plain);
            }
            toast("Извлечено: " + target.getName());
        } catch (Exception e) {
            toast("Не удалось извлечь файл");
        }
    }

    private void removeFromSafe(String[] pair) {
        FileUtils.delete(encryptedFile(pair[0]));
        List<String[]> names = readNames();
        names.removeIf(item -> item[0].equals(pair[0]));
        writeNames(names);
        render();
    }

    @Override
    protected void onPause() {
        pin = null;
        finish();
        super.onPause();
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

    private int accentColor() {
        return resolveColor(androidx.appcompat.R.attr.colorAccent, Color.parseColor("#B39DDB"));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
