package org.deadscout.core;

import java.util.ArrayList;
import java.util.List;

public final class DeadScoutExport {
    private DeadScoutExport() {}

    public static String sessionBundle(CaptureSession session) {
        return session.toJson();
    }

    public static byte[] ieee802154PcapFromPackets(List<PacketRecord> packets) {
        ArrayList<byte[]> frames = new ArrayList<>();
        for (PacketRecord packet : packets) {
            if (packet.decode.module.equals("ieee802154") && !packet.rawHex.isEmpty()) frames.add(HexUtils.fromHex(packet.rawHex));
        }
        return PcapWriter.writeIeee802154(frames);
    }
}
