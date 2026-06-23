package org.deadscout.app;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDeviceConnection;

import org.deadscout.core.RtlSdrIqPipeline;
import org.deadscout.core.RtlSdrTuningPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-app RTL2832U/R820T control path for Android USB Host.
 *
 * This follows the same public RTL2832U vendor-control/I2C register model used by librtlsdr:
 * initialize RTL2832U baseband, identify the Rafael tuner, program R82xx registers,
 * configure sample-rate/IF, tune the PLL, set gain mode, reset USB FIFO, then read bulk IQ.
 */
public final class AndroidRtlSdrController {
    private static final int CTRL_IN = UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_IN;
    private static final int CTRL_OUT = UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_OUT;
    private static final int TIMEOUT_MS = 300;

    private static final int DEMODB = 0;
    private static final int USBB = 1;
    private static final int SYSB = 2;
    private static final int IRB = 5;
    private static final int IICB = 6;

    private static final int USB_SYSCTL = 0x2000;
    private static final int USB_EPA_CTL = 0x2148;
    private static final int USB_EPA_MAXPKT = 0x2158;
    private static final int DEMOD_CTL = 0x3000;
    private static final int DEMOD_CTL_1 = 0x300b;

    private static final int R820T_I2C_ADDR = 0x34;
    private static final int R828D_I2C_ADDR = 0x74;
    private static final int R82XX_CHECK_ADDR = 0x00;
    private static final int R82XX_CHECK_VAL = 0x69;
    private static final int REG_SHADOW_START = 5;

    private static final byte[] R82XX_INIT = new byte[]{
            (byte) 0x80, 0x13, 0x70, (byte) 0xc0, 0x40, (byte) 0xdb, 0x6b,
            (byte) 0xeb, 0x53, 0x75, 0x68, 0x6c, (byte) 0xbb, (byte) 0x80,
            0x31, 0x0f, 0x00, (byte) 0xc0, 0x30, 0x48, (byte) 0xec, 0x60,
            0x00, 0x24, (byte) 0xdd, 0x0e, 0x40
    };

    private static final int[] FIR_DEFAULT = {
            -54, -36, -41, -40, -32, -14, 14, 53,
            101, 156, 215, 273, 327, 372, 404, 421
    };

    private final UsbDeviceConnection connection;
    private final List<String> steps = new ArrayList<>();
    private final byte[] r82xxRegs = new byte[32];
    private int tunerAddr = R820T_I2C_ADDR;
    private String tunerName = "unknown";

    private AndroidRtlSdrController(UsbDeviceConnection connection) {
        this.connection = connection;
    }

    public static ControlReport configure(UsbDeviceConnection connection, RtlSdrIqPipeline.Config config) {
        AndroidRtlSdrController ctl = new AndroidRtlSdrController(connection);
        return ctl.configure(config);
    }

    private ControlReport configure(RtlSdrIqPipeline.Config config) {
        int failures = 0;
        try {
            failures += initBaseband();
            boolean tunerFound = probeTuner();
            if (tunerFound) {
                failures += initR82xx();
                failures += setR82xxGain(config);
                failures += setIfModeAndSampleRate(config);
                failures += tuneR82xx(config.frequencyHz);
            } else {
                steps.add("R820T/R828D tuner probe failed; bulk read would not be reliably tuned.");
                failures++;
            }
            failures += resetBuffer();
            i2cRepeater(false);
        } catch (RuntimeException ex) {
            steps.add("control exception: " + ex.getClass().getSimpleName() + " " + ex.getMessage());
            failures++;
        }
        boolean ok = failures == 0;
        String plan = RtlSdrTuningPlan.describe(config.frequencyHz, config.sampleRateHz, config.gainTenthsDb, config.agc);
        String status = (ok ? "RTL2832U/R82xx control layer applied. " : "RTL2832U/R82xx control layer had " + failures + " issue(s). ") + plan;
        return new ControlReport(ok, failures, tunerName, status, compactSteps());
    }

    private int initBaseband() {
        int f = 0;
        f += require("USB SYSCTL", writeReg(USBB, USB_SYSCTL, 0x09, 1));
        f += require("USB EPA max packet", writeReg(USBB, USB_EPA_MAXPKT, 0x0002, 2));
        f += require("USB EPA reset/assert", writeReg(USBB, USB_EPA_CTL, 0x1002, 2));
        f += require("power demod ctl1", writeReg(SYSB, DEMOD_CTL_1, 0x22, 1));
        f += require("power demod ctl", writeReg(SYSB, DEMOD_CTL, 0xe8, 1));
        f += require("demod soft reset assert", demodWrite(1, 0x01, 0x14, 1));
        f += require("demod soft reset release", demodWrite(1, 0x01, 0x10, 1));
        f += require("disaaux spectrum inversion", demodWrite(1, 0x15, 0x00, 1));
        f += require("disaaux adjacent rejection", demodWrite(1, 0x16, 0x0000, 2));
        for (int i = 0; i < 6; i++) f += require("clear DDC/IF reg " + i, demodWrite(1, 0x16 + i, 0x00, 1));
        f += setFir();
        f += require("enable SDR mode", demodWrite(0, 0x19, 0x05, 1));
        f += require("FSM state hold high", demodWrite(1, 0x93, 0xf0, 1));
        f += require("FSM state hold low", demodWrite(1, 0x94, 0x0f, 1));
        f += require("disaaux demod AGC", demodWrite(1, 0x11, 0x00, 1));
        f += require("disaaux PID filter", demodWrite(0, 0x61, 0x60, 1));
        f += require("ADC IQ datapath", demodWrite(0, 0x06, 0x80, 1));
        f += require("zero-IF/DC/IQ compensation", demodWrite(1, 0xb1, 0x1b, 1));
        f += require("disaaux TP clock", demodWrite(0, 0x0d, 0x83, 1));
        return f;
    }

    private int setFir() {
        int[] fir = new int[20];
        for (int i = 0; i < 8; i++) fir[i] = FIR_DEFAULT[i] & 0xff;
        for (int i = 0; i < 8; i += 2) {
            int val0 = FIR_DEFAULT[8 + i];
            int val1 = FIR_DEFAULT[8 + i + 1];
            fir[8 + i * 3 / 2] = (val0 >> 4) & 0xff;
            fir[8 + i * 3 / 2 + 1] = ((val0 << 4) | ((val1 >> 8) & 0x0f)) & 0xff;
            fir[8 + i * 3 / 2 + 2] = val1 & 0xff;
        }
        int f = 0;
        for (int i = 0; i < fir.length; i++) f += require("FIR " + i, demodWrite(1, 0x1c + i, fir[i], 1));
        return f;
    }

    private boolean probeTuner() {
        i2cRepeater(true);
        int r820 = i2cReadReg(R820T_I2C_ADDR, R82XX_CHECK_ADDR);
        if (r820 == R82XX_CHECK_VAL) {
            tunerAddr = R820T_I2C_ADDR;
            tunerName = "R820T/R820T2";
            steps.add("Detected Rafael " + tunerName + " tuner at I2C 0x34.");
            return true;
        }
        int r828 = i2cReadReg(R828D_I2C_ADDR, R82XX_CHECK_ADDR);
        if (r828 == R82XX_CHECK_VAL) {
            tunerAddr = R828D_I2C_ADDR;
            tunerName = "R828D/R860";
            steps.add("Detected Rafael " + tunerName + " tuner at I2C 0x74.");
            return true;
        }
        steps.add(String.format(Locale.US, "Rafael tuner not identified: R820T check 0x%02X, R828D check 0x%02X.", r820, r828));
        return false;
    }

    private int initR82xx() {
        System.arraycopy(R82XX_INIT, 0, r82xxRegs, REG_SHADOW_START, R82XX_INIT.length);
        int f = require("R82xx init register block", r82xxWriteArr(0x05, R82XX_INIT));
        // Digital-TV IF defaults good enough for SDR zero-IF path; do not run destructive calibration loops on every read.
        return f;
    }

    private int setIfModeAndSampleRate(RtlSdrIqPipeline.Config config) {
        int f = 0;
        // R82xx IF path: disaaux zero-IF, use I ADC input, set 3.57 MHz IF, enable RTL spectrum inversion.
        f += require("disaaux zero-IF for tuner IF", demodWrite(1, 0xb1, 0x1a, 1));
        f += require("enable I ADC input", demodWrite(0, 0x08, 0x4d, 1));
        RtlSdrTuningPlan.IfPlan ifPlan = RtlSdrTuningPlan.ifFrequency(RtlSdrTuningPlan.R82XX_IF_HZ);
        f += require("IF reg 0x19", demodWrite(1, 0x19, ifPlan.reg19, 1));
        f += require("IF reg 0x1a", demodWrite(1, 0x1a, ifPlan.reg1a, 1));
        f += require("IF reg 0x1b", demodWrite(1, 0x1b, ifPlan.reg1b, 1));
        f += require("enable spectrum inversion for R82xx", demodWrite(1, 0x15, 0x01, 1));
        f += require("ADC IQ datapath", demodWrite(0, 0x06, 0x80, 1));

        RtlSdrTuningPlan.SampleRatePlan sr = RtlSdrTuningPlan.sampleRate(config.sampleRateHz);
        f += require("sample-rate ratio high", demodWrite(1, 0x9f, sr.ratioHighReg, 2));
        f += require("sample-rate ratio low", demodWrite(1, 0xa1, sr.ratioLowReg, 2));
        f += require("sample-rate demod reset assert", demodWrite(1, 0x01, 0x14, 1));
        f += require("sample-rate demod reset release", demodWrite(1, 0x01, 0x10, 1));
        steps.add(String.format(Locale.US, "Sample-rate requested %d Hz; exact RTL rate approx %d Hz.", config.sampleRateHz, sr.exactSampleRateHz));
        return f;
    }

    private int tuneR82xx(long centerFrequencyHz) {
        int f = 0;
        R82xxRange range = rangeFor(centerFrequencyHz + RtlSdrTuningPlan.R82XX_IF_HZ);
        f += r82xxWriteRegMask(0x17, range.openD, 0x08, "open-drain/filter range");
        f += r82xxWriteRegMask(0x1a, range.rfMux, 0xc3, "RF mux/polyphase");
        f += require("tracking filter", r82xxWriteReg(0x1b, range.tfC));
        f += r82xxWriteRegMask(0x10, range.xtal0p, 0x0b, "XTAL cap/drive");

        RtlSdrTuningPlan.PllPlan pll = RtlSdrTuningPlan.r82xxPll(centerFrequencyHz);
        f += r82xxWriteRegMask(0x10, pll.phaseSplitterReg, 0xe0, "PLL phase splitter");
        f += r82xxWriteRegMask(0x12, 0x00, 0x18, "PLL dither/default current");
        f += require("PLL rough VCO", r82xxWriteReg(0x14, pll.roughVcoReg));
        if (pll.fractional) {
            f += require("PLL SDM low", r82xxWriteReg(0x15, pll.sdm & 0xff));
            f += require("PLL SDM high", r82xxWriteReg(0x16, (pll.sdm >> 8) & 0xff));
            f += r82xxWriteRegMask(0x12, 0x00, 0x08, "enable fractional PLL");
        } else {
            f += r82xxWriteRegMask(0x12, 0x08, 0x08, "disaaux fractional PLL");
        }
        int[] lock = r82xxRead(0x00, 3);
        if (lock.length >= 3) {
            if ((lock[2] & 0x40) != 0) steps.add("R82xx PLL lock bit is set.");
            else steps.add("R82xx PLL lock bit was not set after tuning; IQ may be weak/noisy until retuned.");
        } else {
            steps.add("Could not read R82xx PLL lock status; continuing after register writes.");
        }
        steps.add(String.format(Locale.US, "Tuned center %.3f MHz via LO %.3f MHz.", centerFrequencyHz / 1_000_000.0, pll.loFrequencyHz / 1_000_000.0));
        return f;
    }

    private int setR82xxGain(RtlSdrIqPipeline.Config config) {
        if (config.agc) {
            int f = 0;
            f += r82xxWriteRegMask(0x05, 0x00, 0x10, "LNA auto gain");
            f += r82xxWriteRegMask(0x07, 0x10, 0x10, "Mixer auto gain");
            steps.add("R82xx gain mode: AGC.");
            return f;
        }
        int[] idx = RtlSdrTuningPlan.r82xxGainIndexes(config.gainTenthsDb);
        int f = 0;
        f += r82xxWriteRegMask(0x05, 0x10, 0x10, "LNA manual mode");
        f += r82xxWriteRegMask(0x07, 0x00, 0x10, "Mixer manual mode");
        f += r82xxWriteRegMask(0x05, idx[0], 0x0f, "LNA gain index");
        f += r82xxWriteRegMask(0x07, idx[1], 0x0f, "Mixer gain index");
        steps.add(String.format(Locale.US, "R82xx manual gain %.1f dB -> LNA index %d, mixer index %d.", config.gainTenthsDb / 10.0, idx[0], idx[1]));
        return f;
    }

    private int resetBuffer() {
        int f = 0;
        f += require("USB FIFO reset", writeReg(USBB, USB_EPA_CTL, 0x1002, 2));
        f += require("USB FIFO release", writeReg(USBB, USB_EPA_CTL, 0x0000, 2));
        return f;
    }

    private int r82xxWriteRegMask(int reg, int val, int mask, String label) {
        int old = r82xxRegs[reg] & 0xff;
        int next = (old & ~mask) | (val & mask);
        return require(label, r82xxWriteReg(reg, next));
    }

    private int r82xxWriteReg(int reg, int val) {
        byte[] one = new byte[]{(byte) (val & 0xff)};
        int rc = r82xxWriteArr(reg, one);
        if (rc == 0 && reg >= 0 && reg < r82xxRegs.length) r82xxRegs[reg] = one[0];
        return rc;
    }

    private int r82xxWriteArr(int reg, byte[] values) {
        int pos = 0;
        while (pos < values.length) {
            int n = Math.min(7, values.length - pos);
            byte[] buf = new byte[n + 1];
            buf[0] = (byte) ((reg + pos) & 0xff);
            System.arraycopy(values, pos, buf, 1, n);
            int rc = writeArray(IICB, tunerAddr, buf);
            if (rc != 0) return rc;
            for (int i = 0; i < n && reg + pos + i < r82xxRegs.length; i++) r82xxRegs[reg + pos + i] = values[pos + i];
            pos += n;
        }
        return 0;
    }

    private int[] r82xxRead(int reg, int len) {
        byte[] request = new byte[]{(byte) (reg & 0xff)};
        if (writeArray(IICB, tunerAddr, request) != 0) return new int[0];
        byte[] out = new byte[len];
        int got = readArray(IICB, tunerAddr, out);
        if (got != len) return new int[0];
        int[] values = new int[len];
        for (int i = 0; i < len; i++) values[i] = bitReverse(out[i] & 0xff);
        return values;
    }

    private int i2cReadReg(int addr, int reg) {
        byte[] request = new byte[]{(byte) (reg & 0xff)};
        if (writeArray(IICB, addr, request) != 0) return -1;
        byte[] out = new byte[1];
        int got = readArray(IICB, addr, out);
        return got == 1 ? (out[0] & 0xff) : -1;
    }

    private void i2cRepeater(boolean on) {
        demodWrite(1, 0x01, on ? 0x18 : 0x10, 1);
    }

    private int writeReg(int block, int addr, int val, int len) {
        byte[] data = new byte[len];
        if (len == 1) data[0] = (byte) (val & 0xff);
        else {
            data[0] = (byte) ((val >> 8) & 0xff);
            data[1] = (byte) (val & 0xff);
        }
        int index = (block << 8) | 0x10;
        if (block == IRB) index = (SYSB << 8) | 0x11;
        int r = connection.controlTransfer(CTRL_OUT, 0, addr, index, data, len, TIMEOUT_MS);
        return r == len ? 0 : -1;
    }

    private int demodWrite(int page, int addr, int val, int len) {
        byte[] data = new byte[len];
        if (len == 1) data[0] = (byte) (val & 0xff);
        else {
            data[0] = (byte) ((val >> 8) & 0xff);
            data[1] = (byte) (val & 0xff);
        }
        int r = connection.controlTransfer(CTRL_OUT, 0, (addr << 8) | 0x20, 0x10 | page, data, len, TIMEOUT_MS);
        demodRead(0x0a, 0x01, 1);
        return r == len ? 0 : -1;
    }

    private int demodRead(int page, int addr, int len) {
        byte[] data = new byte[len];
        return connection.controlTransfer(CTRL_IN, 0, (addr << 8) | 0x20, page, data, len, TIMEOUT_MS);
    }

    private int writeArray(int block, int addr, byte[] array) {
        int index = (block << 8) | 0x10;
        if (block == IRB) index = (SYSB << 8) | 0x11;
        int r = connection.controlTransfer(CTRL_OUT, 0, addr, index, array, array.length, TIMEOUT_MS);
        return r == array.length ? 0 : -1;
    }

    private int readArray(int block, int addr, byte[] array) {
        int index = block << 8;
        if (block == IRB) index = (SYSB << 8) | 0x01;
        return connection.controlTransfer(CTRL_IN, 0, addr, index, array, array.length, TIMEOUT_MS);
    }

    private int require(String label, int rc) {
        if (rc == 0) return 0;
        steps.add(label + " failed");
        return 1;
    }

    private String compactSteps() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(10, steps.size()); i++) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("- ").append(steps.get(i));
        }
        if (steps.size() > 10) sb.append("\n- ").append(steps.size() - 10).append(" more control detail(s)");
        return sb.toString();
    }

    private static int bitReverse(int b) {
        b = ((b & 0xF0) >> 4) | ((b & 0x0F) << 4);
        b = ((b & 0xCC) >> 2) | ((b & 0x33) << 2);
        b = ((b & 0xAA) >> 1) | ((b & 0x55) << 1);
        return b & 0xff;
    }

    private static R82xxRange rangeFor(long freqHz) {
        long mhz = freqHz / 1_000_000L;
        R82xxRange selected = RANGES[0];
        for (R82xxRange r : RANGES) {
            if (mhz >= r.freqMhz) selected = r;
            else break;
        }
        return selected;
    }

    private static final R82xxRange[] RANGES = new R82xxRange[]{
            new R82xxRange(0, 0x08, 0x02, 0xdf, 0x00),
            new R82xxRange(50, 0x08, 0x02, 0xbe, 0x00),
            new R82xxRange(55, 0x08, 0x02, 0x8b, 0x00),
            new R82xxRange(60, 0x08, 0x02, 0x7b, 0x00),
            new R82xxRange(65, 0x08, 0x02, 0x69, 0x00),
            new R82xxRange(70, 0x08, 0x02, 0x58, 0x00),
            new R82xxRange(75, 0x00, 0x02, 0x44, 0x00),
            new R82xxRange(80, 0x00, 0x02, 0x44, 0x00),
            new R82xxRange(90, 0x00, 0x02, 0x34, 0x00),
            new R82xxRange(100, 0x00, 0x02, 0x34, 0x00),
            new R82xxRange(110, 0x00, 0x02, 0x24, 0x00),
            new R82xxRange(120, 0x00, 0x02, 0x24, 0x00),
            new R82xxRange(140, 0x00, 0x02, 0x14, 0x00),
            new R82xxRange(180, 0x00, 0x02, 0x13, 0x00),
            new R82xxRange(220, 0x00, 0x02, 0x13, 0x00),
            new R82xxRange(250, 0x00, 0x02, 0x11, 0x00),
            new R82xxRange(280, 0x00, 0x02, 0x00, 0x00),
            new R82xxRange(310, 0x00, 0x41, 0x00, 0x00),
            new R82xxRange(450, 0x00, 0x41, 0x00, 0x00),
            new R82xxRange(588, 0x00, 0x40, 0x00, 0x00),
            new R82xxRange(650, 0x00, 0x40, 0x00, 0x00)
    };

    private static final class R82xxRange {
        final int freqMhz;
        final int openD;
        final int rfMux;
        final int tfC;
        final int xtal0p;

        R82xxRange(int freqMhz, int openD, int rfMux, int tfC, int xtal0p) {
            this.freqMhz = freqMhz;
            this.openD = openD;
            this.rfMux = rfMux;
            this.tfC = tfC;
            this.xtal0p = xtal0p;
        }
    }

    public static final class ControlReport {
        public final boolean ok;
        public final int failures;
        public final String tuner;
        public final String status;
        public final String detail;

        ControlReport(boolean ok, int failures, String tuner, String status, String detail) {
            this.ok = ok;
            this.failures = failures;
            this.tuner = tuner;
            this.status = status;
            this.detail = detail;
        }
    }
}
