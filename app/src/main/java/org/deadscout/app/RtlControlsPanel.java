package org.deadscout.app;

import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.deadscout.core.CaptureSession;
import org.deadscout.core.FrequencyPlan;
import org.deadscout.core.RtlSdrIqPipeline;

import java.util.Locale;

final class RtlControlsPanel {
    private final MainActivity activity;

    RtlControlsPanel(MainActivity activity) {
        this.activity = activity;
    }

    void renderControls() {
        if (!activity.rtlControlsVisible && !activity.usbAdapterScanner.hasRtlSdr() && activity.rtlSession == null) return;
        activity.section("RTL-SDR controls");
        renderRtlTuningStatusCard();
        renderTunerControls();
        renderLiveRtlCaptureButton();
        renderAudioMonitorControls();
        renderRadioSetupControls();
        renderAudioStatusCard();
        renderExternalRtlFallbackControls();
        renderRadioStatusCard();
    }

    private void renderRtlTuningStatusCard() {
        activity.card("Tuning", activity.rtlTuningLine(), MainActivity.WARN);
    }

    private void renderAudioStatusCard() {
        activity.card("Audio monitor", activity.rtlAudioStatus, activity.lastRtlIqBytes.length > 0 ? MainActivity.ACCENT : MainActivity.QUIET);
    }

    private void renderRadioStatusCard() {
        activity.card("Radio status", operatorRtlStatus(), activity.rtlSnapshot == null ? MainActivity.WARN : MainActivity.ACCENT);
    }

    void renderLiveSourceStatus() {
        activity.card("Live source status", liveSourceStatusText(), liveSourceStatusColor());
    }

    private String liveSourceStatusText() {
        String source = activity.sourceStatus();
        StringBuilder sb = new StringBuilder();
        sb.append("Active source: ").append(activity.activeMonitorKind()).append(" / ").append(source);
        appendLiveSourceTuningStatus(sb);
        if (CaptureMonitorCoordinator.ANDROID_PACKET.equals(activity.activeMonitorKind()) || activity.androidPacketButtonLabel().startsWith("Stop")) {
            sb.append("\n").append(activity.androidPacketStatusLine());
        }
        appendLiveSourceSessionStatus(sb);
        appendHiddenTunerWarning(sb);
        return sb.toString();
    }

    private void appendLiveSourceTuningStatus(StringBuilder sb) {
        sb.append("\nHost/port: ").append(activity.rtlTcpCapture || activity.showExternalRtlFallback ? "127.0.0.1:1234" : "USB/local");
        sb.append("\nFrequency: ").append(activity.formatMhz(activity.rtlConfig.frequencyHz)).append(" MHz");
        sb.append("\nSample rate: ").append(activity.rtlConfig.sampleRateHz).append(" sps");
        sb.append("\nIQ bytes: ").append(activity.lastRtlIqBytes == null ? 0 : activity.lastRtlIqBytes.length);
        sb.append("\nUpdate count: RTL ").append(activity.rtlLiveRefreshCount).append(" · rtl_tcp ").append(activity.rtlTcpRefreshCount).append(" · USB ").append(activity.usbAdapterRefreshCount);
    }

    private void appendLiveSourceSessionStatus(StringBuilder sb) {
        CaptureSession session = activity.activeSession();
        if (session != null) sb.append("\nSession: ").append(session.id).append(" · observations ").append(session.observations.size()).append(" · packets ").append(session.packets.size());
    }

    private void appendHiddenTunerWarning(StringBuilder sb) {
        if ((activity.rtlStatus != null && activity.rtlStatus.toLowerCase(Locale.US).contains("warning")) && !activity.showRawPacketDetails) sb.append("\nRadio setup: IQ/status received; nonfatal tuner warnings are hidden. Use Show technical packet details for raw control notes.");
    }

    private int liveSourceStatusColor() {
        return activity.isAnyMonitorRunning() ? MainActivity.ACCENT : MainActivity.QUIET;
    }

    private void stopAudioFromControls() {
        activity.stopRtlAudioMonitor(false);
        activity.stopRtlAudio();
        activity.rtlAudioStatus = "Radio audio stopped.";
        activity.render();
    }

    private void renderLiveRtlCaptureButton() {
        activity.actionButton(activity.rtlLiveCapture ? "Stop RTL capture" : "Start RTL capture", activity.rtlLiveCapture ? MainActivity.WARN : MainActivity.ACCENT, MainActivity.BG, v -> activity.toggleRtlLiveMonitor());
    }

    private void renderAudioMonitorControls() {
        activity.buttonRow(new String[]{amMonitorLabel(), fmMonitorLabel(), "Stop audio"}, new View.OnClickListener[]{
                v -> toggleAmMonitor(),
                v -> toggleFmMonitor(),
                v -> stopAudioFromControls()
        });
    }

    private String amMonitorLabel() {
        return activity.rtlAudioMonitor && "AM".equals(activity.rtlAudioMode) ? "Stop AM monitor" : "Start AM monitor";
    }

    private String fmMonitorLabel() {
        return activity.rtlAudioMonitor && "FM".equals(activity.rtlAudioMode) ? "Stop FM monitor" : "Start FM monitor";
    }

    private void toggleAmMonitor() {
        activity.toggleRtlAudioMonitor(false);
    }

    private void toggleFmMonitor() {
        activity.toggleRtlAudioMonitor(true);
    }

    private void renderRadioSetupControls() {
        renderRadioSetupToggle();
        if (!activity.showRtlRadioSetup) return;
        renderRtlGainControls();
        renderRtlSampleRateControls();
        renderRtlSetupActions();
    }

    private void renderRadioSetupToggle() {
        activity.actionButton(activity.showRtlRadioSetup ? "Hide radio setup" : "Show radio setup", MainActivity.PANEL_2, MainActivity.TEXT, v -> toggleRadioSetup());
    }

    private void renderRtlGainControls() {
        activity.buttonRow(new String[]{activity.rtlConfig.agc ? "AGC on" : "Manual gain", "Gain -", "Gain +"}, new View.OnClickListener[]{
                v -> toggleGainMode(),
                v -> decreaseGain(),
                v -> increaseGain()
        });
    }

    private void renderRtlSampleRateControls() {
        activity.buttonRow(new String[]{"1.024 MS/s", "2.048 MS/s", "2.4 MS/s"}, new View.OnClickListener[]{
                v -> setSampleRate1024(),
                v -> setSampleRate2048(),
                v -> setSampleRate2400()
        });
    }

    private void renderRtlSetupActions() {
        activity.actionButton("Quick built-in USB test", MainActivity.PANEL_3, MainActivity.TEXT, v -> activity.runDirectUsbProbe());
        activity.actionButton("Record short IQ audio clip", MainActivity.PANEL_3, MainActivity.TEXT, v -> recordAudioClip());
        activity.actionButton("Play last AM clip", MainActivity.PANEL_3, MainActivity.TEXT, v -> playAmClip());
        activity.actionButton("Play last FM clip", MainActivity.PANEL_3, MainActivity.TEXT, v -> playFmClip());
        activity.actionButton("Stop audio", MainActivity.PANEL_3, MainActivity.TEXT, v -> stopAudioFromControls());
    }

    private void recordAudioClip() {
        activity.recordRtlAudioClip();
    }

    private void playAmClip() {
        activity.playRtlAudio(false);
    }

    private void playFmClip() {
        activity.playRtlAudio(true);
    }

    private void renderExternalRtlFallbackControls() {
        activity.actionButton(activity.showExternalRtlFallback ? "Hide external rtl_tcp fallback" : "Show external rtl_tcp fallback", MainActivity.PANEL_2, MainActivity.TEXT, v -> toggleExternalRtlFallback());
        if (!activity.showExternalRtlFallback) return;
        activity.card("External driver fallback", "Only use this if built-in USB capture fails. External rtl_tcp capture also uses Start/Stop: launch the driver if needed, then start the monitor; stop it from here.", MainActivity.WARN);
        activity.actionButton("Launch external RTL-SDR driver", MainActivity.PANEL_3, MainActivity.TEXT, v -> launchExternalRtlDriver());
        activity.actionButton(activity.rtlTcpCapture ? "Stop external rtl_tcp capture" : "Start external rtl_tcp capture", activity.rtlTcpCapture ? MainActivity.WARN : MainActivity.PANEL_3, activity.rtlTcpCapture ? MainActivity.BG : MainActivity.TEXT, v -> toggleExternalRtlTcpCapture());
    }

    private void launchExternalRtlDriver() {
        activity.launchLocalRtlDriver();
    }

    private void toggleExternalRtlTcpCapture() {
        activity.chooseRtlTcpPath();
    }

    private void renderTunerEntryRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, activity.dp(3), 0, activity.dp(3));
        EditText mhz = new EditText(activity);
        mhz.setSingleLine(true);
        mhz.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        mhz.setText(String.format(Locale.US, "%.3f", activity.rtlConfig.frequencyHz / 1_000_000.0));
        mhz.setTextColor(MainActivity.TEXT);
        mhz.setTextSize(14);
        mhz.setSelectAllOnFocus(true);
        mhz.setHint("MHz");
        mhz.setHintTextColor(MainActivity.MUTED);
        mhz.setBackground(activity.rounded(MainActivity.SURFACE, 16, MainActivity.STROKE, 1));
        mhz.setPadding(activity.dp(12), 0, activity.dp(12), 0);
        row.addView(mhz, new LinearLayout.LayoutParams(0, activity.dp(46), 2));
        Button tune = activity.smallButton("Tune");
        tune.setOnClickListener(v -> activity.tuneFromEdit(mhz));
        LinearLayout.LayoutParams tuneLp = new LinearLayout.LayoutParams(0, activity.dp(46), 1);
        tuneLp.setMargins(activity.dp(5), 0, 0, 0);
        row.addView(tune, tuneLp);
        activity.root.addView(row);
    }

    private void renderTunerControls() {
        renderTunerEntryRow();
        renderQuickTunerRows();
        renderRecentFavoriteTunerControls();
        activity.actionButton(activity.showRtlPresets ? "Hide more presets" : "Show more presets", MainActivity.PANEL_2, MainActivity.TEXT, v -> toggleMorePresets());
        if (!activity.showRtlPresets) return;
        renderPresetSearchAndGroups();
        renderExpandedPresetRows();
    }

    private void renderQuickTunerRows() {
        activity.buttonRow(new String[]{"-1M", "-100k", "+100k", "+1M"}, new View.OnClickListener[]{
                stepFrequency(-1_000_000L),
                stepFrequency(-100_000L),
                stepFrequency(100_000L),
                stepFrequency(1_000_000L)
        });
        activity.buttonRow(new String[]{"433.92 ISM", "915 ISM", "1090 ADS-B"}, new View.OnClickListener[]{
                rtlPreset(433_920_000L, 2_400_000, 280),
                rtlPreset(915_000_000L, 2_400_000, 280),
                rtlPreset(1_090_000_000L, 2_400_000, 360)
        });
    }

    private void renderExpandedPresetRows() {
        renderLowBandExpandedPresetRow();
        renderAirWeatherExpandedPresetRow();
        renderAprsMarineExpandedPresetRow();
        renderWeatherIsmExpandedPresetRow();
        renderUhfExpandedPresetRow();
        renderSubGhzExpandedPresetRow();
        renderUpperRtlExpandedPresetRow();
        renderExpandedPresetUtilityRow();
    }

    private void renderLowBandExpandedPresetRow() {
        activity.buttonRow(new String[]{"-10M", "27 CB", "50 6m"}, new View.OnClickListener[]{
                stepFrequency(-10_000_000L),
                rtlPreset(27_185_000L, 1_024_000, 280),
                rtlPreset(50_125_000L, 1_024_000, 280)
        });
    }

    private void renderAirWeatherExpandedPresetRow() {
        activity.buttonRow(new String[]{"72 Pager", "118 Air", "137 NOAA"}, new View.OnClickListener[]{
                rtlPreset(72_000_000L, 1_024_000, 280),
                rtlPreset(118_000_000L, 1_024_000, 280),
                rtlPreset(137_100_000L, 1_024_000, 280)
        });
    }

    private void renderAprsMarineExpandedPresetRow() {
        activity.buttonRow(new String[]{"144.39 APRS", "145.825 ISS", "AIS 162"}, new View.OnClickListener[]{
                rtlPreset(144_390_000L, 1_024_000, 280),
                rtlPreset(145_825_000L, 1_024_000, 280),
                rtlPreset(162_025_000L, 1_024_000, 280)
        });
    }

    private void renderWeatherIsmExpandedPresetRow() {
        activity.buttonRow(new String[]{"162.55 WX", "315 ISM", "345 ISM"}, new View.OnClickListener[]{
                rtlPreset(162_550_000L, 1_024_000, 280),
                rtlPreset(315_000_000L, 2_048_000, 280),
                rtlPreset(345_000_000L, 2_048_000, 280)
        });
    }

    private void renderUhfExpandedPresetRow() {
        activity.buttonRow(new String[]{"433.92", "462 FRS", "868.35"}, new View.OnClickListener[]{
                rtlPreset(433_920_000L, 2_400_000, 280),
                rtlPreset(462_562_500L, 1_024_000, 280),
                rtlPreset(868_350_000L, 2_400_000, 280),
        });
    }

    private void renderSubGhzExpandedPresetRow() {
        activity.buttonRow(new String[]{"902 Mesh", "915 LoRa", "929 Pager"}, new View.OnClickListener[]{
                rtlPreset(902_000_000L, 2_400_000, 280),
                rtlPreset(915_000_000L, 2_400_000, 280),
                rtlPreset(929_000_000L, 2_400_000, 360)
        });
    }

    private void renderUpperRtlExpandedPresetRow() {
        activity.buttonRow(new String[]{"978 UAT", "1.2G Sat", "1.575 GPS"}, new View.OnClickListener[]{
                rtlPreset(978_000_000L, 2_400_000, 360),
                rtlPreset(1_200_000_000L, 2_400_000, 360),
                rtlPreset(1_575_420_000L, 2_400_000, 360)
        });
    }

    private void renderExpandedPresetUtilityRow() {
        activity.buttonRow(new String[]{"+10M", "1.024 MS/s", "2.4 MS/s"}, new View.OnClickListener[]{
                stepFrequency(10_000_000L),
                v -> setSampleRate1024(),
                v -> setSampleRate2400()
        });
    }

    private void renderRecentFavoriteTunerControls() {
        activity.buttonRow(new String[]{"Favorite current", "Recall favorite", "Recall recent"}, new View.OnClickListener[]{
                v -> favoriteCurrentFrequency(),
                v -> recallFavoriteFrequency(),
                v -> recallRecentFrequency()
        });
        renderTunerMemoryStatusCard();
    }

    private void renderTunerMemoryStatusCard() {
        StringBuilder sb = new StringBuilder();
        sb.append("Recent: ").append(activity.rtlTunerMemory.recentList(5));
        sb.append("\nFavorites: ").append(activity.rtlTunerMemory.favoriteList(5));
        activity.card("Tuner memory", sb.toString(), activity.rtlTunerMemory.hasFavorites() ? MainActivity.ACCENT : MainActivity.QUIET);
    }

    private void favoriteCurrentFrequency() {
        activity.toggleFavoriteFrequency(activity.rtlConfig.frequencyHz);
        activity.render();
    }

    private void recallFavoriteFrequency() {
        if (!activity.rtlTunerMemory.hasFavorites()) {
            activity.rtlStatus = "No favorite frequencies yet. Tune a valid frequency, then tap Favorite current.";
            activity.render();
        } else {
            activity.setRtlFrequency(activity.rtlTunerMemory.firstFavorite(), "Favorite recalled");
        }
    }

    private void recallRecentFrequency() {
        if (!activity.rtlTunerMemory.hasRecent()) {
            activity.rtlStatus = "No recent frequencies yet.";
            activity.render();
        } else {
            activity.setRtlFrequency(activity.rtlTunerMemory.firstRecent(), "Recent recalled");
        }
    }

    private void setSampleRate1024() {
        activity.setRtlSampleRate(1_024_000);
    }

    private void setSampleRate2048() {
        activity.setRtlSampleRate(2_048_000);
    }

    private void setSampleRate2400() {
        activity.setRtlSampleRate(2_400_000);
    }

    private View.OnClickListener stepFrequency(long deltaHz) {
        return v -> activity.stepRtlFrequency(deltaHz);
    }

    private View.OnClickListener rtlPreset(long frequencyHz, int sampleRateHz, int gainTenthsDb) {
        return v -> activity.setRtlPreset(frequencyHz, sampleRateHz, gainTenthsDb, false);
    }

    private void toggleRadioSetup() {
        activity.showRtlRadioSetup = !activity.showRtlRadioSetup;
        activity.render();
    }

    private void toggleExternalRtlFallback() {
        activity.showExternalRtlFallback = !activity.showExternalRtlFallback;
        activity.render();
    }

    private void toggleGainMode() {
        RtlSdrIqPipeline.Config config = activity.rtlConfig;
        activity.applyRtlConfig(rtlConfigWithGain(config.gainTenthsDb, !config.agc), "Gain mode changed");
    }

    private void decreaseGain() {
        RtlSdrIqPipeline.Config config = activity.rtlConfig;
        activity.applyRtlConfig(rtlConfigWithGain(Math.max(0, config.gainTenthsDb - 50), false), "Gain decreased");
    }

    private void increaseGain() {
        RtlSdrIqPipeline.Config config = activity.rtlConfig;
        activity.applyRtlConfig(rtlConfigWithGain(Math.min(496, config.gainTenthsDb + 50), false), "Gain increased");
    }

    private RtlSdrIqPipeline.Config rtlConfigWithGain(int gainTenthsDb, boolean agc) {
        RtlSdrIqPipeline.Config config = activity.rtlConfig;
        return new RtlSdrIqPipeline.Config(config.frequencyHz, config.sampleRateHz,
                gainTenthsDb, config.ppmCorrection, agc, config.fftSize, config.squelchDbAboveNoise);
    }

    private void toggleMorePresets() {
        activity.showRtlPresets = !activity.showRtlPresets;
        activity.render();
    }

    private void applyPresetSearch(EditText search) {
        activity.presetSearch = search.getText().toString().trim();
        activity.render();
    }

    private void clearPresetSearch() {
        activity.presetSearch = "";
        activity.render();
    }

    private void togglePresetAll() {
        activity.showPresetAll = !activity.showPresetAll;
        activity.render();
    }

    private void togglePresetVhf() {
        activity.showPresetVhf = !activity.showPresetVhf;
        activity.render();
    }

    private void togglePresetUhf() {
        activity.showPresetUhf = !activity.showPresetUhf;
        activity.render();
    }

    private void togglePresetIsm() {
        activity.showPresetIsm = !activity.showPresetIsm;
        activity.render();
    }

    private void renderPresetSearchAndGroups() {
        activity.section("Preset search / band groups");
        EditText search = renderPresetSearchInput();
        renderPresetSearchButtons(search);
        renderPresetBandToggleButtons();
        renderPresetBandGroups();
    }

    private EditText renderPresetSearchInput() {
        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setText(activity.presetSearch);
        search.setHint("Search presets: ISM, airband, ADS-B, ISM, 802.15.4…");
        search.setHintTextColor(MainActivity.MUTED);
        search.setTextColor(MainActivity.TEXT);
        search.setTextSize(12);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setBackground(activity.rounded(MainActivity.SURFACE, 16, MainActivity.STROKE, 1));
        search.setPadding(activity.dp(12), 0, activity.dp(12), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(46));
        lp.setMargins(0, activity.dp(4), 0, activity.dp(4));
        activity.root.addView(search, lp);
        return search;
    }

    private void renderPresetSearchButtons(EditText search) {
        activity.buttonRow(new String[]{"Apply search", "Clear search", activity.showPresetAll ? "Collapse refs" : "Show all refs"}, new View.OnClickListener[]{
                v -> applyPresetSearch(search),
                v -> clearPresetSearch(),
                v -> togglePresetAll()
        });
    }

    private void renderPresetBandToggleButtons() {
        activity.buttonRow(new String[]{activity.showPresetVhf ? "Hide VHF" : "Show VHF", activity.showPresetUhf ? "Hide UHF" : "Show UHF", activity.showPresetIsm ? "Hide ISM+" : "Show ISM+"}, new View.OnClickListener[]{
                v -> togglePresetVhf(),
                v -> togglePresetUhf(),
                v -> togglePresetIsm()
        });
    }

    private void renderPresetBandGroups() {
        renderFrequencyPlanGroup("VHF / air / marine / weather", 24_000_000L, 300_000_000L, activity.showPresetVhf);
        renderFrequencyPlanGroup("UHF / sub-GHz", 300_000_000L, 700_000_000L, activity.showPresetUhf);
        renderFrequencyPlanGroup("ISM / ADS-B / upper RTL", 700_000_000L, 1_766_000_000L, activity.showPresetIsm);
        if (activity.showPresetAll || !activity.presetSearch.isEmpty()) renderFrequencyPlanGroup("Other hardware/setup routes", 0L, Long.MAX_VALUE, true);
    }

    private void renderFrequencyPlanGroup(String label, long minHz, long maxHz, boolean expanded) {
        if (!expanded) {
            renderCollapsedFrequencyPlanGroup(label);
            return;
        }
        int shown = 0;
        for (FrequencyPlan plan : FrequencyPlan.presets()) {
            long center = centerFrequency(plan);
            if (!frequencyPlanInGroup(label, minHz, maxHz, center)) continue;
            if (!frequencyMatches(plan, activity.presetSearch)) continue;
            shown++;
            renderFrequencyPlanAction(plan, center);
            if (presetGroupLimitReached(shown)) break;
        }
        if (shown == 0) renderNoFrequencyPlanMatches(label);
    }

    private void renderCollapsedFrequencyPlanGroup(String label) {
        activity.card(label, "Collapsed. Tap the band group toggle to show matching presets.", MainActivity.QUIET);
    }

    private boolean frequencyPlanInGroup(String label, long minHz, long maxHz, long centerHz) {
        boolean inGroup = centerHz >= minHz && centerHz <= maxHz;
        boolean otherGroup = "Other hardware/setup routes".equals(label) && !activity.rtlFrequencyValid(centerHz);
        return inGroup || otherGroup;
    }

    private void renderFrequencyPlanAction(FrequencyPlan plan, long centerHz) {
        if (activity.rtlFrequencyValid(centerHz)) {
            activity.actionCard(plan.display(), plan.notes, "Tune center", MainActivity.ACCENT, v -> tuneFrequencyPlan(centerHz, plan));
        } else {
            activity.actionCard(plan.display(), plan.notes, "Open setup", MainActivity.WARN, v -> activity.openUnavailablePreset(plan));
        }
    }

    private boolean presetGroupLimitReached(int shown) {
        return !activity.showPresetAll && activity.presetSearch.isEmpty() && shown >= 4;
    }

    private void renderNoFrequencyPlanMatches(String label) {
        activity.card(label, "No presets match " + (activity.presetSearch.isEmpty() ? "this group." : "search: " + activity.presetSearch), MainActivity.QUIET);
    }

    private void tuneFrequencyPlan(long centerHz, FrequencyPlan plan) {
        activity.setRtlPreset(centerHz, sampleRateForPlan(plan), activity.rtlConfig.gainTenthsDb, activity.rtlConfig.agc);
    }

    private int sampleRateForPlan(FrequencyPlan plan) {
        long spanHz = plan.endHz - plan.startHz + 1;
        long clampedSpan = Math.min(2_400_000L, Math.max(1_024_000L, spanHz));
        return Math.min(2_400_000, Math.max(1_024_000, (int) clampedSpan));
    }

    private boolean frequencyMatches(FrequencyPlan plan, String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String q = query.toLowerCase(Locale.US);
        return (plan.label + " " + plan.source + " " + plan.notes).toLowerCase(Locale.US).contains(q);
    }

    private long centerFrequency(FrequencyPlan plan) {
        if (plan.startHz == plan.endHz) return plan.startHz;
        return plan.startHz + Math.max(0L, (plan.endHz - plan.startHz) / 2L);
    }

    private String operatorRtlStatus() {
        if (activity.rtlStatus == null) return "";
        if (activity.showRawPacketDetails || activity.showTechnicalErrors) return activity.rtlStatus;
        return activity.rtlStatus.replace(" Radio setup reported non-fatal tuning warnings; IQ data was still received.", " Radio setup details hidden; IQ data was received.")
                .replace(" Setup also reported tuning warnings.", " Setup details hidden.")
                .replace("non-fatal tuning warnings", "setup details hidden");
    }
}
