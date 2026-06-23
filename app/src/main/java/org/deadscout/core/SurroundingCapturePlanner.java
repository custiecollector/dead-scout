package org.deadscout.core;

import java.util.*;

public final class SurroundingCapturePlanner { private SurroundingCapturePlanner() {}
    public static List<CaptureCapability> capabilities(){ ArrayList<CaptureCapability> out=new ArrayList<>(); out.add(new CaptureCapability("rtl-ism","ISM SDR capture",315_000_000L,928_000_000L,"RF telemetry and unknown bursts","RTL-SDR USB, rtl_tcp, or imported IQ","IQ/rtl_433 JSON/raw bits","rtl_433, OOK/FSK, burst analyzer",true,true)); out.add(new CaptureCapability("ieee802154","802.15.4 sniffer logs",2_405_000_000L,2_480_000_000L,"802.15.4 frames","operator-selected sniffer log/import","frame hex/LQI/PAN/source/destination","802.15.4 parser",true,true)); out.add(new CaptureCapability("adsb","ADS-B",1_090_000_000L,1_090_000_000L,"aircraft messages","RTL-SDR or import","decoder-dependent frames","ADS-B path",true,false)); return Collections.unmodifiableList(out);}
    public static String coverageSummary(){return capabilities().size()+" public capture capability rows: SDR/import, 802.15.4, ADS-B.";}
    public static String matrix(int limit){StringBuilder sb=new StringBuilder();int i=0; for(CaptureCapability c:capabilities()){ if(i++>=limit) break; sb.append(c.label).append(" · ").append(c.requiredSource).append(" · ").append(c.decoderStack).append('\n'); } return sb.toString().trim();}
}
