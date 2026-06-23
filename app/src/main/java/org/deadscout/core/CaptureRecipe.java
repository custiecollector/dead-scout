package org.deadscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CaptureRecipe {
    public final String id;
    public final String label;
    public final long frequencyHz;
    public final int sampleRateHz;
    public final int gainTenthsDb;
    public final boolean agc;
    public final String decoder;
    public final String notes;

    public CaptureRecipe(String id, String label, long frequencyHz, int sampleRateHz, int gainTenthsDb, boolean agc, String decoder, String notes) {
        this.id = clean(id, "recipe");
        this.label = clean(label, this.id);
        this.frequencyHz = frequencyHz;
        this.sampleRateHz = sampleRateHz;
        this.gainTenthsDb = gainTenthsDb;
        this.agc = agc;
        this.decoder = clean(decoder, "operator");
        this.notes = clean(notes, "");
    }

    public RtlSdrIqPipeline.Config rtlConfig() {
        return new RtlSdrIqPipeline.Config(frequencyHz, sampleRateHz, gainTenthsDb, 0, agc, 1024, 6.0);
    }

    public String card() {
        return String.format(Locale.US, "%.3f MHz · %.3f MS/s · %s · decoder %s\n%s",
                frequencyHz / 1_000_000.0,
                sampleRateHz / 1_000_000.0,
                agc ? "AGC" : String.format(Locale.US, "gain %.1f dB", gainTenthsDb / 10.0),
                decoder,
                notes);
    }

    public static List<CaptureRecipe> builtIns() {
        ArrayList<CaptureRecipe> out = new ArrayList<>();
        out.add(new CaptureRecipe("ism-433-rtl433", "433.92 MHz ISM sensors", 433_920_000L, 1_024_000, 280, false, "rtl_433", "OOK/FSK sensors, remotes, meters, weather stations; packet cards come from rtl_433 JSON/import or integrated decoder output."));
        out.add(new CaptureRecipe("ism-915-rtl433", "915 MHz ISM sensors", 915_000_000L, 1_024_000, 280, false, "rtl_433", "North America ISM telemetry and utility-style burst monitoring with waterfall plus rtl_433 packet normalization."));
        out.add(new CaptureRecipe("adsb-1090", "1090 MHz ADS-B", 1_090_000_000L, 2_400_000, 280, false, "ADS-B", "Aircraft Mode-S/ADS-B capture recipe; decoded aircraft packets should enter Packets when the ADS-B decoder emits frames."));
        out.add(new CaptureRecipe("ais-vhf", "162 MHz marine AIS", 162_000_000L, 1_024_000, 280, false, "AIS", "Marine AIS channel monitoring around 161.975/162.025 MHz with vessel packets once decoder output is available."));
        out.add(new CaptureRecipe("aprs-144390", "144.390 MHz APRS", 144_390_000L, 1_024_000, 280, false, "APRS/AX.25", "VHF packet-radio audio/IQ monitoring; decoded AX.25 frames enter the packet browser."));
        out.add(new CaptureRecipe("pocsag-vhf", "VHF/UHF POCSAG", 152_000_000L, 1_024_000, 280, false, "POCSAG", "Pager-band monitoring recipe; tune to the local paging channel before capture."));
        out.add(new CaptureRecipe("zigbee-802154", "802.15.4 channel 15", FrequencyPlan.ieee802154ChannelToHz(15), 2_000_000, 0, true, "802.15.4", "Use an 802.15.4 sniffer dongle/import path for packets; RTL-SDR can only monitor nearby RF energy at 2.4 GHz with suitable hardware."));
        out.add(new CaptureRecipe("fm-audio", "Narrowband FM voice/data", 162_550_000L, 1_024_000, 280, false, "FM/audio", "Continuous AM/FM audio monitor recipe; keep raw IQ in Signal/Lab until a decoder emits framed data."));
        return Collections.unmodifiableList(out);
    }

    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
