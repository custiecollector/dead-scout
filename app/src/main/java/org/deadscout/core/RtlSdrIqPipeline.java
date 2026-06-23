package org.deadscout.core;

import java.util.ArrayList;
import java.util.Locale;

public final class RtlSdrIqPipeline {
    public final Config config;
    private SpectrumSnapshot peakHold;

    public RtlSdrIqPipeline(Config config) {
        this.config = config == null ? Config.default915() : config;
        this.config.validate();
    }

    public SpectrumSnapshot ingestUnsignedIq(byte[] interleavedIq) {
        if (interleavedIq == null || interleavedIq.length < 8) throw new IllegalArgumentException("need interleaved unsigned IQ bytes");
        int bytesPerWindow = Math.max(16, config.fftSize * 2);
        if (interleavedIq.length <= bytesPerWindow) {
            SpectrumSnapshot now = SpectrumAnalyzer.analyzeUnsignedIq(interleavedIq, config.frequencyHz, config.sampleRateHz, config.fftSize);
            peakHold = SpectrumAnalyzer.mergePeakHold(peakHold, now);
            return peakHold;
        }
        int windows = Math.min(32, Math.max(1, interleavedIq.length / bytesPerWindow));
        int stride = Math.max(bytesPerWindow, (interleavedIq.length - bytesPerWindow) / Math.max(1, windows - 1));
        for (int w = 0; w < windows; w++) {
            int start = Math.min(interleavedIq.length - bytesPerWindow, w * stride);
            byte[] slice = new byte[bytesPerWindow];
            System.arraycopy(interleavedIq, start, slice, 0, bytesPerWindow);
            SpectrumSnapshot now = SpectrumAnalyzer.analyzeUnsignedIq(slice, config.frequencyHz, config.sampleRateHz, config.fftSize);
            peakHold = SpectrumAnalyzer.mergePeakHold(peakHold, now);
        }
        return peakHold;
    }

    public ArrayList<SignalObservation> observationsFrom(SpectrumSnapshot snapshot) {
        return observationsFrom(snapshot, "rtl-sdr-usb");
    }

    public ArrayList<SignalObservation> observationsFrom(SpectrumSnapshot snapshot, String sourceId) {
        ArrayList<SignalObservation> out = new ArrayList<>();
        long t = System.currentTimeMillis();
        String normalizedSourceId = (sourceId == null || sourceId.trim().isEmpty()) ? "rtl-sdr-iq" : sourceId.trim();
        for (SpectrumSnapshot.SignalMarker marker : snapshot.markers) {
            out.add(new SignalObservation(t, normalizedSourceId, marker.frequencyHz, config.sampleRateHz, config.sampleRateHz,
                    marker.powerDb, marker.aboveNoiseDb, marker.aboveNoiseDb > 20 ? "strong narrowband/OOK-FSK candidate" : "RF energy peak",
                    "RTL-SDR IQ FFT marker; route to waterfall, burst detector, rtl_433, ADS-B, or unknown signal lab."));
        }
        return out;
    }

    public String controlPlan() {
        return "USB open RTL2832U bulk endpoint → set tuner frequency " + config.frequencyHz + " Hz → sample rate " + config.sampleRateHz
                + " Hz → gain " + (config.agc ? "AGC" : config.gainTenthsDb / 10.0 + " dB")
                + " → ppm " + config.ppmCorrection + " → read unsigned interleaved IQ → FFT/waterfall/burst detector/SigMF recorder";
    }

    public String sigMfMetaJson(int sampleCount, String dataFileName) {
        return "{\"global\":{\"core:datatype\":\"cu8\",\"core:sample_rate\":" + config.sampleRateHz
                + ",\"core:hw\":\"RTL-SDR USB\",\"deadscout:ppm\":" + config.ppmCorrection
                + ",\"deadscout:gain_db\":\"" + (config.agc ? "agc" : String.format(Locale.US, "%.1f", config.gainTenthsDb / 10.0)) + "\"},"
                + "\"captures\":[{\"core:sample_start\":0,\"core:frequency\":" + config.frequencyHz + "}],"
                + "\"annotations\":[{\"core:sample_start\":0,\"core:sample_count\":" + sampleCount + ",\"deadscout:source\":\"rtl-sdr-usb\"}],"
                + "\"deadscout:data_file\":\"" + HexUtils.jsonEscape(dataFileName == null ? "capture.cu8" : dataFileName) + "\"}";
    }

    public static final class Config {
        public final long frequencyHz;
        public final int sampleRateHz;
        public final int gainTenthsDb;
        public final int ppmCorrection;
        public final boolean agc;
        public final int fftSize;
        public final double squelchDbAboveNoise;

        public Config(long frequencyHz, int sampleRateHz, int gainTenthsDb, int ppmCorrection, boolean agc, int fftSize, double squelchDbAboveNoise) {
            this.frequencyHz = frequencyHz;
            this.sampleRateHz = sampleRateHz;
            this.gainTenthsDb = gainTenthsDb;
            this.ppmCorrection = ppmCorrection;
            this.agc = agc;
            this.fftSize = fftSize;
            this.squelchDbAboveNoise = squelchDbAboveNoise;
        }

        public static Config default915() { return new Config(915_000_000L, 2_400_000, 280, 0, false, 64, 12.0); }

        public void validate() {
            if (frequencyHz < 24_000_000L || frequencyHz > 1_766_000_000L) throw new IllegalArgumentException("RTL-SDR frequency outside practical tuner range");
            if (sampleRateHz < 225_001 || sampleRateHz > 3_200_000) throw new IllegalArgumentException("RTL-SDR sample rate outside common stable range");
            if (fftSize < 8 || fftSize > 4096) throw new IllegalArgumentException("FFT size out of range");
        }
    }
}
