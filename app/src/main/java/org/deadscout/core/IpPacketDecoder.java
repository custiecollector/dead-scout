package org.deadscout.core;

import java.util.LinkedHashMap;

public final class IpPacketDecoder {
    private IpPacketDecoder() {}

    public static PacketRecord decodeIpHex(String hex, String sourceId, String transportHint) {
        byte[] raw = HexUtils.fromHex(hex);
        if (raw.length < 1) throw new IllegalArgumentException("empty packet");
        int version = (raw[0] >> 4) & 0x0F;
        if (version == 4) return decodeIpv4(raw, sourceId, transportHint);
        if (version == 6) return decodeIpv6(raw, sourceId, transportHint);
        throw new IllegalArgumentException("unsupported IP version " + version);
    }

    private static PacketRecord decodeIpv4(byte[] raw, String sourceId, String transportHint) {
        if (raw.length < 20) throw new IllegalArgumentException("IPv4 packet too short");
        int ihlBytes = (raw[0] & 0x0F) * 4;
        if (ihlBytes < 20 || raw.length < ihlBytes) throw new IllegalArgumentException("invalid IPv4 header length");
        int totalLength = ((raw[2] & 0xFF) << 8) | (raw[3] & 0xFF);
        int proto = raw[9] & 0xFF;
        String src = ipv4(raw, 12);
        String dst = ipv4(raw, 16);
        LinkedHashMap<String, String> f = new LinkedHashMap<>();
        f.put("ip_version", "4");
        f.put("transport_hint", transportHint == null ? "unknown" : transportHint);
        f.put("src", src);
        f.put("dst", dst);
        f.put("protocol_number", Integer.toString(proto));
        f.put("protocol", protocolName(proto));
        f.put("ttl", Integer.toString(raw[8] & 0xFF));
        f.put("total_length", Integer.toString(totalLength));
        ProtocolDecode decode = ProtocolDecode.decoded("packet-data", "IPv4/" + protocolName(proto),
                "Own-device/imported packet metadata " + src + " → " + dst, f);
        return new PacketRecord(System.currentTimeMillis(), sourceId, 0L, transportHint, 0, -1,
                "IP packet metadata", HexUtils.toHex(raw), "", decode, null);
    }

    private static PacketRecord decodeIpv6(byte[] raw, String sourceId, String transportHint) {
        if (raw.length < 40) throw new IllegalArgumentException("IPv6 packet too short");
        int nextHeader = raw[6] & 0xFF;
        int payloadLength = ((raw[4] & 0xFF) << 8) | (raw[5] & 0xFF);
        String src = ipv6(raw, 8);
        String dst = ipv6(raw, 24);
        LinkedHashMap<String, String> f = new LinkedHashMap<>();
        f.put("ip_version", "6");
        f.put("transport_hint", transportHint == null ? "unknown" : transportHint);
        f.put("src", src);
        f.put("dst", dst);
        f.put("protocol_number", Integer.toString(nextHeader));
        f.put("protocol", protocolName(nextHeader));
        f.put("hop_limit", Integer.toString(raw[7] & 0xFF));
        f.put("payload_length", Integer.toString(payloadLength));
        ProtocolDecode decode = ProtocolDecode.decoded("packet-data", "IPv6/" + protocolName(nextHeader),
                "Own-device/imported packet metadata " + src + " → " + dst, f);
        return new PacketRecord(System.currentTimeMillis(), sourceId, 0L, transportHint, 0, -1,
                "IP packet metadata", HexUtils.toHex(raw), "", decode, null);
    }

    public static String protocolName(int proto) {
        switch (proto) {
            case 1: return "ICMP";
            case 6: return "TCP";
            case 17: return "UDP";
            case 58: return "ICMPv6";
            default: return "IP-" + proto;
        }
    }

    private static String ipv4(byte[] raw, int offset) {
        return (raw[offset] & 0xFF) + "." + (raw[offset + 1] & 0xFF) + "." + (raw[offset + 2] & 0xFF) + "." + (raw[offset + 3] & 0xFF);
    }

    private static String ipv6(byte[] raw, int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i += 2) {
            if (i > 0) sb.append(':');
            int part = ((raw[offset + i] & 0xFF) << 8) | (raw[offset + i + 1] & 0xFF);
            sb.append(Integer.toHexString(part));
        }
        return sb.toString();
    }
}
