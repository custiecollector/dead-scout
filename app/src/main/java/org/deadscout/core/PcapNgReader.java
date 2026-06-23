package org.deadscout.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class PcapNgReader {
    public static final int BLOCK_SECTION_HEADER = 0x0A0D0D0A;
    public static final int BLOCK_INTERFACE_DESCRIPTION = 0x00000001;
    public static final int BLOCK_SIMPLE_PACKET = 0x00000003;
    public static final int BLOCK_ENHANCED_PACKET = 0x00000006;

    public final List<InterfaceInfo> interfaces;
    public final List<PacketBlock> packets;
    public final boolean littleEndian;

    private PcapNgReader(boolean littleEndian, List<InterfaceInfo> interfaces, List<PacketBlock> packets) {
        this.littleEndian = littleEndian;
        this.interfaces = Collections.unmodifiableList(interfaces);
        this.packets = Collections.unmodifiableList(packets);
    }

    public static boolean looksLike(byte[] raw) {
        return raw != null && raw.length >= 12
                && (raw[0] & 0xFF) == 0x0A && (raw[1] & 0xFF) == 0x0D
                && (raw[2] & 0xFF) == 0x0D && (raw[3] & 0xFF) == 0x0A;
    }

    public static PcapNgReader parse(byte[] raw) {
        if (!looksLike(raw)) throw new IllegalArgumentException("pcapng section header missing");
        boolean little = true;
        ArrayList<InterfaceInfo> ifaces = new ArrayList<>();
        ArrayList<PacketBlock> packets = new ArrayList<>();
        int offset = 0;
        while (offset + 12 <= raw.length) {
            int blockType = read32(raw, offset, little);
            if (blockType != BLOCK_SECTION_HEADER && offset == 0) blockType = BLOCK_SECTION_HEADER;
            int blockLen = read32(raw, offset + 4, little);
            if (blockType == BLOCK_SECTION_HEADER) {
                int magicLittle = read32(raw, offset + 8, true);
                int magicBig = read32(raw, offset + 8, false);
                if (magicLittle == 0x1A2B3C4D) little = true;
                else if (magicBig == 0x1A2B3C4D) little = false;
                else throw new IllegalArgumentException("pcapng byte-order magic missing");
                blockLen = read32(raw, offset + 4, little);
            }
            if (blockLen < 12 || offset + blockLen > raw.length) break;
            int body = offset + 8;
            int bodyEnd = offset + blockLen - 4;
            if (blockType == BLOCK_INTERFACE_DESCRIPTION && body + 8 <= bodyEnd) {
                int linkType = read16(raw, body, little);
                int snapLen = read32(raw, body + 4, little);
                LinkedHashMap<Integer, String> options = readOptions(raw, body + 8, bodyEnd, little);
                ifaces.add(new InterfaceInfo(ifaces.size(), linkType, snapLen,
                        options.getOrDefault(2, "iface-" + ifaces.size()),
                        options.getOrDefault(1, ""), options));
            } else if (blockType == BLOCK_ENHANCED_PACKET && body + 20 <= bodyEnd) {
                int interfaceId = read32(raw, body, little);
                long tsHigh = read32(raw, body + 4, little) & 0xFFFFFFFFL;
                long tsLow = read32(raw, body + 8, little) & 0xFFFFFFFFL;
                int capLen = read32(raw, body + 12, little);
                int origLen = read32(raw, body + 16, little);
                int dataOffset = body + 20;
                if (capLen >= 0 && dataOffset + capLen <= bodyEnd) {
                    byte[] pkt = new byte[capLen];
                    System.arraycopy(raw, dataOffset, pkt, 0, capLen);
                    InterfaceInfo info = interfaceId >= 0 && interfaceId < ifaces.size()
                            ? ifaces.get(interfaceId) : InterfaceInfo.unknown(interfaceId);
                    packets.add(new PacketBlock(info, ((tsHigh << 32) | tsLow), origLen, pkt));
                }
            } else if (blockType == BLOCK_SIMPLE_PACKET && body + 4 <= bodyEnd) {
                int origLen = read32(raw, body, little);
                int capLen = Math.min(origLen, bodyEnd - (body + 4));
                if (capLen > 0) {
                    byte[] pkt = new byte[capLen];
                    System.arraycopy(raw, body + 4, pkt, 0, capLen);
                    InterfaceInfo info = ifaces.isEmpty() ? InterfaceInfo.unknown(0) : ifaces.get(0);
                    packets.add(new PacketBlock(info, 0L, origLen, pkt));
                }
            }
            offset += blockLen;
        }
        return new PcapNgReader(little, ifaces, packets);
    }

    public List<PacketRecord> decodeRecords(String sourceId) {
        ArrayList<PacketRecord> out = new ArrayList<>();
        for (PacketBlock packet : packets) {
            int link = packet.interfaceInfo == null ? -1 : packet.interfaceInfo.linkType;
            try {
                if (link == PcapReader.LINKTYPE_ETHERNET && packet.data.length > 14) out.add(IpPacketDecoder.decodeIpHex(HexUtils.toHex(PcapReader.stripPrefix(packet.data, 14)), sourceId, "ethernet"));
                else if (link == PcapReader.LINKTYPE_RAW_IP) out.add(IpPacketDecoder.decodeIpHex(HexUtils.toHex(packet.data), sourceId, "raw-ip"));
                else if (link == PcapReader.LINKTYPE_IEEE802_15_4_NOFCS) out.add(Ieee802154FrameDecoder.decodeRecord(packet.data, sourceId, 11, 0, 0));
                else {
                    java.util.LinkedHashMap<String,String> f = new java.util.LinkedHashMap<>(); f.put("bytes", Integer.toString(packet.data.length)); f.put("linktype", Integer.toString(link));
                    out.add(new PacketRecord(System.currentTimeMillis(), sourceId, 0, "pcapng", 0, -1, "packet bytes", HexUtils.toHex(packet.data), "", ProtocolDecode.partial("pcapng-linktype-" + link, "Unsupported PCAPNG link type", "Stored packet bytes for manual review", f), null));
                }
            } catch (RuntimeException ignored) { }
        }
        return out;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("PCAPNG: ").append(interfaces.size()).append(" interfaces · ").append(packets.size()).append(" packet blocks");
        for (InterfaceInfo i : interfaces) {
            sb.append('\n').append(i.index).append(": linktype ").append(i.linkType)
                    .append(" snaplen ").append(i.snapLen).append(" name ").append(i.name);
            if (!i.comment.isEmpty()) sb.append(" · ").append(i.comment);
        }
        return sb.toString();
    }

    public static byte[] stripRadiotap(byte[] packet) {
        if (packet.length >= 4 && (packet[0] & 0xFF) == 0) {
            int len = (packet[2] & 0xFF) | ((packet[3] & 0xFF) << 8);
            if (len >= 4 && len < packet.length) {
                byte[] out = new byte[packet.length - len];
                System.arraycopy(packet, len, out, 0, out.length);
                return out;
            }
        }
        return packet;
    }

    private static LinkedHashMap<Integer, String> readOptions(byte[] raw, int offset, int end, boolean little) {
        LinkedHashMap<Integer, String> out = new LinkedHashMap<>();
        while (offset + 4 <= end) {
            int code = read16(raw, offset, little);
            int len = read16(raw, offset + 2, little);
            offset += 4;
            if (code == 0) break;
            if (len < 0 || offset + len > end) break;
            out.put(code, new String(raw, offset, len, StandardCharsets.UTF_8));
            offset += padded4(len);
        }
        return out;
    }

    private static int padded4(int len) { return (len + 3) & ~3; }

    private static int read16(byte[] raw, int offset, boolean little) {
        if (little) return (raw[offset] & 0xFF) | ((raw[offset + 1] & 0xFF) << 8);
        return ((raw[offset] & 0xFF) << 8) | (raw[offset + 1] & 0xFF);
    }

    private static int read32(byte[] raw, int offset, boolean little) {
        if (offset + 3 >= raw.length) return 0;
        if (little) return (raw[offset] & 0xFF) | ((raw[offset + 1] & 0xFF) << 8) | ((raw[offset + 2] & 0xFF) << 16) | ((raw[offset + 3] & 0xFF) << 24);
        return ((raw[offset] & 0xFF) << 24) | ((raw[offset + 1] & 0xFF) << 16) | ((raw[offset + 2] & 0xFF) << 8) | (raw[offset + 3] & 0xFF);
    }

    public static final class InterfaceInfo {
        public final int index;
        public final int linkType;
        public final int snapLen;
        public final String name;
        public final String comment;
        public final LinkedHashMap<Integer, String> options;

        InterfaceInfo(int index, int linkType, int snapLen, String name, String comment, LinkedHashMap<Integer, String> options) {
            this.index = index;
            this.linkType = linkType;
            this.snapLen = snapLen;
            this.name = name == null ? "" : name;
            this.comment = comment == null ? "" : comment;
            this.options = new LinkedHashMap<>(options);
        }

        static InterfaceInfo unknown(int index) {
            return new InterfaceInfo(index, 0, 0, String.format(Locale.US, "iface-%d", index), "unknown interface", new LinkedHashMap<Integer, String>());
        }
    }

    public static final class PacketBlock {
        public final InterfaceInfo interfaceInfo;
        public final long timestampTicks;
        public final int originalLength;
        public final byte[] data;

        PacketBlock(InterfaceInfo interfaceInfo, long timestampTicks, int originalLength, byte[] data) {
            this.interfaceInfo = interfaceInfo;
            this.timestampTicks = timestampTicks;
            this.originalLength = originalLength;
            this.data = data;
        }
    }
}
