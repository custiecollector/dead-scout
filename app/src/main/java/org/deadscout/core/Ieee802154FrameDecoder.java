package org.deadscout.core;

import java.util.Arrays;
import java.util.Locale;

public final class Ieee802154FrameDecoder {
    private Ieee802154FrameDecoder() {}

    public static Ieee802154Frame parse(byte[] raw) {
        if (raw == null || raw.length < 5) throw new IllegalArgumentException("802.15.4 frame too short");
        int fcf = HexUtils.le16(raw, 0);
        int frameType = fcf & 0x7;
        boolean security = (fcf & 0x0008) != 0;
        boolean ack = (fcf & 0x0020) != 0;
        boolean panCompression = (fcf & 0x0040) != 0;
        boolean seqSuppressed = (fcf & 0x0100) != 0;
        int destMode = (fcf >> 10) & 0x3;
        int srcMode = (fcf >> 14) & 0x3;
        int offset = 2;
        int seq = seqSuppressed ? -1 : raw[offset++] & 0xFF;
        int destPan = -1;
        String dest = "";
        if (destMode != 0) {
            destPan = HexUtils.le16(raw, offset); offset += 2;
            ReadAddress ra = readAddress(raw, offset, destMode); dest = ra.text; offset = ra.nextOffset;
        }
        int srcPan = -1;
        String src = "";
        if (srcMode != 0) {
            if (destMode == 0 || !panCompression) { srcPan = HexUtils.le16(raw, offset); offset += 2; }
            else srcPan = destPan;
            ReadAddress ra = readAddress(raw, offset, srcMode); src = ra.text; offset = ra.nextOffset;
        }
        int payloadEnd = raw.length;
        if (payloadEnd - offset >= 2) payloadEnd -= 2; // common sniffer captures include FCS
        byte[] payload = Arrays.copyOfRange(raw, Math.min(offset, raw.length), Math.max(Math.min(offset, raw.length), payloadEnd));
        return new Ieee802154Frame(raw.clone(), fcf, frameType, seq, security, ack, panCompression, destPan, dest, srcPan, src, payload);
    }

    public static PacketRecord decodeRecord(byte[] raw, String sourceId, int channel, int lqi, double rssiDbm) {
        Ieee802154Frame frame = parse(raw);
        ProtocolDecode.Status status = frame.securityEnabled ? ProtocolDecode.Status.ENCRYPTED : ProtocolDecode.Status.DECODED;
        ProtocolDecode decode = new ProtocolDecode("ieee802154", "IEEE 802.15.4 MAC", status, frame.summary(), frame.fields());
        return new PacketRecord(System.currentTimeMillis(), sourceId, FrequencyPlan.ieee802154ChannelToHz(channel),
                "802.15.4 ch " + channel, rssiDbm, lqi, "O-QPSK DSSS", HexUtils.toHex(raw), "", decode, null);
    }

    private static ReadAddress readAddress(byte[] raw, int offset, int mode) {
        if (mode == 2) {
            int shortAddr = HexUtils.le16(raw, offset);
            return new ReadAddress(String.format(Locale.US, "%04X", shortAddr), offset + 2);
        }
        if (mode == 3) {
            long ext = HexUtils.le64(raw, offset);
            return new ReadAddress(String.format(Locale.US, "%016X", ext), offset + 8);
        }
        return new ReadAddress("reserved", offset);
    }

    private static final class ReadAddress {
        final String text;
        final int nextOffset;
        ReadAddress(String text, int nextOffset) { this.text = text; this.nextOffset = nextOffset; }
    }
}
