package org.deadscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RemoteNodeManager {
    public final ArrayList<RemoteNode> nodes = new ArrayList<>();

    public RemoteNodeManager add(RemoteNode node) { if (node != null) nodes.add(node); return this; }
    public List<RemoteNode> nodes() { return Collections.unmodifiableList(nodes); }

    public static RemoteNode parse(String label, String uri) {
        if (uri == null) throw new IllegalArgumentException("uri required");
        String lower = uri.toLowerCase(Locale.US);
        boolean secure = lower.startsWith("wss://") || lower.startsWith("https://") || lower.startsWith("deadscouts://");
        RemoteNode.Kind kind;
        if (lower.startsWith("rtl_tcp://") || lower.startsWith("rtl://")) kind = RemoteNode.Kind.RTL_TCP;
        else if (lower.startsWith("ws://") || lower.startsWith("wss://")) kind = RemoteNode.Kind.WEBSOCKET_STREAM;
        else if (lower.contains("esp32")) kind = RemoteNode.Kind.ESP32_SNIFFER;
        else if (lower.contains("nrf")) kind = RemoteNode.Kind.NRF_SNIFFER;
        else if (lower.contains("pcap")) kind = RemoteNode.Kind.PCAP_FEED;
        else kind = RemoteNode.Kind.DEADSCOUT_NODE;
        String noScheme = uri.substring(uri.indexOf("://") >= 0 ? uri.indexOf("://") + 3 : 0);
        String path = "";
        int slash = noScheme.indexOf('/');
        if (slash >= 0) { path = noScheme.substring(slash); noScheme = noScheme.substring(0, slash); }
        String host = noScheme;
        int port = defaultPort(kind, secure);
        int colon = noScheme.lastIndexOf(':');
        if (colon > 0) {
            host = noScheme.substring(0, colon);
            try { port = Integer.parseInt(noScheme.substring(colon + 1)); } catch (RuntimeException ignored) { port = defaultPort(kind, secure); }
        }
        return new RemoteNode(kind, label == null || label.isEmpty() ? host : label, host, port, path, secure);
    }

    public String summary() {
        if (nodes.isEmpty()) return "No remote nodes configured";
        StringBuilder sb = new StringBuilder();
        for (RemoteNode n : nodes) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(n.card());
        }
        return sb.toString();
    }

    public static RemoteNodeManager configuredController() {
        return new RemoteNodeManager();
    }

    private static int defaultPort(RemoteNode.Kind kind, boolean secure) {
        switch (kind) {
            case RTL_TCP: return 1234;
            case WEBSOCKET_STREAM: return secure ? 443 : 80;
            case PCAP_FEED: return secure ? 443 : 80;
            default: return secure ? 9443 : 9090;
        }
    }
}
