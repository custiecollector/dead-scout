package org.deadscout.core;

import java.util.*;

public final class HardwareCompatibilityDatabase {
    public static final class Entry { public final String label, kind, priority, route, status, notes; public Entry(String label,String kind,String priority,String route,String status,String notes){this.label=label;this.kind=kind;this.priority=priority;this.route=route;this.status=status;this.notes=notes;} public String card(){return kind+"\nPriority: "+priority+"\nRoute: "+route+"\nStatus: "+status+"\nNotes: "+notes;} }
    private HardwareCompatibilityDatabase() {}
    public static List<Entry> priorityMatrix(){ ArrayList<Entry> out=new ArrayList<>(); out.add(new Entry("RTL-SDR Blog V3/V4","RTL2832U/R820T SDR","primary","USB Host or rtl_tcp","implemented; physical reliability depends on attached hardware","Use for supported VHF/UHF/ISM captures.")); out.add(new Entry("802.15.4 sniffers","serial/USB sniffer logs","primary","log import + parser","reader/import coverage available","Confirm firmware output format for live field use.")); out.add(new Entry("HackRF-class SDR","wideband SDR","secondary","operator-provided IQ/import path","planning/import route","Use external capture tooling when needed.")); return Collections.unmodifiableList(out); }
    public static String summary(){StringBuilder sb=new StringBuilder(); for(Entry e:priorityMatrix()) sb.append(e.label).append(" · ").append(e.status).append('\n'); return sb.toString().trim();}
}
