package org.deadscout.app;

import org.deadscout.core.CaptureSession;
import org.deadscout.core.SessionDatabase;
import org.deadscout.core.SpectrumSnapshot;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

final class SessionLibraryPanel {
    private final MainActivity activity;

    SessionLibraryPanel(MainActivity activity) {
        this.activity = activity;
    }

    void render(CaptureSession session) {
        activity.section("Session save / load");
        activity.card("Capture session bundle", "Saves source, frequency, sample rate, start/stop time, snapshots, decoded packets, signal observations, notes, and tags into app-private DeadScout JSON.", MainActivity.ACCENT);
        activity.buttonRow(new String[]{"Save active", "Load newest", "Import session"}, new android.view.View.OnClickListener[]{
                v -> saveActiveSession(),
                v -> loadNewestSession(),
                v -> activity.toggleImportCapture()
        });
        activity.card("Session library status", activity.sessionLibraryStatus, session == null ? MainActivity.QUIET : MainActivity.ACCENT);
        try {
            SessionDatabase db = database();
            List<SessionDatabase.Entry> entries = db.listSessions();
            if (entries.isEmpty()) {
                activity.card("Saved sessions", "No saved sessions yet. Tap Save active after importing/capturing data.", MainActivity.QUIET);
            } else {
                int shown = 0;
                for (int i = entries.size() - 1; i >= 0 && shown < 4; i--, shown++) {
                    SessionDatabase.Entry e = entries.get(i);
                    final String id = e.id;
                    activity.actionCard("Saved: " + e.id, e.card(), "Load", MainActivity.ACCENT, v -> loadSessionById(id));
                }
            }
        } catch (IOException ex) {
            activity.card("Saved sessions", "Could not read session library: " + ex.getMessage(), MainActivity.WARN);
        }
    }

    private void saveActiveSession() {
        CaptureSession session = activity.activeSession();
        if (session == null) {
            activity.sessionLibraryStatus = "No active session to save. Import, monitor, sweep, or create a Lab candidate first.";
            activity.setActionaauxError("No active session", "Start/import a capture before saving a DeadScout session bundle.", activity.sessionLibraryStatus);
            activity.render();
            return;
        }
        try {
            session.withSource(activity.sourceStatus(), activity.rtlConfig.frequencyHz, activity.rtlConfig.sampleRateHz);
            if (!activity.isAnyMonitorRunning()) session.finish(System.currentTimeMillis());
            SpectrumSnapshot snapshot = activity.currentSpectrumSnapshot();
            if (snapshot != null) session.addSnapshot(snapshot.summary() + "\n" + snapshot.waterfallText(12));
            session.addTag("active");
            session.addTag(activity.sourceStatus().toLowerCase(Locale.US).replace(' ', '-'));
            activity.trimCaptureMonitorSession(session);
            SessionDatabase.Entry entry = database().saveSession(session, session.tags);
            activity.sessionLibraryStatus = "Saved active session: " + entry.card();
            activity.clearActionaauxError();
        } catch (IOException ex) {
            activity.sessionLibraryStatus = "Save failed: " + ex.getMessage();
            activity.setActionaauxError("Session save failed", "DeadScout could not write the session bundle to app-private storage. Retry or free storage.", ex.toString());
        }
        activity.render();
    }

    private void loadNewestSession() {
        try {
            List<SessionDatabase.Entry> entries = database().listSessions();
            if (entries.isEmpty()) {
                activity.sessionLibraryStatus = "No saved sessions found.";
                activity.setActionaauxError("No saved sessions", "Save an active session first, or use Import session to load a DeadScout JSON bundle.", activity.sessionLibraryStatus);
                activity.render();
                return;
            }
            loadSessionById(entries.get(entries.size() - 1).id);
        } catch (IOException ex) {
            activity.sessionLibraryStatus = "Load failed: " + ex.getMessage();
            activity.setActionaauxError("Session load failed", "DeadScout could not read the session library.", ex.toString());
            activity.render();
        }
    }

    private void loadSessionById(String id) {
        try {
            activity.stopRunningCaptureMonitors(CaptureMonitorCoordinator.NONE);
            CaptureSession loaded = CaptureSession.fromJson(database().readSessionJson(id));
            activity.loadedLibrarySession = loaded;
            activity.rtlSession = loaded;
            activity.lastImport = null;
            activity.lastImportBytes = null;
            activity.rtlSnapshot = null;
            activity.selectedPacketIndex = 0;
            activity.packetFilter = "";
            activity.sessionLibraryStatus = "Loaded session: " + loaded.id + " · packets " + loaded.packets.size() + " · observations " + loaded.observations.size();
            activity.clearActionaauxError();
            activity.mode = "Packets";
        } catch (Exception ex) {
            activity.sessionLibraryStatus = "Load failed: " + ex.getMessage();
            activity.setActionaauxError("Session load failed", "DeadScout could not parse that saved session bundle. Try another saved session or import the JSON again.", ex.toString());
        }
        activity.render();
    }

    private SessionDatabase database() {
        return new SessionDatabase(new File(activity.getFilesDir(), "deadscout-library"));
    }
}
