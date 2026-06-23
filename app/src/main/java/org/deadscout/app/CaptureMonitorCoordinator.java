package org.deadscout.app;

/**
 * Tracks the single operator-facing capture source. MainActivity still owns the
 * hardware threads and sessions, but active-source state lives here so UI panels
 * and start/stop paths share one source of truth.
 */
final class CaptureMonitorCoordinator {
    static final String IDLE = "idle";
    static final String NONE = "none";
    static final String RTL = "rtl";
    static final String RTL_TCP = "rtltcp";
    static final String AUDIO = "audio";
    static final String ANDROID = "android";
    static final String USB = "usb";
    static final String SWEEP = "sweep";
    static final String IMPORT = "import";
    static final String ANDROID_PACKET = "android-packet";
    static final String TRAINING = "training";

    private String activeKind = IDLE;

    String activeKind() {
        return activeKind;
    }

    void setActive(String kind) {
        activeKind = normalize(kind);
    }

    boolean clearIfActive(String kind) {
        if (kind == null || normalize(kind).equals(activeKind)) {
            activeKind = IDLE;
            return true;
        }
        return false;
    }

    boolean shouldStopForKeep(String keep, String kind) {
        return !normalize(kind).equals(normalize(keep));
    }

    boolean shouldResetAfterStop(String keep) {
        return NONE.equals(keep);
    }

    void reset() {
        activeKind = IDLE;
    }

    private static String normalize(String kind) {
        if (kind == null) return IDLE;
        String trimmed = kind.trim();
        return trimmed.isEmpty() || "none".equals(trimmed) ? IDLE : trimmed;
    }
}
