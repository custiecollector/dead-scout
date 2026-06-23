package org.deadscout.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SessionDatabase {
    private final File root;
    private final File sessionsDir;
    private final File indexFile;

    public SessionDatabase(File root) {
        this.root = root;
        this.sessionsDir = new File(root, "sessions");
        this.indexFile = new File(root, "index.tsv");
        if (!sessionsDir.exists()) sessionsDir.mkdirs();
    }

    public Entry saveSession(CaptureSession session, List<String> tags) throws IOException {
        if (session == null) throw new IllegalArgumentException("session required");
        String safeId = sanitize(session.id);
        File out = new File(sessionsDir, safeId + ".json");
        String json = session.toJson();
        write(out, json);
        Entry entry = new Entry(session.id, session.startedMillis, session.packets.size(), session.observations.size(),
                tags == null ? Collections.<String>emptyList() : tags, out.getAbsolutePath(), firstNote(session));
        ArrayList<Entry> entries = new ArrayList<>(listSessions());
        boolean replaced = false;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(entry.id)) { entries.set(i, entry); replaced = true; break; }
        }
        if (!replaced) entries.add(entry);
        writeIndex(entries);
        return entry;
    }

    public List<Entry> listSessions() throws IOException {
        if (!indexFile.exists()) return Collections.emptyList();
        ArrayList<Entry> out = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(indexFile), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                out.add(Entry.fromTsv(line));
            }
        } finally { br.close(); }
        return out;
    }

    public List<Entry> search(String query) throws IOException {
        String q = query == null ? "" : query.toLowerCase(Locale.US);
        ArrayList<Entry> out = new ArrayList<>();
        for (Entry e : listSessions()) {
            String hay = (e.id + " " + e.summary + " " + e.tags).toLowerCase(Locale.US);
            if (q.isEmpty() || hay.contains(q)) out.add(e);
        }
        return out;
    }

    public String readSessionJson(String id) throws IOException {
        File f = new File(sessionsDir, sanitize(id) + ".json");
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally { br.close(); }
    }

    public String exportSelectedPackets(CaptureSession session, List<Integer> packetIndexes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"format\":\"deadscout-selected-packets-v1\",\"sourceSession\":\"").append(HexUtils.jsonEscape(session.id)).append("\",\"packets\":[");
        boolean first = true;
        for (Integer idx : packetIndexes) {
            if (idx == null || idx < 0 || idx >= session.packets.size()) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append(session.packets.get(idx).toJson());
        }
        sb.append("]}");
        return sb.toString();
    }

    private void writeIndex(List<Entry> entries) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) sb.append(e.toTsv()).append('\n');
        write(indexFile, sb.toString());
    }

    private void write(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try { w.write(text); } finally { w.close(); }
    }

    private static String sanitize(String id) { return (id == null || id.isEmpty() ? "session" : id).replaceAll("[^A-Za-z0-9._-]", "_"); }

    private static String firstNote(CaptureSession session) { return session.notes.isEmpty() ? "" : session.notes.get(0); }

    public static final class Entry {
        public final String id;
        public final long startedMillis;
        public final int packetCount;
        public final int observationCount;
        public final List<String> tags;
        public final String path;
        public final String summary;

        public Entry(String id, long startedMillis, int packetCount, int observationCount, List<String> tags, String path, String summary) {
            this.id = id == null ? "" : id;
            this.startedMillis = startedMillis;
            this.packetCount = packetCount;
            this.observationCount = observationCount;
            this.tags = new ArrayList<>(tags == null ? Collections.<String>emptyList() : tags);
            this.path = path == null ? "" : path;
            this.summary = summary == null ? "" : summary;
        }

        public String toTsv() {
            return esc(id) + '\t' + startedMillis + '\t' + packetCount + '\t' + observationCount + '\t' + esc(join(tags)) + '\t' + esc(path) + '\t' + esc(summary);
        }

        public static Entry fromTsv(String line) {
            String[] p = line.split("\\t", -1);
            ArrayList<String> tags = new ArrayList<>();
            if (p.length > 4 && !unesc(p[4]).isEmpty()) for (String t : unesc(p[4]).split(",")) tags.add(t);
            return new Entry(unesc(p.length > 0 ? p[0] : ""), parseLong(p, 1), (int) parseLong(p, 2), (int) parseLong(p, 3), tags,
                    unesc(p.length > 5 ? p[5] : ""), unesc(p.length > 6 ? p[6] : ""));
        }

        public String card() {
            return id + " · packets " + packetCount + " · tags " + tags + "\n" + summary + "\n" + path;
        }

        private static long parseLong(String[] parts, int index) { try { return Long.parseLong(parts[index]); } catch (RuntimeException ex) { return 0L; } }
        private static String join(List<String> tags) { StringBuilder sb = new StringBuilder(); for (String t : tags) { if (sb.length() > 0) sb.append(','); sb.append(t); } return sb.toString(); }
        private static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n"); }
        private static String unesc(String s) { return s == null ? "" : s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\"); }
    }
}
