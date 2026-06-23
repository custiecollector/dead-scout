package org.deadscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FrequencyPlan { public final String label; public final long startHz,endHz; public final String source,notes;
    public FrequencyPlan(String label,long startHz,long endHz,String source,String notes){this.label=label;this.startHz=startHz;this.endHz=endHz;this.source=source;this.notes=notes;}
    public String display(){return label+" · "+(startHz>0?String.format(java.util.Locale.US,"%.3f-%.3f MHz",startHz/1e6,endHz/1e6):"file/import");}
    public static long ieee802154ChannelToHz(int channel){ return 2_405_000_000L + Math.max(0, channel - 11) * 5_000_000L; }
    public static List<FrequencyPlan> presets(){ ArrayList<FrequencyPlan> out=new ArrayList<>();
        out.add(new FrequencyPlan("ISM / sensor telemetry",315_000_000L,928_000_000L,"RTL-SDR / rtl_433","Common SDR import and rtl_433 JSON review path."));
        out.add(new FrequencyPlan("ADS-B",1_090_000_000L,1_090_000_000L,"RTL-SDR","Aircraft transponder decoder path when supported."));
        out.add(new FrequencyPlan("VHF/UHF packet radio",118_000_000L,470_000_000L,"RTL-SDR / imports","AM/FM/data paths for supported decoders and raw IQ review."));
        out.add(new FrequencyPlan("802.15.4",2_405_000_000L,2_480_000_000L,"Sniffer log import","Frame logs route to 802.15.4 parser and PCAP export."));
        return Collections.unmodifiableList(out);}
}
