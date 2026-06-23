package org.deadscout.core;

import java.util.Locale;

/**
 * Operator-facing hex inspector used by the Android Lab screen.
 *
 * This is deliberately conservative: it explains when bytes look like raw IQ data
 * instead of pretending every hex blob is a decoded RF/network packet.
 */
public final class HexDecodeLab {
    private HexDecodeLab() {}

    public static String analyze(String hex) {
        String cleaned = hex == null ? "" : hex.replaceAll("[^0-9A-Fa-f]", "");
        if (cleaned.isEmpty()) {
            return "Paste hex from a packet, imported PCAP frame, 802.15.4 sniffer frame, or RTL IQ preview. DeadScout will identify what it can and call out raw IQ when it is not a traffic packet.";
        }
        if ((cleaned.length() & 1) != 0) {
            return "Hex has an odd number of digits. Add the missing nibaux or remove separators/noise, then decode again.";
        }

        byte[] raw;
        try {
            raw = HexUtils.fromHex(cleaned);
        } catch (RuntimeException ex) {
            return "Could not parse hex: " + ex.getMessage();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Bytes: ").append(raw.length)
                .append(" · Entropy: ")
                .append(String.format(Locale.US, "%.2f", HexInspector.entropyBitsPerByte(cleaned)))
                .append(" bits/byte\n");
        sb.append("ASCII: ").append(shorten(HexUtils.ascii(raw), 160)).append("\n\n");

        boolean decoded = false;
        decoded |= tryIp(cleaned, sb);
        decoded |= tryIeee802154(raw, sb);

        if (!decoded) {
            sb.append("No confident protocol packet decode.\n");
            if (looksLikeIq(raw)) {
                sb.append("This looks consistent with unsigned interleaved raw IQ data: I/Q byte pairs, not a framed traffic packet. Use Signal/Monitor/Waterfall tools first; packet decoders need demodulated/framed bytes.\n");
                sb.append("\nSignal/IQ stats: ").append(iqStats(raw)).append('\n');
            } else {
                sb.append("This may be encrypted, truncated, an unknown PHY frame, or framed bytes for a decoder DeadScout does not support yet. Use the hexdump and source context to choose a decoder path.\n");
            }
        }

        sb.append("\nHexdump:\n").append(HexInspector.hexdump(cleaned, 128));
        return sb.toString().trim();
    }

    private static boolean tryIp(String hex, StringBuilder sb) {
        try {
            PacketRecord p = IpPacketDecoder.decodeIpHex(hex, "lab-hex", "Android packet capture/import");
            appendPacket(sb, "IP packet", p);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean tryIeee802154(byte[] raw, StringBuilder sb) {
        try {
            PacketRecord p = Ieee802154FrameDecoder.decodeRecord(raw, "lab-hex", 15, -1, 0.0);
            String frameType = p.decode.fields.getOrDefault("frame_type", "Reserved");
            boolean hasAddress = !p.decode.fields.getOrDefault("dest", "").isEmpty()
                    || !p.decode.fields.getOrDefault("source", "").isEmpty();
            if (!"Reserved".equals(frameType) && hasAddress) {
                appendPacket(sb, "802.15.4 MAC frame", p);
                return true;
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    private static void appendPacket(StringBuilder sb, String label, PacketRecord p) {
        sb.append(label).append(": ").append(p.decode.protocol).append(" · ").append(p.decode.summary).append('\n');
        int shown = 0;
        for (String key : p.decode.fields.keySet()) {
            if (shown++ >= 6) break;
            sb.append("  ").append(key).append(": ").append(shorten(p.decode.fields.get(key), 96)).append('\n');
        }
        sb.append('\n');
    }

    private static boolean looksLikeIq(byte[] raw) {
        if (raw.length < 32 || (raw.length & 1) != 0) return false;
        int min = 255, max = 0;
        long sumI = 0, sumQ = 0;
        for (int i = 0; i < raw.length; i++) {
            int v = raw[i] & 0xFF;
            if (v < min) min = v;
            if (v > max) max = v;
            if ((i & 1) == 0) sumI += v; else sumQ += v;
        }
        double meanI = sumI / Math.max(1.0, raw.length / 2.0);
        double meanQ = sumQ / Math.max(1.0, raw.length / 2.0);
        return max - min > 16 && meanI > 20 && meanI < 235 && meanQ > 20 && meanQ < 235;
    }

    private static String iqStats(byte[] raw) {
        int pairs = raw.length / 2;
        int minI = 255, maxI = 0, minQ = 255, maxQ = 0;
        long sumI = 0, sumQ = 0;
        for (int i = 0; i + 1 < raw.length; i += 2) {
            int iv = raw[i] & 0xFF;
            int qv = raw[i + 1] & 0xFF;
            minI = Math.min(minI, iv); maxI = Math.max(maxI, iv); sumI += iv;
            minQ = Math.min(minQ, qv); maxQ = Math.max(maxQ, qv); sumQ += qv;
        }
        if (pairs == 0) return "not enough bytes";
        return String.format(Locale.US, "%d I/Q pairs · I avg %.1f range %d-%d · Q avg %.1f range %d-%d",
                pairs, sumI / (double) pairs, minI, maxI, sumQ / (double) pairs, minQ, maxQ);
    }

    private static String shorten(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
