package org.deadscout.core;

import java.util.LinkedHashMap;
import java.util.Locale;

public final class Ieee802154Frame {
    public final byte[] raw;
    public final int frameControl;
    public final int frameType;
    public final int sequence;
    public final boolean securityEnabled;
    public final boolean ackRequest;
    public final boolean panCompression;
    public final int destPanId;
    public final String destAddress;
    public final int sourcePanId;
    public final String sourceAddress;
    public final byte[] payload;

    public Ieee802154Frame(byte[] raw, int frameControl, int frameType, int sequence, boolean securityEnabled,
                           boolean ackRequest, boolean panCompression, int destPanId, String destAddress,
                           int sourcePanId, String sourceAddress, byte[] payload) {
        this.raw = raw;
        this.frameControl = frameControl;
        this.frameType = frameType;
        this.sequence = sequence;
        this.securityEnabled = securityEnabled;
        this.ackRequest = ackRequest;
        this.panCompression = panCompression;
        this.destPanId = destPanId;
        this.destAddress = destAddress;
        this.sourcePanId = sourcePanId;
        this.sourceAddress = sourceAddress;
        this.payload = payload;
    }

    public String frameTypeName() {
        switch (frameType) {
            case 0: return "Beacon";
            case 1: return "Data";
            case 2: return "Ack";
            case 3: return "MAC command";
            case 5: return "Multipurpose";
            case 6: return "Fragment";
            case 7: return "Extended";
            default: return "Reserved";
        }
    }

    public String summary() {
        String secure = securityEnabled ? " encrypted/security-enabled" : "";
        return String.format(Locale.US, "%s frame seq %d PAN %04X %s → %s%s", frameTypeName(), sequence, destPanId, sourceAddress, destAddress, secure);
    }

    public LinkedHashMap<String, String> fields() {
        LinkedHashMap<String, String> f = new LinkedHashMap<>();
        f.put("frame_type", frameTypeName());
        f.put("sequence", Integer.toString(sequence));
        f.put("dest_pan", String.format(Locale.US, "%04X", destPanId));
        f.put("dest", destAddress);
        f.put("source_pan", sourcePanId >= 0 ? String.format(Locale.US, "%04X", sourcePanId) : "");
        f.put("source", sourceAddress);
        f.put("security_enabled", Boolean.toString(securityEnabled));
        f.put("ack_request", Boolean.toString(ackRequest));
        f.put("pan_compression", Boolean.toString(panCompression));
        f.put("payload_ascii", HexUtils.ascii(payload));
        return f;
    }
}
