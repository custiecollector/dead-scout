package org.deadscout.core;

public final class AlertEvent {
    public final long timeMillis;
    public final String ruleId;
    public final String title;
    public final String detail;
    public final int packetIndex;

    public AlertEvent(long timeMillis, String ruleId, String title, String detail, int packetIndex) {
        this.timeMillis = timeMillis;
        this.ruleId = ruleId == null ? "" : ruleId;
        this.title = title == null ? "" : title;
        this.detail = detail == null ? "" : detail;
        this.packetIndex = packetIndex;
    }

    public String displayLine() {
        return ruleId + " · " + title + (packetIndex >= 0 ? " · packet #" + packetIndex : "") + (detail.isEmpty() ? "" : "\n" + detail);
    }
}
