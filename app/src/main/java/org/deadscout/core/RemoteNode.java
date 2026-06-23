package org.deadscout.core;

import java.util.Locale;

public final class RemoteNode {
    public enum Kind { RTL_TCP, DEADSCOUT_NODE, ESP32_SNIFFER, NRF_SNIFFER, RASPBERRY_PI_SDR, PCAP_FEED, WEBSOCKET_STREAM }

    public final Kind kind;
    public final String label;
    public final String host;
    public final int port;
    public final String path;
    public final boolean secure;

    public RemoteNode(Kind kind, String label, String host, int port, String path, boolean secure) {
        this.kind = kind == null ? Kind.DEADSCOUT_NODE : kind;
        this.label = label == null ? "" : label;
        this.host = host == null ? "" : host;
        this.port = port;
        this.path = path == null ? "" : path;
        this.secure = secure;
        if (this.host.isEmpty()) throw new IllegalArgumentException("remote host required");
        if (this.port <= 0 || this.port > 65535) throw new IllegalArgumentException("remote port invalid");
    }

    public String endpoint() {
        String scheme;
        switch (kind) {
            case RTL_TCP: scheme = "rtl_tcp"; break;
            case WEBSOCKET_STREAM: scheme = secure ? "wss" : "ws"; break;
            case PCAP_FEED: scheme = secure ? "https" : "http"; break;
            default: scheme = secure ? "deadscouts" : "deadscout";
        }
        return scheme + "://" + host + ":" + port + (path.isEmpty() ? "" : path);
    }

    public String captureRoute() {
        switch (kind) {
            case RTL_TCP: return "rtl_tcp IQ stream → RTL-SDR pipeline → spectrum/waterfall/rtl_433/unknown burst";
            case ESP32_SNIFFER: return "ESP32-C6 serial/network 802.15.4 frames → sniffer reader → PCAPNG/session";
            case NRF_SNIFFER: return "nRF52840 sniffer frames → 802.15.4 routes";
            case RASPBERRY_PI_SDR: return "Raspberry Pi SDR box → IQ/PCAP feed → DeadScout controller";
            case PCAP_FEED: return "network PCAP/PCAPNG feed → import workbench";
            case WEBSOCKET_STREAM: return "WebSocket packet JSON/hex stream → decoder plugin routing";
            default: return "remote DeadScout node session/packet stream → local workbench";
        }
    }

    public String card() {
        return label + " · " + endpoint() + "\n" + captureRoute();
    }
}
