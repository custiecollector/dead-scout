package org.deadscout.core;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class PcapWriter {
    public static final int LINKTYPE_RAW_IP = 101;
    public static final int LINKTYPE_IEEE802_15_4_NOFCS = 230;

    private PcapWriter() {}

    public static byte[] writeRawIp(List<byte[]> packets) {
        return writePackets(packets, LINKTYPE_RAW_IP);
    }

    public static byte[] writeIeee802154(List<byte[]> frames) {
        return writePackets(frames, LINKTYPE_IEEE802_15_4_NOFCS);
    }

    private static byte[] writePackets(List<byte[]> frames, int linkType) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write32(out, 0xA1B2C3D4); // little-endian writer yields d4 c3 b2 a1
        write16(out, 2);
        write16(out, 4);
        write32(out, 0);
        write32(out, 0);
        write32(out, 262144);
        write32(out, linkType);
        long now = System.currentTimeMillis();
        for (byte[] frame : frames) {
            int seconds = (int) (now / 1000L);
            int micros = (int) ((now % 1000L) * 1000L);
            write32(out, seconds);
            write32(out, micros);
            write32(out, frame.length);
            write32(out, frame.length);
            out.write(frame, 0, frame.length);
            now += 10;
        }
        return out.toByteArray();
    }

    private static void write16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void write32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }
}
