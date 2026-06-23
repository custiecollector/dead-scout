package org.deadscout.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PacketFilter {
    private PacketFilter() {}

    public static List<PacketRecord> filter(List<PacketRecord> packets, String query) {
        ArrayList<PacketRecord> out = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            out.addAll(packets);
            return out;
        }
        for (PacketRecord p : packets) if (matches(p, query)) out.add(p);
        return out;
    }

    public static boolean matches(PacketRecord p, String query) {
        if (p == null) return false;
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return true;
        String lower = q.toLowerCase(Locale.US);
        if (lower.startsWith("protocol:")) return contains(p.decode.protocol, q.substring(9));
        if (lower.startsWith("module:")) return contains(p.decode.module, q.substring(7));
        if (lower.startsWith("source:")) return contains(p.sourceId, q.substring(7));
        if (lower.startsWith("channel:")) return contains(p.channel, q.substring(8));
        int eq = q.indexOf('=');
        if (eq > 0) {
            String key = q.substring(0, eq).trim();
            String value = q.substring(eq + 1).trim();
            String field = p.decode.fields.get(key);
            if (field != null) return contains(field, value);
            for (Map.Entry<String, String> e : p.decode.fields.entrySet()) {
                if (contains(e.getKey(), key) && contains(e.getValue(), value)) return true;
            }
            return false;
        }
        return contains(p.decode.protocol, q) || contains(p.decode.summary, q) || contains(p.sourceId, q)
                || contains(p.channel, q) || contains(p.rawHex, q) || contains(p.rawBits, q)
                || p.decode.fields.toString().toLowerCase(Locale.US).contains(lower);
    }

    private static boolean contains(String text, String needle) {
        return text != null && needle != null && text.toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US).trim());
    }
}
