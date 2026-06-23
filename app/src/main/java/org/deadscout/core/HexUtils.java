package org.deadscout.core;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class HexUtils {
    private HexUtils() {}

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02X", b & 0xFF));
        return sb.toString();
    }

    public static byte[] fromHex(String hex) {
        String cleaned = hex.replaceAll("[^0-9A-Fa-f]", "");
        if ((cleaned.length() & 1) != 0) throw new IllegalArgumentException("hex string must have an even number of digits");
        byte[] out = new byte[cleaned.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(cleaned.charAt(i * 2), 16);
            int lo = Character.digit(cleaned.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("invalid hex digit");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static int le16(byte[] bytes, int offset) {
        if (offset + 1 >= bytes.length) throw new IllegalArgumentException("need two bytes at offset " + offset);
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    public static long le64(byte[] bytes, int offset) {
        if (offset + 7 >= bytes.length) throw new IllegalArgumentException("need eight bytes at offset " + offset);
        long value = 0;
        for (int i = 7; i >= 0; i--) value = (value << 8) | (bytes[offset + i] & 0xFFL);
        return value;
    }

    public static String ascii(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            int c = b & 0xFF;
            sb.append(c >= 0x20 && c <= 0x7E ? (char) c : '.');
        }
        return sb.toString();
    }

    public static String jsonEscape(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
