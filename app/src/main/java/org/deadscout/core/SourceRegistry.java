package org.deadscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SourceRegistry {
    private SourceRegistry() {}

    public static List<CaptureSourceDescriptor> sources() {
        ArrayList<CaptureSourceDescriptor> out = new ArrayList<>();
        out.add(new CaptureSourceDescriptor("rtl-sdr-usb", "RTL-SDR USB", CaptureSourceDescriptor.SourceKind.RTL_SDR_USB,
                "Built-in USB Host path with direct RTL2832U/R820T control for supported SDR bands.", true, true, false, "~24 MHz-1.7 GHz"));
        out.add(new CaptureSourceDescriptor("rtl-tcp", "rtl_tcp remote SDR", CaptureSourceDescriptor.SourceKind.RTL_TCP,
                "Connect to a user-specified rtl_tcp server for remote SDR samples; no telemetry.", true, false, true, "server dependent"));
        out.add(new CaptureSourceDescriptor("import", "Imported files", CaptureSourceDescriptor.SourceKind.IMPORTED_FILE,
                "IQ, rtl_433 JSON, PCAP/PCAPNG, IP packet hex, 802.15.4 logs, or DeadScout session bundles selected by the user.", true, false, false, "file metadata"));
        out.add(new CaptureSourceDescriptor("rtl-433", "rtl_433 module", CaptureSourceDescriptor.SourceKind.RTL_433_JSON,
                "Decode rtl_433 JSON into normalized packet cards for ISM sensors/remotes/meters.", true, false, false, "315/345/433/868/915 MHz"));
        out.add(new CaptureSourceDescriptor("ieee802154", "802.15.4 sniffer", CaptureSourceDescriptor.SourceKind.IEEE802154_SNIFFER,
                "Serial/USB sniffer log import and packet export for IEEE 802.15.4 frames.", true, true, false, "2405-2480 MHz channels 11-26"));
        out.add(new CaptureSourceDescriptor("packet-data-import", "Packet-data capture/import", CaptureSourceDescriptor.SourceKind.PACKET_DATA_IMPORT,
                "Parse PCAP/IP packet metadata from operator-selected local files; payload decoding stays opt-in and local.", true, false, false, "IP/PCAP metadata"));
        out.add(new CaptureSourceDescriptor("own-device-packet-capture", "Local packet capture", CaptureSourceDescriptor.SourceKind.OWN_DEVICE_PACKET_CAPTURE,
                "No-root VpnService/TUN capture writes raw IPv4/IPv6 packets to app-private PCAP for local packet review.", true, false, true, "IP packet data"));
        out.add(new CaptureSourceDescriptor("packet-workbench", "Packet workbench", CaptureSourceDescriptor.SourceKind.PACKET_WORKBENCH,
                "Display filters, protocol hierarchy, endpoint/conversation stats, hex/ASCII inspector, findings timeline, and PCAP export.", true, false, false, "all packet sources"));
        out.add(new CaptureSourceDescriptor("wideband-sdr", "Wideband SDR scout", CaptureSourceDescriptor.SourceKind.WIDEBAND_SDR,
                "HackRF/Lime/Pluto/SDRplay/Airspy/SoapySDR/imported IQ source contract for hardware-supported frequencies.", true, true, true, "hardware dependent"));
        out.add(new CaptureSourceDescriptor("spectrum-waterfall", "Spectrum + waterfall view", CaptureSourceDescriptor.SourceKind.SPECTRUM_WATERFALL,
                "FFT/peak hold/noise floor/channel occupancy/signal markers over IQ samples.", true, false, false, "IQ-derived"));
        out.add(new CaptureSourceDescriptor("rtl-sdr-iq", "RTL-SDR USB IQ pipeline", CaptureSourceDescriptor.SourceKind.RTL_SDR_IQ_PIPELINE,
                "Tune/gain/sample-rate/ppm/AGC control model and unsigned IQ FFT/waterfall ingestion.", true, true, false, "24 MHz-1.766 GHz"));
        out.add(new CaptureSourceDescriptor("session-library", "Session database", CaptureSourceDescriptor.SourceKind.SESSION_LIBRARY,
                "File-backed session index with tags, notes, search, selected-packet export, and reopenable session bundles.", true, false, false, "app-private storage"));
        out.add(new CaptureSourceDescriptor("signal-fingerprints", "Unknown signal fingerprinting", CaptureSourceDescriptor.SourceKind.SIGNAL_FINGERPRINT,
                "Burst duration, pulse/gap clusters, baud estimate, entropy, autocorrelation, preamaux candidates, and modulation classification.", true, false, false, "IQ/bits/pulses"));
        out.add(new CaptureSourceDescriptor("alert-watch", "Alert / watch mode", CaptureSourceDescriptor.SourceKind.ALERT_WATCH,
                "Rule engine for frequency activity, model/field matches, unknown burst repeats, and signal thresholds.", true, false, false, "session markers/events"));
        out.add(new CaptureSourceDescriptor("decoder-plugins", "Decoder plugin system", CaptureSourceDescriptor.SourceKind.DECODER_PLUGIN,
                "Plugin metadata registry with protocol/input/frequency/fields/export/license hints for built-in and extension decoders.", true, false, false, "IQ/bits/bytes/JSON/PCAP"));
        out.add(new CaptureSourceDescriptor("report-generator", "Report generator", CaptureSourceDescriptor.SourceKind.REPORT_GENERATOR,
                "One-tap Markdown/session summary, protocol hierarchy, findings, correlations, export bundle manifest, and hashes.", true, false, false, "export bundle"));
        out.add(new CaptureSourceDescriptor("remote-nodes", "Remote nodes", CaptureSourceDescriptor.SourceKind.DISTRIBUTED_REMOTE_NODE,
                "Controller/workbench routes for rtl_tcp, remote DeadScout nodes, SDR boxes, PCAP feeds, and WebSocket streams.", true, false, true, "LAN/user-specified"));
        out.add(new CaptureSourceDescriptor("acars-ais", "ACARS / AIS decoders", CaptureSourceDescriptor.SourceKind.ACARS_AIS_DECODER,
                "Aviation ACARS imports/captures and marine AIS frames route into packet cards and reports when decoder modules are available.", true, false, false, "VHF marine/air bands"));
        out.add(new CaptureSourceDescriptor("pocsag-aprs", "POCSAG / APRS decoders", CaptureSourceDescriptor.SourceKind.POCSAG_APRS_DECODER,
                "Pager and amateur packet-radio capture paths for POCSAG/FLEX awareness, APRS/AX.25 frames, raw hex, and timeline review.", true, false, false, "VHF/UHF"));
        return Collections.unmodifiableList(out);
    }
}
