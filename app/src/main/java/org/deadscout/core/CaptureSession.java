package org.deadscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CaptureSession {
    public final String id;
    public final long startedMillis;
    public long stoppedMillis = 0L;
    public String sourceId = "";
    public long centerFrequencyHz = 0L;
    public int sampleRateHz = 0;
    public final ArrayList<SignalObservation> observations = new ArrayList<>();
    public final ArrayList<PacketRecord> packets = new ArrayList<>();
    public final ArrayList<String> notes = new ArrayList<>();
    public final ArrayList<String> tags = new ArrayList<>();
    public final ArrayList<String> snapshots = new ArrayList<>();

    public CaptureSession(String id, long startedMillis) {
        this.id = id;
        this.startedMillis = startedMillis;
    }

    public CaptureSession withSource(String sourceId, long centerFrequencyHz, int sampleRateHz) {
        this.sourceId = sourceId == null ? "" : sourceId;
        this.centerFrequencyHz = centerFrequencyHz;
        this.sampleRateHz = sampleRateHz;
        return this;
    }

    public CaptureSession finish(long stoppedMillis) {
        this.stoppedMillis = stoppedMillis;
        return this;
    }

    public CaptureSession addObservation(SignalObservation observation) {
        if (observation != null) observations.add(observation);
        return this;
    }

    public CaptureSession addPacket(PacketRecord packet) {
        if (packet != null) packets.add(packet);
        return this;
    }

    public CaptureSession addNote(String note) {
        if (note != null && !note.isEmpty()) notes.add(note);
        return this;
    }

    public CaptureSession addTag(String tag) {
        if (tag != null && !tag.trim().isEmpty() && !tags.contains(tag.trim())) tags.add(tag.trim());
        return this;
    }

    public CaptureSession addSnapshot(String snapshot) {
        if (snapshot != null && !snapshot.isEmpty()) snapshots.add(snapshot);
        return this;
    }

    public List<PacketRecord> packets() { return Collections.unmodifiableList(packets); }
    public List<SignalObservation> observations() { return Collections.unmodifiableList(observations); }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"format\":\"deadscout-session-v1\",");
        appendJson(sb, "id", id).append(',');
        appendJson(sb, "sourceId", sourceId).append(',');
        sb.append("\"startedMillis\":").append(startedMillis).append(',');
        sb.append("\"stoppedMillis\":").append(stoppedMillis).append(',');
        sb.append("\"centerFrequencyHz\":").append(centerFrequencyHz).append(',');
        sb.append("\"sampleRateHz\":").append(sampleRateHz).append(',');
        appendStringArray(sb, "tags", tags).append(',');
        appendStringArray(sb, "snapshots", snapshots).append(',');
        appendStringArray(sb, "notes", notes).append(',');
        sb.append("\"observations\":[");
        for (int i = 0; i < observations.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(observations.get(i).toJson());
        }
        sb.append("],\"packets\":[");
        for (int i = 0; i < packets.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(packets.get(i).toJson());
        }
        sb.append("]}");
        return sb.toString();
    }

    public static CaptureSession fromJson(String json) {
        if (json == null || !json.contains("deadscout-session-v1")) throw new IllegalArgumentException("not a DeadScout session bundle");
        String id = stringValue(json, "id", "deadscout-session-import");
        long started = longValue(json, "startedMillis", System.currentTimeMillis());
        CaptureSession session = new CaptureSession(id, started);
        session.sourceId = stringValue(json, "sourceId", "");
        session.stoppedMillis = longValue(json, "stoppedMillis", 0L);
        session.centerFrequencyHz = longValue(json, "centerFrequencyHz", 0L);
        session.sampleRateHz = (int) longValue(json, "sampleRateHz", 0L);
        session.tags.addAll(stringArray(json, "tags"));
        session.snapshots.addAll(stringArray(json, "snapshots"));
        session.notes.addAll(stringArray(json, "notes"));
        for (String o : objectArray(json, "observations")) session.addObservation(observationFromJson(o));
        for (String p : objectArray(json, "packets")) session.addPacket(packetFromJson(p));
        return session;
    }

    private static SignalObservation observationFromJson(String json) {
        return new SignalObservation(
                longValue(json, "timestampMillis", System.currentTimeMillis()),
                stringValue(json, "sourceId", "session-import"),
                longValue(json, "frequencyHz", 0L),
                (int) longValue(json, "sampleRateHz", 0L),
                (int) longValue(json, "bandwidthHz", 0L),
                doubleValue(json, "rssiDbm", 0.0),
                doubleValue(json, "snrDb", 0.0),
                stringValue(json, "modulation", ""),
                stringValue(json, "note", ""));
    }

    private static PacketRecord packetFromJson(String json) {
        LinkedHashMap<String, String> fields = objectFields(objectValue(json, "fields"));
        ProtocolDecode.Status status;
        try { status = ProtocolDecode.Status.valueOf(stringValue(json, "status", "UNKNOWN")); }
        catch (RuntimeException ex) { status = ProtocolDecode.Status.UNKNOWN; }
        ProtocolDecode decode = new ProtocolDecode(
                stringValue(json, "module", "session-import"),
                stringValue(json, "protocol", "unknown"),
                status,
                stringValue(json, "summary", "loaded from DeadScout session"),
                fields);
        return new PacketRecord(
                longValue(json, "timestampMillis", System.currentTimeMillis()),
                stringValue(json, "sourceId", "session-import"),
                longValue(json, "frequencyHz", 0L),
                stringValue(json, "channel", ""),
                doubleValue(json, "rssiDbm", 0.0),
                (int) longValue(json, "lqi", -1L),
                stringValue(json, "modulationGuess", ""),
                stringValue(json, "rawHex", ""),
                stringValue(json, "rawBits", ""),
                decode,
                Collections.<String, String>emptyMap());
    }

    private static StringBuilder appendStringArray(StringBuilder sb, String key, List<String> values) {
        sb.append('"').append(HexUtils.jsonEscape(key)).append("\":[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(HexUtils.jsonEscape(values.get(i))).append('"');
        }
        sb.append(']');
        return sb;
    }

    private static StringBuilder appendJson(StringBuilder sb, String key, String value) {
        sb.append('"').append(HexUtils.jsonEscape(key)).append("\":\"").append(HexUtils.jsonEscape(value)).append('"');
        return sb;
    }

    private static String stringValue(String json, String key, String fallback) {
        int idx = json.indexOf('"' + key + '"');
        if (idx < 0) return fallback;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return fallback;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return fallback;
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                if (c == 'n') out.append('\n');
                else if (c == 'r') out.append('\r');
                else if (c == 't') out.append('\t');
                else out.append(c);
                esc = false;
            } else if (c == '\\') esc = true;
            else if (c == '"') return out.toString();
            else out.append(c);
        }
        return fallback;
    }

    private static long longValue(String json, String key, long fallback) {
        String num = numberToken(json, key);
        if (num.isEmpty()) return fallback;
        try { return (long) Double.parseDouble(num); } catch (RuntimeException ex) { return fallback; }
    }

    private static double doubleValue(String json, String key, double fallback) {
        String num = numberToken(json, key);
        if (num.isEmpty()) return fallback;
        try { return Double.parseDouble(num); } catch (RuntimeException ex) { return fallback; }
    }

    private static String numberToken(String json, String key) {
        int idx = json.indexOf('"' + key + '"');
        if (idx < 0) return "";
        int colon = json.indexOf(':', idx);
        if (colon < 0) return "";
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'E' || c == 'e') end++;
            else break;
        }
        return json.substring(start, end);
    }

    private static ArrayList<String> stringArray(String json, String key) {
        ArrayList<String> out = new ArrayList<>();
        String array = arrayValue(json, key);
        if (array.isEmpty()) return out;
        int i = 0;
        while (i < array.length()) {
            int start = array.indexOf('"', i);
            if (start < 0) break;
            StringBuilder item = new StringBuilder();
            boolean esc = false;
            for (int j = start + 1; j < array.length(); j++) {
                char c = array.charAt(j);
                if (esc) {
                    if (c == 'n') item.append('\n');
                    else if (c == 'r') item.append('\r');
                    else if (c == 't') item.append('\t');
                    else item.append(c);
                    esc = false;
                } else if (c == '\\') esc = true;
                else if (c == '"') { out.add(item.toString()); i = j + 1; break; }
                else item.append(c);
            }
            if (i <= start) break;
        }
        return out;
    }

    private static String arrayValue(String json, String key) {
        int idx = json.indexOf('"' + key + '"');
        if (idx < 0) return "";
        int colon = json.indexOf(':', idx);
        int start = json.indexOf('[', colon < 0 ? idx : colon);
        if (start < 0) return "";
        int end = matching(json, start, '[', ']');
        return end < 0 ? "" : json.substring(start + 1, end);
    }

    private static ArrayList<String> objectArray(String json, String key) {
        ArrayList<String> out = new ArrayList<>();
        String array = arrayValue(json, key);
        int i = 0;
        while (i < array.length()) {
            int start = array.indexOf('{', i);
            if (start < 0) break;
            int end = matching(array, start, '{', '}');
            if (end < 0) break;
            out.add(array.substring(start, end + 1));
            i = end + 1;
        }
        return out;
    }

    private static String objectValue(String json, String key) {
        int idx = json.indexOf('"' + key + '"');
        if (idx < 0) return "";
        int colon = json.indexOf(':', idx);
        int start = json.indexOf('{', colon < 0 ? idx : colon);
        if (start < 0) return "";
        int end = matching(json, start, '{', '}');
        return end < 0 ? "" : json.substring(start + 1, end);
    }

    private static LinkedHashMap<String, String> objectFields(String json) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        int i = 0;
        while (i < json.length()) {
            int keyStart = json.indexOf('"', i);
            if (keyStart < 0) break;
            int keyEnd = findStringEnd(json, keyStart);
            if (keyEnd < 0) break;
            String key = unescape(json.substring(keyStart + 1, keyEnd));
            int colon = json.indexOf(':', keyEnd);
            int valStart = colon < 0 ? -1 : json.indexOf('"', colon);
            if (valStart < 0) break;
            int valEnd = findStringEnd(json, valStart);
            if (valEnd < 0) break;
            out.put(key, unescape(json.substring(valStart + 1, valEnd)));
            i = valEnd + 1;
        }
        return out;
    }

    private static int matching(String text, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean esc = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inString = false;
            } else if (c == '"') inString = true;
            else if (c == open) depth++;
            else if (c == close && --depth == 0) return i;
        }
        return -1;
    }

    private static int findStringEnd(String text, int quote) {
        boolean esc = false;
        for (int i = quote + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (esc) esc = false;
            else if (c == '\\') esc = true;
            else if (c == '"') return i;
        }
        return -1;
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                if (c == 'n') out.append('\n');
                else if (c == 'r') out.append('\r');
                else if (c == 't') out.append('\t');
                else out.append(c);
                esc = false;
            } else if (c == '\\') esc = true;
            else out.append(c);
        }
        return out.toString();
    }
}
