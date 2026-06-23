package org.deadscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DecoderPluginRegistry {
    private DecoderPluginRegistry() {}
    public static List<DecoderPlugin> builtIns() {
        ArrayList<DecoderPlugin> out = new ArrayList<>();
        out.add(new DecoderPlugin("rtl-433-json", "rtl_433 JSON decoder", "ISM telemetry", "rtl_433 JSON", "315/345/433/868/915 MHz", "model,id,channel,battery,temperature,humidity", "session JSON", "DeadScout core", "JSON import path."));
        out.add(new DecoderPlugin("ieee802154", "802.15.4 frame parser", "IEEE 802.15.4", "serial logs/PCAP", "2405-2480 MHz", "PAN,src,dst,seq,LQI", "PCAP/session", "DeadScout core", "Sniffer log import path."));
        out.add(new DecoderPlugin("ip-packet", "IP packet parser", "IPv4/IPv6 packet data", "raw IP/PCAP/TUN", "local packet data", "src,dst,protocol,ports", "PCAP/session", "DeadScout core", "Local packet capture/import path."));
        out.add(new DecoderPlugin("ook-fsk-burst", "OOK/FSK burst analyzer", "Unknown RF bursts", "IQ/raw pulses", "SDR dependent", "pulse,gap,baud,entropy", "session", "DeadScout core", "Unknown signal workbench."));
        return Collections.unmodifiableList(out);
    }
    public static String summary() { StringBuilder sb=new StringBuilder(); for (DecoderPlugin p: builtIns()) sb.append(p.name).append(" · ").append(p.protocol).append('\n'); return sb.toString().trim(); }
    public static List<DecoderPlugin> forInput(String input) { ArrayList<DecoderPlugin> out = new ArrayList<>(); String needle=input==null?"":input.toLowerCase(); for (DecoderPlugin p: builtIns()) if (p.inputType.toLowerCase().contains(needle) || p.protocol.toLowerCase().contains(needle)) out.add(p); return out; }
}
