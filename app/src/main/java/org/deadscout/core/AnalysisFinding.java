package org.deadscout.core;

public final class AnalysisFinding {
    public enum Severity { INFO, NOTICE, WARNING, CRITICAL }

    public final Severity severity;
    public final String category;
    public final String title;
    public final String detail;
    public final int packetIndex;

    public AnalysisFinding(Severity severity, String category, String title, String detail, int packetIndex) {
        this.severity = severity == null ? Severity.INFO : severity;
        this.category = category == null ? "general" : category;
        this.title = title == null ? "" : title;
        this.detail = detail == null ? "" : detail;
        this.packetIndex = packetIndex;
    }

    public String displayLine() {
        String prefix = packetIndex >= 0 ? "#" + packetIndex + " · " : "";
        return severity + " · " + category + " · " + prefix + title + (detail.isEmpty() ? "" : "\n" + detail);
    }
}
