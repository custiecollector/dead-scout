package org.deadscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PcapReader {
    public static final int LINKTYPE_NULL = 0;
    public static final int LINKTYPE_ETHERNET = 1;
    public static final int LINKTYPE_RAW_IP = 101;
    public static final int LINKTYPE_LINUX_SLL = 113;
    public static final int LINKTYPE_IEEE802_11 = 105;
    public static final int LINKTYPE_RADIOTAP = 127;
    public static final int LINKTYPE_IEEE802_15_4_NOFCS = 230;
    public static final int LINKTYPE_LINUX_SLL2 = 276;

    public final int linkType;
    public final List<byte[]> packets;

    private PcapReader(int linkType, List<byte[]> packets) {
        this.linkType = linkType;
        this.packets = packets;
    }

    public static PcapReader parse(byte[] pcap) {
        if (pcap.length < 24) throw new IllegalArgumentException("pcap too short");
        boolean little;
        int magic0 = pcap[0] & 0xFF, magic1 = pcap[1] & 0xFF, magic2 = pcap[2] & 0xFF, magic3 = pcap[3] & 0xFF;
        if (magic0 == 0xD4 && magic1 == 0xC3 && magic2 == 0xB2 && magic3 == 0xA1) little = true;
        else if (magic0 == 0xA1 && magic1 == 0xB2 && magic2 == 0xC3 && magic3 == 0xD4) little = false;
        else throw new IllegalArgumentException("unsupported pcap magic");
        int link = read32(pcap, 20, little);
        int offset = 24;
        ArrayList<byte[]> packets = new ArrayList<>();
        while (offset + 16 <= pcap.length) {
            int inclLen = read32(pcap, offset + 8, little);
            offset += 16;
            if (inclLen < 0 || offset + inclLen > pcap.length) break;
            byte[] pkt = new byte[inclLen];
            System.arraycopy(pcap, offset, pkt, 0, inclLen);
            packets.add(pkt);
            offset += inclLen;
        }
        return new PcapReader(link, Collections.unmodifiableList(packets));
    }

    public List<PacketRecord> decodeRecords(String sourceId) {
        ArrayList<PacketRecord> records = new ArrayList<>();
        for (byte[] packet : packets) {
            try {
                if (linkType == LINKTYPE_ETHERNET && packet.length > 14) records.add(IpPacketDecoder.decodeIpHex(HexUtils.toHex(stripPrefix(packet, 14)), sourceId, "ethernet"));
                else if (linkType == LINKTYPE_RAW_IP) records.add(IpPacketDecoder.decodeIpHex(HexUtils.toHex(packet), sourceId, "raw-ip"));
                else if (linkType == LINKTYPE_IEEE802_15_4_NOFCS) records.add(Ieee802154FrameDecoder.decodeRecord(packet, sourceId, 11, 0, 0));
                else records.add(unknownRecord(packet, sourceId, "pcap-linktype-" + linkType));
            } catch (RuntimeException ex) { records.add(unknownRecord(packet, sourceId, "pcap-decode-error")); }
        }
        return records;
    }

    private static PacketRecord unknownRecord(byte[] packet, String sourceId, String protocol) {
        java.util.LinkedHashMap<String,String> f = new java.util.LinkedHashMap<>();
        f.put("bytes", Integer.toString(packet == null ? 0 : packet.length));
        return new PacketRecord(System.currentTimeMillis(), sourceId, 0, "pcap", 0, -1, "packet bytes", HexUtils.toHex(packet), "", ProtocolDecode.partial(protocol, "Unsupported PCAP link type", "Stored packet bytes for manual review", f), null);
    }

    public static byte[] stripPrefix(byte[] packet, int prefixBytes) {
        if (packet.length <= prefixBytes) return packet;
        byte[] out = new byte[packet.length - prefixBytes];
        System.arraycopy(packet, prefixBytes, out, 0, out.length);
        return out;
    }


    private static int read32(byte[] raw, int offset, boolean little) {
        if (little) return (raw[offset] & 0xFF) | ((raw[offset + 1] & 0xFF) << 8) | ((raw[offset + 2] & 0xFF) << 16) | ((raw[offset + 3] & 0xFF) << 24);
        return ((raw[offset] & 0xFF) << 24) | ((raw[offset + 1] & 0xFF) << 16) | ((raw[offset + 2] & 0xFF) << 8) | (raw[offset + 3] & 0xFF);
    }
}
