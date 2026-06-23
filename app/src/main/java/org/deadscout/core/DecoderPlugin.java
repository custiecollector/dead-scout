package org.deadscout.core;

public final class DecoderPlugin {
    public final String id;
    public final String name;
    public final String protocol;
    public final String inputType;
    public final String frequencyHints;
    public final String fieldsEmitted;
    public final String exportFormats;
    public final String license;
    public final String notes;

    public DecoderPlugin(String id, String name, String protocol, String inputType, String frequencyHints,
                         String fieldsEmitted, String exportFormats, String license, String notes) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.protocol = protocol == null ? "" : protocol;
        this.inputType = inputType == null ? "" : inputType;
        this.frequencyHints = frequencyHints == null ? "" : frequencyHints;
        this.fieldsEmitted = fieldsEmitted == null ? "" : fieldsEmitted;
        this.exportFormats = exportFormats == null ? "" : exportFormats;
        this.license = license == null ? "" : license;
        this.notes = notes == null ? "" : notes;
    }

    public String card() {
        return name + "\nProtocol: " + protocol + "\nInput: " + inputType + "\nFrequency hints: " + frequencyHints
                + "\nFields: " + fieldsEmitted + "\nExports: " + exportFormats + "\nLicense: " + license
                + (notes.isEmpty() ? "" : "\nNotes: " + notes);
    }
}
