package org.deadscout.core;

public final class RtlSdrAudioDemodulator {
    public static final int DEFAULT_AUDIO_RATE_HZ = 12_000;

    private RtlSdrAudioDemodulator() { }

    public static short[] amEnvelopeToPcm16(byte[] unsignedIq, int iqSampleRateHz, int audioRateHz, int maxAudioSamples) {
        if (unsignedIq == null || unsignedIq.length < 4) return new short[0];
        int pairs = unsignedIq.length / 2;
        int stride = stride(iqSampleRateHz, audioRateHz);
        int count = Math.min(maxAudioSamples, Math.max(0, pairs / stride));
        if (count <= 0) return new short[0];
        double[] values = new double[count];
        double avg = 0.0;
        for (int n = 0; n < count; n++) {
            int idx = Math.min(pairs - 1, n * stride) * 2;
            double i = (unsignedIq[idx] & 0xFF) - 127.5;
            double q = (unsignedIq[idx + 1] & 0xFF) - 127.5;
            double mag = Math.sqrt(i * i + q * q);
            avg += (mag - avg) * 0.003;
            values[n] = mag - avg;
        }
        return normalize(values, count);
    }

    public static short[] fmToPcm16(byte[] unsignedIq, int iqSampleRateHz, int audioRateHz, int maxAudioSamples) {
        if (unsignedIq == null || unsignedIq.length < 8) return new short[0];
        int pairs = unsignedIq.length / 2;
        int stride = stride(iqSampleRateHz, audioRateHz);
        int count = Math.min(maxAudioSamples, Math.max(0, (pairs - stride - 1) / stride));
        if (count <= 0) return new short[0];
        double[] values = new double[count];
        double prevI = (unsignedIq[0] & 0xFF) - 127.5;
        double prevQ = (unsignedIq[1] & 0xFF) - 127.5;
        for (int n = 0; n < count; n++) {
            int startPair = Math.min(pairs - 1, n * stride + 1);
            double sum = 0.0;
            int used = 0;
            for (int s = 0; s < stride && startPair + s < pairs; s++) {
                int idx = (startPair + s) * 2;
                double i = (unsignedIq[idx] & 0xFF) - 127.5;
                double q = (unsignedIq[idx + 1] & 0xFF) - 127.5;
                double cross = prevI * q - prevQ * i;
                double dot = prevI * i + prevQ * q;
                sum += Math.atan2(cross, dot);
                prevI = i;
                prevQ = q;
                used++;
            }
            values[n] = used == 0 ? 0.0 : sum / used;
        }
        removeDc(values, count);
        return normalize(values, count);
    }

    private static int stride(int iqSampleRateHz, int audioRateHz) {
        int iqRate = Math.max(1, iqSampleRateHz);
        int audioRate = Math.max(1, audioRateHz);
        return Math.max(1, Math.round((float) iqRate / audioRate));
    }

    private static void removeDc(double[] values, int count) {
        double avg = 0.0;
        for (int i = 0; i < count; i++) avg += values[i];
        avg /= Math.max(1, count);
        for (int i = 0; i < count; i++) values[i] -= avg;
    }

    private static short[] normalize(double[] values, int count) {
        removeDc(values, count);
        double peak = 0.0;
        for (int i = 0; i < count; i++) peak = Math.max(peak, Math.abs(values[i]));
        if (peak < 1e-9) peak = 1.0;
        double scale = 26_000.0 / peak;
        short[] out = new short[count];
        for (int i = 0; i < count; i++) {
            int sample = (int) Math.round(values[i] * scale);
            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;
            out[i] = (short) sample;
        }
        return out;
    }
}
