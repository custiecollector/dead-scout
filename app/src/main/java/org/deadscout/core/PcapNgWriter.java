package org.deadscout.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class PcapNgWriter {
    private PcapNgWriter() {}

    public static byte[] writeSingleInterface(int linkType, String interfaceName, List<byte[]> packets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeSectionHeader(out);
        writeInterface(out, linkType, interfaceName == null ? "deadscout0" : interfaceName);
        long ts = System.currentTimeMillis() * 1000L;
        if (packets != null) for (byte[] packet : packets) writeEnhancedPacket(out, 0, ts += 10_000, packet);
        return out.toByteArray();
    }

    private static void writeSectionHeader(ByteArrayOutputStream out) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write32(body, 0x1A2B3C4D);
        write16(body, 1);
        write16(body, 0);
        write32(body, -1);
        write32(body, -1);
        writeBlock(out, PcapNgReader.BLOCK_SECTION_HEADER, body.toByteArray());
    }

    private static void writeInterface(ByteArrayOutputStream out, int linkType, String name) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write16(body, linkType);
        write16(body, 0);
        write32(body, 262144);
        writeOption(body, 2, name);
        write16(body, 0);
        write16(body, 0);
        writeBlock(out, PcapNgReader.BLOCK_INTERFACE_DESCRIPTION, body.toByteArray());
    }

    private static void writeEnhancedPacket(ByteArrayOutputStream out, int iface, long timestampMicros, byte[] data) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write32(body, iface);
        write32(body, (int) (timestampMicros >>> 32));
        write32(body, (int) timestampMicros);
        write32(body, data.length);
        write32(body, data.length);
        body.write(data, 0, data.length);
        pad4(body, data.length);
        writeBlock(out, PcapNgReader.BLOCK_ENHANCED_PACKET, body.toByteArray());
    }

    private static void writeBlock(ByteArrayOutputStream out, int type, byte[] body) {
        int len = 12 + body.length;
        write32(out, type);
        write32(out, len);
        out.write(body, 0, body.length);
        write32(out, len);
    }

    private static void writeOption(ByteArrayOutputStream out, int code, String value) {
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        write16(out, code);
        write16(out, raw.length);
        out.write(raw, 0, raw.length);
        pad4(out, raw.length);
    }

    private static void pad4(ByteArrayOutputStream out, int len) { for (int i = len; (i & 3) != 0; i++) out.write(0); }
    private static void write16(ByteArrayOutputStream out, int value) { out.write(value & 0xFF); out.write((value >> 8) & 0xFF); }
    private static void write32(ByteArrayOutputStream out, int value) { out.write(value & 0xFF); out.write((value >> 8) & 0xFF); out.write((value >> 16) & 0xFF); out.write((value >> 24) & 0xFF); }
}
