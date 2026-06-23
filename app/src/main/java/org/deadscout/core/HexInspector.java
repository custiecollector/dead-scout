package org.deadscout.core;

import java.util.Locale;

public final class HexInspector {
    private HexInspector() {}

    public static String hexdump(String hex, int maxBytes) {
        byte[] raw = HexUtils.fromHex(hex == null ? "" : hex);
        int n = Math.min(raw.length, Math.max(0, maxBytes));
        StringBuilder sb = new StringBuilder();
        for (int offset = 0; offset < n; offset += 16) {
            sb.append(String.format(Locale.US, "%04X  ", offset));
            StringBuilder ascii = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                int idx = offset + i;
                if (idx < n) {
                    int b = raw[idx] & 0xFF;
                    sb.append(String.format(Locale.US, "%02X ", b));
                    ascii.append(b >= 32 && b <= 126 ? (char) b : '.');
                } else {
                    sb.append("   ");
                    ascii.append(' ');
                }
                if (i == 7) sb.append(' ');
            }
            sb.append(" ").append(ascii).append('\n');
        }
        if (raw.length > n) sb.append("… ").append(raw.length - n).append(" more bytes");
        return sb.toString().trim();
    }

    public static double entropyBitsPerByte(String hex) {
        byte[] raw = HexUtils.fromHex(hex == null ? "" : hex);
        if (raw.length == 0) return 0.0;
        int[] counts = new int[256];
        for (byte b : raw) counts[b & 0xFF]++;
        double entropy = 0.0;
        for (int c : counts) {
            if (c == 0) continue;
            double p = c / (double) raw.length;
            entropy -= p * (Math.log(p) / Math.log(2.0));
        }
        return entropy;
    }
}
