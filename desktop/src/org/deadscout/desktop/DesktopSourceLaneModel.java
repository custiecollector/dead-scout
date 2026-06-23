package org.deadscout.desktop;

import org.deadscout.core.CaptureSession;
import org.deadscout.core.PacketRecord;
import org.deadscout.core.PacketWorkbench;
import org.deadscout.core.PacketWorkbenchReport;
import org.deadscout.core.SignalObservation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DesktopSourceLaneModel {
    private static final String PACKET_RULE = "Packets are added only when a parser or decoder returns real frames.";
    private static final String OBSERVATION_RULE = "Raw IQ, source checks, no-data results, and topology hints stay in session observations/notes.";

    private DesktopSourceLaneModel() {}

    static final class LaneStatus {
        final String id;
        final String label;
        final String monitorPath;
        final String importPath;
        final String topologyPath;
        final String state;
        final int packets;
        final int observations;
        final boolean active;

        LaneStatus(String id, String label, String monitorPath, String importPath, String topologyPath,
                   String state, int packets, int observations, boolean active) {
            this.id = id;
            this.label = label;
            this.monitorPath = monitorPath;
            this.importPath = importPath;
            this.topologyPath = topologyPath;
            this.state = state;
            this.packets = packets;
            this.observations = observations;
            this.active = active;
        }

        String summaryLine() {
            return String.format(Locale.US, "%s%s · %s · packets=%d · observations=%d",
                    active ? "▶ " : "  ", label, state, packets, observations);
        }

        String detailBlock() {
            StringBuilder sb = new StringBuilder();
            sb.append(summaryLine()).append('\n');
            sb.append("    live: ").append(monitorPath).append('\n');
            sb.append("    import: ").append(importPath).append('\n');
            sb.append("    topology: ").append(topologyPath).append('\n');
            sb.append("    rule: ").append(PACKET_RULE).append(' ').append(OBSERVATION_RULE).append('\n');
            return sb.toString();
        }
    }

    static List<LaneStatus> statuses(CaptureSession session, ActiveSource activeSource) {
        ArrayList<LaneStatus> lanes = new ArrayList<>();
        PacketWorkbenchReport report = PacketWorkbench.analyze(session == null ? new ArrayList<PacketRecord>() : session.packets());
        lanes.add(new LaneStatus(
                "network",
                "Network interfaces",
                "Start network runs Windows pktmon, an existing dumpcap helper, or Linux tcpdump and decodes PCAP packets into the session.",
                "PCAP/PCAPNG and raw IPv4/IPv6 hex imports feed IP/Ethernet packets.",
                "Endpoint and conversation stats come from decoded source/destination fields.",
                stateFor(activeSource, ActiveSource.NETWORK, "network monitor"),
                packetCount(session, "ipv4", "ipv6", "tcp", "udp", "icmp", "ethernet", "network", "own-device"),
                observationCount(session, "packetdriver", "libpcap", "network", "tcpdump", "desktop-network"),
                activeSource == ActiveSource.NETWORK));
        lanes.add(new LaneStatus(
                "netA",
                "NetworkA / 802.11",
                "Start NetworkA runs Windows pktmon or a configured/existing monitor-mode helper and decodes packets; non-monitor captures are flagged clearly.",
                "Radiotap/802.11 PCAP/PCAPNG imports feed management, control, and data packets.",
                "APs, BSSIDs, EAPOL/deauth events, and conversations populate topology from decoded fields.",
                stateFor(activeSource, ActiveSource.NET_A, "NetworkA monitor"),
                packetCount(session, "802.11", "netA", "netA", "netlan", "radiotap", "eapol", "bssid", "ssid"),
                observationCount(session, "802.11", "netA", "netA", "netlan", "ap inventory", "desktop-netA"),
                activeSource == ActiveSource.NET_A));
        lanes.add(new LaneStatus(
                "auxiliary",
                "Auxiliary / AUX",
                "Start Auxiliary runs btmon, hcidump, or a configured Auxiliary HCI helper and decodes BTSnoop/HCI/AUX packets.",
                "HCI, snoop, sniffer text, or PCAP imports feed AUX ADV, SMP, GATT, L2CAP, and HCI packets.",
                "Device addresses, advertisements, pairing events, and GATT/L2CAP endpoints appear after decoding.",
                stateFor(activeSource, ActiveSource.AUXILIARY, "Auxiliary monitor"),
                packetCount(session, "auxiliary", "hci", "smp", "gatt", "l2cap", "advertisement"),
                observationCount(session, "auxiliary", "hci", "smp", "gatt", "l2cap", "desktop-auxiliary"),
                activeSource == ActiveSource.AUXILIARY));
        boolean sdrActive = activeSource == ActiveSource.USB || activeSource == ActiveSource.RTL || activeSource == ActiveSource.RTL_TCP;
        lanes.add(new LaneStatus(
                "sdr-radio",
                "SDR / radio",
                "Start USB/RTL/rtl_tcp runs rtl_433, rtl_sdr, or rtl_tcp capture helpers and routes returned IQ/JSON into review.",
                "IQ/.cu8/raw imports update waterfall review; decoded imports such as rtl_433 append packets when decoded.",
                "Frequency, RSSI/SNR, burst notes, and decoder-emitted IDs can correlate with packet conversations.",
                stateForSdr(activeSource),
                packetCount(session, "rtl_433", "rf", "sdr", "radio", "ads-b", "adsb", "acars", "ais", "aprs", "pocsag", "ook", "fsk", "fineoffset"),
                observationCount(session, "rtl", "rtl_tcp", "iq", "sdr", "radio", "waterfall", "desktop-usb", "desktop-rtl"),
                sdrActive));
        lanes.add(new LaneStatus(
                "sniffer",
                "Sniffer dongles",
                "Start sniffer runs a configured serial/extcap helper or dumpcap sniffer interface and decodes 802.15.4 frames.",
                "802.15.4 text, hex, or PCAP/PCAPNG imports feed packets when frame decoders parse MAC fields.",
                "PAN IDs, short/extended addresses, channels, LQI/RSSI, and conversations populate topology.",
                stateFor(activeSource, ActiveSource.SNIFFER, "sniffer monitor"),
                packetCount(session, "802.15.4", "ieee802154", "zigbee", "thread", "6lowpan", "sniffer", "pan id"),
                observationCount(session, "802.15.4", "ieee802154", "zigbee", "thread", "sniffer", "desktop-sniffer"),
                activeSource == ActiveSource.SNIFFER));
        boolean importActive = activeSource == ActiveSource.IMPORT;
        lanes.add(new LaneStatus(
                "pcap-import",
                "PCAP / PCAPNG / session import",
                "Press import to open saved captures and sessions for review; press again to stop the import lane.",
                "PCAP/PCAPNG, DeadScout sessions, rtl_433 JSON, HCI, raw hex, and IQ are routed by file type.",
                "Imported packets and observations feed every source status and topology view.",
                importActive ? "active" : "ready",
                session == null ? 0 : session.packets.size(),
                session == null ? 0 : session.observations.size(),
                importActive));
        lanes.add(new LaneStatus(
                "topology",
                "Topology data",
                "Topology updates from the current session; it does not start a separate capture.",
                "Decoded source/destination/channel/address fields populate endpoints, conversations, and findings.",
                "Current graph: " + report.endpoints.size() + " endpoints · " + report.conversations.size() + " conversations · " + report.findings.size() + " findings.",
                "derived",
                report.packetCount,
                session == null ? 0 : session.observations.size(),
                false));
        return lanes;
    }

    static String render(CaptureSession session, ActiveSource activeSource) {
        StringBuilder sb = new StringBuilder();
        sb.append("Desktop source status\n");
        sb.append("Active source: ").append(activeSource.label()).append(" · active monitors: ")
                .append(activeLaneCount(session, activeSource)).append("\n");
        sb.append(PACKET_RULE).append(' ').append(OBSERVATION_RULE).append("\n\n");
        for (LaneStatus lane : statuses(session, activeSource)) sb.append(lane.detailBlock()).append('\n');
        return sb.toString();
    }

    static int activeLaneCount(CaptureSession session, ActiveSource activeSource) {
        int count = 0;
        for (LaneStatus lane : statuses(session, activeSource)) if (lane.active) count++;
        return count;
    }

    static LaneStatus find(CaptureSession session, ActiveSource activeSource, String id) {
        for (LaneStatus lane : statuses(session, activeSource)) {
            if (lane.id.equals(id)) return lane;
        }
        return null;
    }

    private static String stateFor(ActiveSource activeSource, ActiveSource lane, String label) {
        if (activeSource == lane) return "active";
        if (activeSource == ActiveSource.NONE) return "ready";
        return "paused while " + activeSource.label() + " is active";
    }

    private static String stateForSdr(ActiveSource activeSource) {
        if (activeSource == ActiveSource.USB) return "active: USB";
        if (activeSource == ActiveSource.RTL) return "active: RTL";
        if (activeSource == ActiveSource.RTL_TCP) return "active: rtl_tcp";
        return activeSource == ActiveSource.NONE ? "ready" : "paused while " + activeSource.label() + " is active";
    }

    private static int packetCount(CaptureSession session, String... terms) {
        if (session == null) return 0;
        int count = 0;
        for (PacketRecord packet : session.packets) {
            if (containsAny(packetText(packet), terms)) count++;
        }
        return count;
    }

    private static int observationCount(CaptureSession session, String... terms) {
        if (session == null) return 0;
        int count = 0;
        for (SignalObservation observation : session.observations) {
            if (containsAny(observationText(observation), terms)) count++;
        }
        return count;
    }

    private static String packetText(PacketRecord packet) {
        StringBuilder sb = new StringBuilder();
        append(sb, packet.sourceId);
        append(sb, packet.channel);
        append(sb, packet.modulationGuess);
        append(sb, packet.decode.module);
        append(sb, packet.decode.protocol);
        append(sb, packet.decode.summary);
        for (Map.Entry<String, String> field : packet.decode.fields.entrySet()) {
            append(sb, field.getKey());
            append(sb, field.getValue());
        }
        for (Map.Entry<String, String> tag : packet.tags.entrySet()) {
            append(sb, tag.getKey());
            append(sb, tag.getValue());
        }
        return sb.toString().toLowerCase(Locale.US);
    }

    private static String observationText(SignalObservation observation) {
        StringBuilder sb = new StringBuilder();
        append(sb, observation.sourceId);
        append(sb, observation.modulation);
        append(sb, observation.note);
        return sb.toString().toLowerCase(Locale.US);
    }

    private static void append(StringBuilder sb, String value) {
        if (value != null && !value.isEmpty()) sb.append(' ').append(value);
    }

    private static boolean containsAny(String haystack, String... terms) {
        for (String term : terms) {
            if (haystack.contains(term.toLowerCase(Locale.US))) return true;
        }
        return false;
    }
}
