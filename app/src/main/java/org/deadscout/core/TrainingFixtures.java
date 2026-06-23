package org.deadscout.core;

public final class TrainingFixtures {
    private TrainingFixtures() {}
    public static CaptureSession packetTrainingSession() { return sampleSession(); }
    public static CaptureSession sampleSession() {
        long now = System.currentTimeMillis();
        CaptureSession session = new CaptureSession("deadscout-training", now);
        session.addNote("Synthetic local training sample for SDR/import packet review.");
        session.addPacket(Rtl433JsonDecoder.decodeLine("{\"model\":\"Generic sensor\",\"id\":42,\"temperature_C\":21.5}", "training-rtl433"));
        session.addPacket(Ieee802154FrameDecoder.decodeRecord(HexUtils.fromHex("4188013412ffff7856341201020304"), "training-802154", 15, 128, -51));
        session.finish(now + 1000);
        return session;
    }
}
