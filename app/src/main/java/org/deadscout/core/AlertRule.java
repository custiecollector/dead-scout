package org.deadscout.core;

import java.util.Locale;

public final class AlertRule {
    public enum Type { FREQUENCY_ACTIVE, DEVICE_APPEARS, UNKNOWN_BURST_REPEATS, PAN_ID_SEEN, RTL433_MODEL, CELL_CHANGE, SIGNAL_THRESHOLD, PACKET_FIELD_MATCH, PROTOCOL_SEEN }

    public final String id;
    public final Type type;
    public final String field;
    public final String value;
    public final long frequencyHz;
    public final long toleranceHz;
    public final double thresholdDbm;

    public AlertRule(String id, Type type, String field, String value, long frequencyHz, long toleranceHz, double thresholdDbm) {
        this.id = id == null ? "rule" : id;
        this.type = type;
        this.field = field == null ? "" : field;
        this.value = value == null ? "" : value;
        this.frequencyHz = frequencyHz;
        this.toleranceHz = toleranceHz;
        this.thresholdDbm = thresholdDbm;
    }

    public static AlertRule frequencyActive(String id, long frequencyHz, long toleranceHz, double thresholdDbm) {
        return new AlertRule(id, Type.FREQUENCY_ACTIVE, "", "", frequencyHz, toleranceHz, thresholdDbm);
    }

    public static AlertRule fieldMatch(String id, String field, String value) {
        return new AlertRule(id, Type.PACKET_FIELD_MATCH, field, value, 0L, 0L, 0.0);
    }

    public static AlertRule protocolSeen(String id, String protocol) {
        return new AlertRule(id, Type.PROTOCOL_SEEN, "protocol", protocol, 0L, 0L, 0.0);
    }

    public boolean matches(PacketRecord packet) {
        if (packet == null) return false;
        switch (type) {
            case FREQUENCY_ACTIVE:
                return packet.frequencyHz > 0 && Math.abs(packet.frequencyHz - frequencyHz) <= toleranceHz && (thresholdDbm == 0.0 || packet.rssiDbm >= thresholdDbm);
            case SIGNAL_THRESHOLD:
                return packet.rssiDbm >= thresholdDbm;
            case PROTOCOL_SEEN:
                return contains(packet.decode.protocol, value) || contains(packet.decode.module, value);
            case RTL433_MODEL:
                return contains(packet.decode.protocol, "rtl_433") && contains(packet.decode.summary, value);
            case PAN_ID_SEEN:
                return contains(packet.decode.fields.get("dest_pan"), value) || contains(packet.decode.fields.get("source_pan"), value);
            case DEVICE_APPEARS:
                return contains(packet.decode.fields.get("src"), value) || contains(packet.decode.fields.get("dst"), value)
                        || contains(packet.decode.fields.get("addr2_transmitter"), value) || contains(packet.decode.fields.get("device_address"), value)
                        || contains(packet.decode.fields.get("address"), value) || contains(packet.decode.fields.get("id"), value);
            case UNKNOWN_BURST_REPEATS:
                return packet.decode.protocol.toLowerCase(Locale.US).contains("unknown") && contains(packet.rawBits, value);
            case PACKET_FIELD_MATCH:
                return contains(packet.decode.fields.get(field), value) || ("protocol".equals(field) && contains(packet.decode.protocol, value));
            case CELL_CHANGE:
                return contains(packet.decode.fields.get("pci"), value) || contains(packet.decode.fields.get("tac"), value);
            default:
                return false;
        }
    }

    public boolean matches(SignalObservation obs) {
        if (obs == null) return false;
        if (type == Type.FREQUENCY_ACTIVE) return obs.frequencyHz > 0 && Math.abs(obs.frequencyHz - frequencyHz) <= toleranceHz && obs.rssiDbm >= thresholdDbm;
        if (type == Type.SIGNAL_THRESHOLD) return obs.rssiDbm >= thresholdDbm;
        return contains(obs.note, value) || contains(obs.modulation, value) || contains(obs.sourceId, value);
    }

    public String display() {
        return id + " · " + type + (frequencyHz > 0 ? " · " + frequencyHz + " Hz ±" + toleranceHz : "") + (value.isEmpty() ? "" : " · " + field + "=" + value);
    }

    private static boolean contains(String hay, String needle) {
        if (hay == null || needle == null || needle.isEmpty()) return false;
        return hay.toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US));
    }
}
