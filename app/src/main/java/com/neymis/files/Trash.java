package com.neymis.files;

import android.content.SharedPreferences;
import android.util.Base64;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

final class Trash {
    static final long KEEP_DAYS = 30;
    private final SharedPreferences prefs;
    private final File folder;

    Trash(SharedPreferences prefs) {
        this.prefs = prefs;
        this.folder = new File(android.os.Environment.getExternalStorageDirectory(), ".NeymisTrash");
    }

    static final class Entry {
        final String name;
        final String originalPath;
        final long time;

        Entry(String name, String originalPath, long time) {
            this.name = name;
            this.originalPath = originalPath;
            this.time = time;
        }

        File file(File trashFolder) {
            try {
                String encoded = Base64.encodeToString(name.getBytes("UTF-8"), Base64.NO_WRAP);
                return new File(trashFolder, encoded);
            } catch (UnsupportedEncodingException e) {
                return new File(trashFolder, "item");
            }
        }
    }

    private String encode(String name) {
        try {
            return Base64.encodeToString(name.getBytes("UTF-8"), Base64.NO_WRAP);
        } catch (UnsupportedEncodingException e) {
            return "item";
        }
    }

    private String decode(String value) {
        try {
            return new String(Base64.decode(value, Base64.NO_WRAP), "UTF-8");
        } catch (Exception e) {
            return "файл";
        }
    }

    private List<Entry> read() {
        List<Entry> result = new ArrayList<>();
        String raw = prefs.getString("trash_entries", "");
        for (String line : raw.split("\n")) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\t", -1);
            if (parts.length < 3) continue;
            try {
                result.add(new Entry(decode(parts[0]), parts[1], Long.parseLong(parts[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private void write(List<Entry> entries) {
        StringBuilder builder = new StringBuilder();
        for (Entry entry : entries) {
            builder.append(encode(entry.name)).append('\t')
                    .append(entry.originalPath).append('\t')
                    .append(entry.time).append('\n');
        }
        prefs.edit().putString("trash_entries", builder.toString()).apply();
    }

    List<Entry> entries() {
        return read();
    }

    File folder() {
        return folder;
    }

    void put(File file) {
        if (!folder.isDirectory() && !folder.mkdirs()) return;
        File target = new File(folder, encode(file.getName()));
        if (target.exists()) target = FileUtils.uniqueDestination(folder, encode(file.getName()));
        if (!FileUtils.move(file, target)) return;
        List<Entry> entries = read();
        entries.add(new Entry(file.getName(), file.getAbsolutePath(), System.currentTimeMillis()));
        write(entries);
    }

    void restore(Entry entry) {
        File source = entry.file(folder);
        File target = new File(entry.originalPath);
        if (!source.exists()) {
            remove(entry);
            return;
        }
        if (target.exists()) target = FileUtils.uniqueDestination(target.getParentFile(), target.getName());
        FileUtils.move(source, target);
        remove(entry);
    }

    void remove(Entry entry) {
        List<Entry> entries = read();
        entries.removeIf(item -> item.name.equals(entry.name)
                && item.originalPath.equals(entry.originalPath) && item.time == entry.time);
        write(entries);
        FileUtils.delete(entry.file(folder));
    }

    void empty() {
        List<Entry> entries = read();
        for (Entry entry : entries) FileUtils.delete(entry.file(folder));
        write(new ArrayList<>());
    }

    void purgeOld() {
        long limit = System.currentTimeMillis() - KEEP_DAYS * 24L * 60 * 60 * 1000;
        List<Entry> keep = new ArrayList<>();
        for (Entry entry : read()) {
            if (entry.time < limit) FileUtils.delete(entry.file(folder));
            else keep.add(entry);
        }
        write(keep);
    }
}
