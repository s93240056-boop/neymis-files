package com.neymis.files;

import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

final class Crypto {
    private static final SecureRandom RANDOM = new SecureRandom();

    private Crypto() {
    }

    static boolean hasPin(SharedPreferences prefs) {
        return prefs.getString("safe_pin_hash", null) != null;
    }

    static boolean checkPin(SharedPreferences prefs, String pin) {
        String stored = prefs.getString("safe_pin_hash", null);
        return stored != null && stored.equals(hash(pin, prefs.getString("safe_salt", "")));
    }

    static void setPin(SharedPreferences prefs, String pin) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String encodedSalt = Base64.encodeToString(salt, Base64.NO_WRAP);
        prefs.edit()
                .putString("safe_salt", encodedSalt)
                .putString("safe_pin_hash", hash(pin, encodedSalt))
                .apply();
    }

    private static String hash(String value, String encodedSalt) {
        try {
            byte[] salt = Base64.decode(encodedSalt, Base64.NO_WRAP);
            PBEKeySpec spec = new PBEKeySpec(value.toCharArray(), salt, 10000, 256);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                    .generateSecret(spec).getEncoded();
            byte[] sha = MessageDigest.getInstance("SHA-256").digest(key);
            return Base64.encodeToString(sha, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    private static SecretKeySpec key(String pin, SharedPreferences prefs) throws Exception {
        byte[] salt = Base64.decode(prefs.getString("safe_salt", ""), Base64.NO_WRAP);
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 10000, 256);
        byte[] raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        return new SecretKeySpec(raw, "AES");
    }

    static byte[] encrypt(String pin, SharedPreferences prefs, byte[] data) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(pin, prefs), new GCMParameterSpec(128, iv));
        byte[] body = cipher.doFinal(data);
        byte[] result = new byte[iv.length + body.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(body, 0, result, iv.length, body.length);
        return result;
    }

    static byte[] decrypt(String pin, SharedPreferences prefs, byte[] data) throws Exception {
        if (data.length <= 12) throw new IllegalArgumentException("Некорректные данные");
        byte[] iv = Arrays.copyOfRange(data, 0, 12);
        byte[] body = Arrays.copyOfRange(data, 12, data.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(pin, prefs), new GCMParameterSpec(128, iv));
        return cipher.doFinal(body);
    }
}
