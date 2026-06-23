package org.deadscout.core;

public final class CaptureCapability {
    public final String id;
    public final String label;
    public final long startHz;
    public final long endHz;
    public final String captureScope;
    public final String requiredSource;
    public final String packetFormat;
    public final String decoderStack;
    public final boolean surroundingData;
    public final boolean androidBuiltIn;

    public CaptureCapability(String id, String label, long startHz, long endHz, String captureScope,
                             String requiredSource, String packetFormat, String decoderStack,
                             boolean surroundingData, boolean androidBuiltIn) {
        this.id = id == null ? "" : id;
        this.label = label == null ? "" : label;
        this.startHz = startHz;
        this.endHz = endHz;
        this.captureScope = captureScope == null ? "" : captureScope;
        this.requiredSource = requiredSource == null ? "" : requiredSource;
        this.packetFormat = packetFormat == null ? "" : packetFormat;
        this.decoderStack = decoderStack == null ? "" : decoderStack;
        this.surroundingData = surroundingData;
        this.androidBuiltIn = androidBuiltIn;
    }

    public String frequencyLabel() {
        if (startHz <= 0 && endHz <= 0) return "baseband/metadata";
        if (startHz == endHz) return formatHz(startHz);
        return formatHz(startHz) + "-" + formatHz(endHz);
    }

    public String displayCard() {
        return frequencyLabel()
                + "\nScope: " + captureScope
                + "\nSource: " + requiredSource
                + "\nFormat: " + packetFormat
                + "\nDecode: " + decoderStack
                + "\nSurrounding: " + (surroundingData ? "yes" : "device/API only")
                + "\nAndroid built-in: " + (androidBuiltIn ? "yes" : "external/import source");
    }

    private static String formatHz(long hz) {
        if (hz >= 1_000_000_000L) return String.format(java.util.Locale.US, "%.3f GHz", hz / 1_000_000_000.0);
        if (hz >= 1_000_000L) return String.format(java.util.Locale.US, "%.3f MHz", hz / 1_000_000.0);
        if (hz >= 1_000L) return String.format(java.util.Locale.US, "%.3f kHz", hz / 1_000.0);
        return hz + " Hz";
    }
}
