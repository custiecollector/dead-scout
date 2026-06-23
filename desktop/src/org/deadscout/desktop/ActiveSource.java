package org.deadscout.desktop;

enum ActiveSource {
    NONE("idle"), IMPORT("import"), NETWORK("network"), NET_A("NetworkA"), AUXILIARY("Auxiliary"), USB("USB radio"), RTL("RTL"), RTL_TCP("rtl_tcp"), SNIFFER("sniffer");
    private final String label; ActiveSource(String label){this.label=label;} String label(){return label;}
}
