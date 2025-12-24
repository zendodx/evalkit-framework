package com.evalkit.framework.common.utils.text;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 文本文件读写工具类（Java 8 兼容，UTF-8）
 */
public final class TextFileUtils {

    private TextFileUtils() {
    }

    /* -------------------- 读 -------------------- */

    /**
     * 按行读取
     */
    public static List<String> readLines(String path) {
        return readLines(new File(path));
    }

    public static List<String> readLines(File file) {
        List<String> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                list.add(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return list;
    }

    /**
     * 一次性读取整个文本
     */
    public static String readString(String path) {
        return readString(new File(path));
    }

    public static String readString(File file) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            char[] buf = new char[1024];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }

    /* -------------------- 写 -------------------- */

    /**
     * 按行写入（覆盖）
     */
    public static void writeLines(String path, Collection<?> lines) {
        writeLines(new File(path), lines);
    }

    public static void writeLines(File file, Collection<?> lines) {
        mkParentDirs(file);
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))) {
            for (Object o : lines) {
                pw.println(o == null ? "" : o.toString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 一次性写入整个文本（覆盖）
     */
    public static void writeString(String path, String content) {
        if (StringUtils.isEmpty(path)) return;
        writeString(new File(path), content);
    }

    public static void writeString(File file, String content) {
        if (file == null) return;
        mkParentDirs(file);
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            w.write(content == null ? "" : content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 追加一行
     */
    public static void appendLine(String path, String line) {
        appendLines(new File(path), Collections.singletonList(line));
    }

    /**
     * 追加多行
     */
    public static void appendLines(String path, Collection<?> lines) {
        appendLines(new File(path), lines);
    }

    public static void appendLines(File file, Collection<?> lines) {
        mkParentDirs(file);
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            for (Object o : lines) {
                pw.println(o == null ? "" : o.toString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 追加整个字符串
     */
    public static void appendString(String path, String content) {
        appendString(new File(path), content);
    }

    public static void appendString(File file, String content) {
        mkParentDirs(file);
        try (Writer w = new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            w.write(content == null ? "" : content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /* -------------------- 私有 -------------------- */

    private static void mkParentDirs(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new UncheckedIOException(
                        new IOException("mkdirs failed: " + parent.getAbsolutePath()));
            }
        }
    }
}