package com.evalkit.framework.common.utils.random;

import java.security.SecureRandom;

/**
 * NanoId工具类
 */
public class NanoIdUtils {
    private static final char[] DEFAULT_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz-".toCharArray();
    private static final int DEFAULT_SIZE = 21;
    private static final SecureRandom RAND = new SecureRandom();

    private NanoIdUtils() {
    }

    /**
     * 默认 21 字符 NanoId
     */
    public static String random() {
        return random(DEFAULT_SIZE, DEFAULT_ALPHABET);
    }

    /**
     * 指定长度
     */
    public static String random(int size) {
        return random(size, DEFAULT_ALPHABET);
    }

    /**
     * 完全自定义
     *
     * @param size     长度 > 0
     * @param alphabet 字母表长度 [2,256]
     */
    public static String random(int size, char[] alphabet) {
        if (size <= 0) throw new IllegalArgumentException("size must be > 0");
        if (alphabet == null || alphabet.length < 2 || alphabet.length > 256)
            throw new IllegalArgumentException("alphabet length must be 2..256");

        final int mask = (2 << (31 - Integer.numberOfLeadingZeros(alphabet.length - 1))) - 1;
        final int step = (int) Math.ceil(1.6 * mask * size / alphabet.length);
        final char[] id = new char[size];

        int count = 0;
        while (count < size) {
            byte[] bytes = new byte[step];
            RAND.nextBytes(bytes);
            for (int i = 0; i < step && count < size; i++) {
                int index = bytes[i] & mask;
                if (index < alphabet.length) {
                    id[count++] = alphabet[index];
                }
            }
        }
        return new String(id);
    }

}
