package org.deadscout.core;

public final class CaptureSourceDescriptor {
    public enum SourceKind {
        RTL_SDR_USB, RTL_TCP, IMPORTED_FILE, RTL_433_JSON, IEEE802154_SNIFFER,
        PACKET_DATA_IMPORT, OWN_DEVICE_PACKET_CAPTURE, PACKET_WORKBENCH, WIDEBAND_SDR,
        SURROUNDING_CAPTURE_MATRIX, IMPORT_WORKBENCH, SPECTRUM_WATERFALL, RTL_SDR_IQ_PIPELINE,
        SESSION_LIBRARY, CORRELATION_ENGINE, SIGNAL_FINGERPRINT, ALERT_WATCH, DECODER_PLUGIN,
        REPORT_GENERATOR, DISTRIBUTED_REMOTE_NODE, ACARS_AIS_DECODER, POCSAG_APRS_DECODER
    }

    public final String id;
    public final String label;
    public final SourceKind kind;
    public final String summary;
    public final boolean availableInPublicBuild;
    public final boolean requiresHardware;
    public final boolean usesNetwork;
    public final String scope;

    public CaptureSourceDescriptor(String id, String label, SourceKind kind, String summary,
                                   boolean availableInPublicBuild, boolean requiresHardware,
                                   boolean usesNetwork, String scope) {
        this.id = id; this.label = label; this.kind = kind; this.summary = summary;
        this.availableInPublicBuild = availableInPublicBuild; this.requiresHardware = requiresHardware;
        this.usesNetwork = usesNetwork; this.scope = scope;
    }

    public String capabilityLine() {
        return (requiresHardware ? "Hardware/config required" : "No attached hardware required")
                + " · " + (usesNetwork ? "user-specified network path" : "local/offline path")
                + " · " + scope;
    }
}
