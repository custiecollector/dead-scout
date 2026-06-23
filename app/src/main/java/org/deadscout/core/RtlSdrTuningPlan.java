package org.deadscout.core;

import java.util.Locale;

public final class RtlSdrTuningPlan {
    public static final int RTL_XTAL_HZ = 28_800_000;
    public static final int R82XX_IF_HZ = 3_570_000;
    public static final int DEFAULT_AUDIO_DISCARD_BYTES = 4096;

    private static final int[] R82XX_GAINS_TENTH_DB = {
            0, 9, 14, 27, 37, 77, 87, 125, 144, 157, 166, 197, 207, 229, 254,
            280, 297, 328, 338, 364, 372, 386, 402, 421, 434, 439, 445, 480, 496
    };

    private RtlSdrTuningPlan() { }

    public static SampleRatePlan sampleRate(int requestedHz) {
        if ((requestedHz <= 225_000) || (requestedHz > 3_200_000) ||
                ((requestedHz > 300_000) && (requestedHz <= 900_000))) {
            throw new IllegalArgumentException("RTL-SDR sample rate outside common stable range");
        }
        long ratio = (long) ((RTL_XTAL_HZ * Math.pow(2.0, 22.0)) / requestedHz);
        ratio &= 0x0ffffffcL;
        long realRatio = ratio | ((ratio & 0x08000000L) << 1);
        double exact = (RTL_XTAL_HZ * Math.pow(2.0, 22.0)) / realRatio;
        int high = (int) ((ratio >> 16) & 0xffff);
        int low = (int) (ratio & 0xffff);
        return new SampleRatePlan(requestedHz, (int) Math.round(exact), ratio, high, low);
    }

    public static IfPlan ifFrequency(int ifHz) {
        int ifReg = (int) (((ifHz * Math.pow(2.0, 22.0)) / RTL_XTAL_HZ) * -1.0);
        return new IfPlan(ifHz, (ifReg >> 16) & 0x3f, (ifReg >> 8) & 0xff, ifReg & 0xff);
    }

    public static PllPlan r82xxPll(long centerFrequencyHz) {
        long loHz = centerFrequencyHz + R82XX_IF_HZ;
        long pllRef = RTL_XTAL_HZ;
        long pllRef2x = pllRef * 2L;
        long vcoMin = 1_770_000_000L;
        long vcoMax = 3_900_000_000L;
        int divNum;
        long vcoExact = 0;
        for (divNum = 0; divNum < 5; divNum++) {
            vcoExact = loHz << (divNum + 1);
            if (vcoExact >= vcoMin && vcoExact <= vcoMax) break;
        }
        if (divNum >= 5) divNum = 4;
        vcoExact = loHz << (divNum + 1);
        int nintRaw = (int) ((vcoExact + (pllRef >> 16)) / pllRef2x);
        long vcoFrac = vcoExact - pllRef2x * nintRaw;
        int nint = nintRaw - 13;
        int ni = nint >> 2;
        int si = nint - (ni << 2);
        int sdm = 0;
        boolean fractional = vcoFrac != 0;
        if (fractional) {
            vcoFrac += pllRef >> 16;
            for (int n = 0; n < 16; n++) {
                long conFrac = pllRef >> n;
                if (vcoFrac >= conFrac) {
                    sdm |= (0x8000 >> n);
                    vcoFrac -= conFrac;
                    if (vcoFrac == 0) break;
                }
            }
        }
        int phaseSplitter = (divNum << 5) & 0xe0;
        int roughVco = (ni + (si << 6)) & 0xff;
        long actualLo = (((long) nintRaw << 16) + sdm) * pllRef2x;
        actualLo >>= (divNum + 1 + 16);
        return new PllPlan(centerFrequencyHz, loHz, actualLo, divNum, phaseSplitter, roughVco, sdm, fractional);
    }

    public static int nearestR82xxGain(int requestedTenthsDb) {
        int best = R82XX_GAINS_TENTH_DB[0];
        int bestDiff = Math.abs(best - requestedTenthsDb);
        for (int gain : R82XX_GAINS_TENTH_DB) {
            int diff = Math.abs(gain - requestedTenthsDb);
            if (diff < bestDiff) {
                best = gain;
                bestDiff = diff;
            }
        }
        return best;
    }

    public static int[] r82xxGainIndexes(int gainTenthsDb) {
        int gain = Math.max(0, nearestR82xxGain(gainTenthsDb));
        int[] lnaSteps = {0, 9, 13, 40, 38, 13, 31, 22, 26, 31, 26, 14, 19, 5, 35, 13};
        int[] mixerSteps = {0, 5, 10, 10, 19, 9, 10, 25, 17, 10, 8, 16, 13, 6, 3, -8};
        int total = 0, lna = 0, mixer = 0;
        for (int i = 0; i < 15; i++) {
            if (total >= gain) break;
            total += lnaSteps[++lna];
            if (total >= gain) break;
            total += mixerSteps[++mixer];
        }
        return new int[]{Math.max(0, Math.min(15, lna)), Math.max(0, Math.min(15, mixer))};
    }

    public static String describe(long centerHz, int sampleRateHz, int gainTenthsDb, boolean agc) {
        SampleRatePlan sr = sampleRate(sampleRateHz);
        PllPlan pll = r82xxPll(centerHz);
        return String.format(Locale.US,
                "RTL2832U/R820T plan %.3f MHz center, LO %.3f MHz, exact %.0f S/s, %s, PLL div %d rough 0x%02X sdm 0x%04X",
                centerHz / 1_000_000.0,
                pll.loFrequencyHz / 1_000_000.0,
                (double) sr.exactSampleRateHz,
                agc ? "AGC" : String.format(Locale.US, "gain %.1f dB", gainTenthsDb / 10.0),
                pll.divNum,
                pll.roughVcoReg,
                pll.sdm);
    }

    public static final class SampleRatePlan {
        public final int requestedSampleRateHz;
        public final int exactSampleRateHz;
        public final long ratio;
        public final int ratioHighReg;
        public final int ratioLowReg;

        public SampleRatePlan(int requestedSampleRateHz, int exactSampleRateHz, long ratio, int ratioHighReg, int ratioLowReg) {
            this.requestedSampleRateHz = requestedSampleRateHz;
            this.exactSampleRateHz = exactSampleRateHz;
            this.ratio = ratio;
            this.ratioHighReg = ratioHighReg;
            this.ratioLowReg = ratioLowReg;
        }
    }

    public static final class IfPlan {
        public final int ifHz;
        public final int reg19;
        public final int reg1a;
        public final int reg1b;

        public IfPlan(int ifHz, int reg19, int reg1a, int reg1b) {
            this.ifHz = ifHz;
            this.reg19 = reg19;
            this.reg1a = reg1a;
            this.reg1b = reg1b;
        }
    }

    public static final class PllPlan {
        public final long centerFrequencyHz;
        public final long loFrequencyHz;
        public final long actualLoFrequencyHz;
        public final int divNum;
        public final int phaseSplitterReg;
        public final int roughVcoReg;
        public final int sdm;
        public final boolean fractional;

        public PllPlan(long centerFrequencyHz, long loFrequencyHz, long actualLoFrequencyHz, int divNum, int phaseSplitterReg, int roughVcoReg, int sdm, boolean fractional) {
            this.centerFrequencyHz = centerFrequencyHz;
            this.loFrequencyHz = loFrequencyHz;
            this.actualLoFrequencyHz = actualLoFrequencyHz;
            this.divNum = divNum;
            this.phaseSplitterReg = phaseSplitterReg;
            this.roughVcoReg = roughVcoReg;
            this.sdm = sdm;
            this.fractional = fractional;
        }
    }
}
