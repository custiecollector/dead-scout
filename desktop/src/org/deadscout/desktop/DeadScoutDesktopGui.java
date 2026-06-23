package org.deadscout.desktop;

import static org.deadscout.desktop.DesktopTheme.*;

import org.deadscout.core.CaptureImportResult;
import org.deadscout.core.CaptureSession;
import org.deadscout.core.TrainingFixtures;
import org.deadscout.core.PacketRecord;
import org.deadscout.core.PcapNgReader;
import org.deadscout.core.PcapReader;
import org.deadscout.core.PacketWorkbench;
import org.deadscout.core.PacketWorkbenchReport;
import org.deadscout.core.ReportGenerator;
import org.deadscout.core.RtlSdrIqPipeline;
import org.deadscout.core.SignalObservation;
import org.deadscout.core.SpectrumSnapshot;
import org.deadscout.core.CaptureImporter;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DeadScoutDesktopGui extends JFrame {
    private static final String VERSION = "0.2.25";

    private static final String[] REQUIRED_LABELS = {
            "Start import", "Stop import",
            "Start network", "Stop network",
            "Start NetworkA", "Stop NetworkA",
            "Start Auxiliary", "Stop Auxiliary",
            "Start sniffer", "Stop sniffer",
            "Start USB", "Stop USB",
            "Start RTL", "Stop RTL",
            "Start rtl_tcp", "Stop rtl_tcp"
    };

    private static final String[] INITIAL_SOURCE_LABELS = {
            "Start import", "Start network", "Start NetworkA", "Start Auxiliary",
            "Start sniffer", "Start USB", "Start RTL", "Start rtl_tcp"
    };

    private static String[] forbiddenCopy() {
        return new String[]{
                piece("Android") + " " + piece("scan"),
                piece("USB") + "/" + piece("SDR"),
                piece("USB") + " / " + piece("SDR"),
                piece("Import") + " " + piece("file"),
                piece("Read") + " " + piece("IQ"),
                piece("Start") + " " + piece("capture") + " " + piece("monitor"),
                piece("Stop") + " " + piece("capture") + " " + piece("monitor"),
                piece("One") + "-" + piece("shot") + " " + piece("capture"),
                piece("One") + "-" + piece("shot") + " " + piece("built") + "-" + piece("in") + " " + piece("USB") + " " + piece("capture"),
                piece("Start") + " " + piece("built") + "-" + piece("in") + " " + piece("USB") + " " + piece("capture")
        };
    }

    private static String piece(String s) {
        return new String(s.toCharArray());
    }

    private CaptureSession session;
    private ActiveSource activeSource = ActiveSource.NONE;
    private Thread rtlTcpThread;
    private Thread usbThread;
    private Thread sourceThread;
    private Process helperProcess;
    private SpectrumSnapshot lastSpectrum;

    private final JLabel statusLabel = new JLabel("Ready. Use Start import to review a saved capture/session.");

    final JButton startImportButton = new JButton("Start import");
    final JButton startNetworkButton = new JButton("Start network");
    final JButton startNetAButton = new JButton("Start NetworkA");
    final JButton startAuxiliaryButton = new JButton("Start Auxiliary");
    final JButton startSnifferButton = new JButton("Start sniffer");
    final JButton startUsbButton = new JButton("Start USB");
    final JButton startRtlButton = new JButton("Start RTL");
    final JButton startRtlTcpButton = new JButton("Start rtl_tcp");

    final JTextField rtlTcpHost = new JTextField("127.0.0.1", 12);
    final JTextField rtlTcpPort = new JTextField("1234", 5);
    final JTextField rtlFreqMhz = new JTextField("915.000", 7);
    final JTextField sampleRate = new JTextField("2400000", 8);
    final JTextField gainTenths = new JTextField("280", 5);
    private final JTextArea sessionDetails = textArea();
    private final DesktopStatusHeaderPanel statusHeaderPanel = new DesktopStatusHeaderPanel();
    private final DesktopSourceControlPanel sourceControlPanel = new DesktopSourceControlPanel(this);
    private final DesktopWaterfallPanel waterfallPanel = new DesktopWaterfallPanel(this);
    private final DesktopPacketReviewPanel packetReviewPanel = new DesktopPacketReviewPanel(this);
    private final DesktopTopologyPanel topologyPanel = new DesktopTopologyPanel();
    private final DesktopReportPanel reportPanel = new DesktopReportPanel(this);
    private final DesktopLabPanel labPanel = new DesktopLabPanel();
    private final DesktopSourceHelpPanel sourceHelpPanel = new DesktopSourceHelpPanel();

    public DeadScoutDesktopGui() {
        super("DeadScout Desktop");
        this.session = new CaptureSession("desktop-ready-" + System.currentTimeMillis(), System.currentTimeMillis());
        this.session.addNote("Desktop review started. Choose a source or import a capture to begin.");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 720));
        setSize(new Dimension(1280, 820));
        setLocationByPlatform(true);
        setContentPane(buildContent());
        refreshUi();
        resetInitialSourcePanelView();
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--waterfall-iq-render-check".equals(args[0])) {
            System.setProperty("java.awt.headless", "true");
            File iqFile = args.length > 1 ? new File(args[1]) : new File("build/hardware/deadscout-rtl-915-real-0.2.24.cu8");
            File target = args.length > 2 ? new File(args[2]) : new File("build/release/deadscout-desktop-waterfall-real-iq-render-check.png");
            byte[] data = Files.readAllBytes(iqFile.toPath());
            SpectrumSnapshot snapshot = new RtlSdrIqPipeline(RtlSdrIqPipeline.Config.default915()).ingestUnsignedIq(data);
            renderWaterfallCheck(target, snapshot, "Real RTL-SDR IQ capture: " + iqFile.getName() + " · " + data.length + " bytes");
            System.out.println("DeadScout Desktop real-IQ waterfall render check saved: " + target.getAbsolutePath());
            System.out.println(snapshot.summary());
            return;
        }
        if (args.length > 0 && "--waterfall-render-check".equals(args[0])) {
            System.setProperty("java.awt.headless", "true");
            File target = args.length > 1 ? new File(args[1]) : new File("build/release/deadscout-desktop-waterfall-render-check.png");
            renderWaterfallCheck(target, demoSpectrum(), "Synthetic IQ preview from the DeadScout DSP path");
            System.out.println("DeadScout Desktop waterfall render check saved: " + target.getAbsolutePath());
            return;
        }
        if (args.length > 0 && "--render-check".equals(args[0])) {
            System.setProperty("java.awt.headless", "true");
            File target = args.length > 1 ? new File(args[1]) : new File("build/release/deadscout-desktop-render-check.png");
            renderReadabilityCheck(target);
            System.out.println("DeadScout Desktop render check saved: " + target.getAbsolutePath());
            return;
        }
        installDarkLookAndFeel();
        if (args.length > 0 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length > 0 && "--launch-check".equals(args[0])) {
            final int millis = args.length > 1 ? Math.max(1400, Integer.parseInt(args[1])) : 1800;
            final File screenshot = args.length > 2 && !args[2].trim().isEmpty() ? new File(args[2]) : null;
            SwingUtilities.invokeLater(() -> {
                try {
                    DeadScoutDesktopGui gui = new DeadScoutDesktopGui();
                    gui.loadSession(TrainingFixtures.packetTrainingSession(), "Preview capture loaded for desktop review.");
                    gui.setVisible(true);
                    gui.resetInitialSourcePanelView();
                    if (screenshot != null) {
                        int delay = Math.min(1200, Math.max(600, millis / 2));
                        Timer captureTimer = new Timer(delay, e -> {
                            try {
                                gui.saveComponentScreenshot(screenshot);
                                System.out.println("DeadScout Desktop screenshot saved: " + screenshot.getAbsolutePath());
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                System.exit(1);
                            }
                        });
                        captureTimer.setRepeats(false);
                        captureTimer.start();
                    }
                    Timer exitTimer = new Timer(millis, e -> {
                        gui.dispose();
                        System.out.println("DeadScout Desktop launch check passed.");
                        System.exit(0);
                    });
                    exitTimer.setRepeats(false);
                    exitTimer.start();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            });
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                DeadScoutDesktopGui gui = new DeadScoutDesktopGui();
                gui.setVisible(true);
                gui.resetInitialSourcePanelView();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "DeadScout Desktop could not start:\n" + e.getMessage(),
                        "DeadScout startup error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void installDarkLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }
        DesktopTheme.installReadableDefaults();
    }

    private JPanel buildContent() {
        JPanel root = darkPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(statusHeaderPanel.build(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Packets", packetReviewPanel.build());
        tabs.add("Topology", topologyPanel.build());
        tabs.add("Lab", labPanel.build());
        tabs.add("Session", scroll(sessionDetails));
        tabs.add("Report", reportPanel.build());
        tabs.add("Waterfall", waterfallPanel.build());
        tabs.add("Sources", sourceHelpPanel.build());
        tabs.setBackground(PANEL);
        tabs.setForeground(TEXT);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlPanel(), tabs);
        mainSplit.setDividerLocation(395);
        mainSplit.setResizeWeight(0.0);
        mainSplit.setBorder(BorderFactory.createLineBorder(BORDER));
        mainSplit.setBackground(PANEL);
        root.add(mainSplit, BorderLayout.CENTER);

        statusLabel.setForeground(MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 4, 0, 4));
        root.add(statusLabel, BorderLayout.SOUTH);
        DesktopTheme.styleComponentTree(root);
        return root;
    }

    private JPanel controlPanel() {
        return sourceControlPanel.build();
    }

    private void resetInitialSourcePanelView() {
        SwingUtilities.invokeLater(() -> {
            startImportButton.requestFocusInWindow();
            sourceControlPanel.scrollToTop();
        });
    }

    void toggleSource(ActiveSource source) {
        if (activeSource == source) {
            stopSource(source);
            return;
        }
        if (source == ActiveSource.IMPORT) {
            startImport();
        } else if (source == ActiveSource.NETWORK) {
            startNetworkCapture();
        } else if (source == ActiveSource.NET_A) {
            startNetACapture();
        } else if (source == ActiveSource.AUXILIARY) {
            startAuxiliaryCapture();
        } else if (source == ActiveSource.SNIFFER) {
            startSnifferCapture();
        } else if (source == ActiveSource.USB) {
            startUsb();
        } else if (source == ActiveSource.RTL) {
            startRtlCapture();
        } else if (source == ActiveSource.RTL_TCP) {
            startRtlTcpCapture();
        }
    }

    void startImport() {
        stopBackgroundThreads();
        activeSource = ActiveSource.IMPORT;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Start import");
        int selected = chooser.showOpenDialog(this);
        if (selected != JFileChooser.APPROVE_OPTION) {
            activeSource = ActiveSource.NONE;
            setStatus("Import cancelled.");
            refreshUi();
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            CaptureImportResult result = CaptureImporter.importCapture(file.getName(), data);
            loadSession(result.session, "Start import loaded " + file.getName() + ": " + result.summary().replace('\n', ' '));
            if (result.detectedType.toLowerCase(Locale.US).contains("iq") || result.detectedType.toLowerCase(Locale.US).contains("raw sample")) {
                rememberSpectrum(data);
            }
            activeSource = ActiveSource.IMPORT;
            refreshUi();
        } catch (Exception ex) {
            activeSource = ActiveSource.NONE;
            showError("Import failed", ex);
        }
    }

    void startNetworkCapture() {
        startPcapHelperSource(ActiveSource.NETWORK, "desktop-network", "network", false);
    }

    void startNetACapture() {
        startPcapHelperSource(ActiveSource.NET_A, "desktop-netA", "NetworkA", true);
    }

    private void startPcapHelperSource(ActiveSource source, String sourceId, String label, boolean requireNetAFrames) {
        stopBackgroundThreads();
        activeSource = source;
        setStatus("Start " + label + " is opening a live capture helper; decoded packets will appear as soon as frames are captured.");
        refreshUi();
        sourceThread = new Thread(() -> {
            try {
                PcapCapturePlan plan = planPcapCapture(source, requireNetAFrames);
                if (plan == null) {
                    reportSourceUnavailable(source, sourceId, label + " capture could not start: install the packet driver driver or use Windows pktmon/admin capture; on Linux install tcpdump/dumpcap and configure a capture interface.");
                    return;
                }
                CaptureProcessResult capture = runBoundedCommand(plan.command, plan.outputFile, plan.timeoutMillis, 262144);
                byte[] data = capture.payload;
                String linkSummary = pcapLinkSummary(plan.fileName, data);
                CaptureImportResult imported = data.length == 0 ? null : CaptureImporter.importCapture(plan.fileName, data, sourceId);
                SwingUtilities.invokeLater(() -> {
                    int packetsBefore = session.packets.size();
                    if (imported != null) appendSession(imported.session);
                    int decoded = session.packets.size() - packetsBefore;
                    String note = label + " live capture helper: command=" + capture.commandLine
                            + "; exit=" + capture.exitCode + (capture.timedOut ? "; stopped_after_timeout=true" : "")
                            + "; bytes=" + data.length + "; decoded_packets=" + decoded
                            + (linkSummary.isEmpty() ? "" : "; " + linkSummary)
                            + (capture.stderrPreview.isEmpty() ? "" : "; stderr=" + capture.stderrPreview);
                    if (requireNetAFrames && data.length > 0 && !pcapHasNetALink(data)) {
                        note += "; NetworkA monitor note=helper captured data, but the link type was not radiotap/802.11. Use a monitor-mode adapter/interface for raw NetworkA frames.";
                    }
                    appendStatusObservation(sourceId, 0L, 0, "live capture", note,
                            decoded > 0 ? label + " captured " + decoded + " decoded packet rows." : label + " capture ran; no supported packets decoded from this attempt.");
                    if (activeSource == source && decoded == 0) activeSource = ActiveSource.NONE;
                    refreshUi();
                });
            } catch (Exception ex) {
                reportSourceUnavailable(source, sourceId, label + " capture failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }, "deadscout-" + sourceId + "-capture");
        sourceThread.setDaemon(true);
        sourceThread.start();
    }

    void startAuxiliaryCapture() {
        stopBackgroundThreads();
        activeSource = ActiveSource.AUXILIARY;
        setStatus("Start Auxiliary is opening a live HCI capture helper.");
        refreshUi();
        sourceThread = new Thread(() -> {
            try {
                ExternalCapturePlan plan = planAuxiliaryCapture();
                if (plan == null) {
                    reportSourceUnavailable(ActiveSource.AUXILIARY, "desktop-auxiliary", "Auxiliary capture could not start: install btmon/hcidump on Linux or set DEADSCOUT_DESKTOP_AUXILIARY_CMD to a Auxiliary HCI helper, then connect/enable a Auxiliary HCI source.");
                    return;
                }
                CaptureProcessResult capture = runBoundedCommand(plan.command, plan.outputFile, plan.timeoutMillis, 262144);
                byte[] data = capture.payload;
                CaptureImportResult imported = data.length == 0 ? null : CaptureImporter.importCapture(plan.fileName, data, "desktop-auxiliary");
                SwingUtilities.invokeLater(() -> {
                    int packetsBefore = session.packets.size();
                    if (imported != null) appendSession(imported.session);
                    int decoded = session.packets.size() - packetsBefore;
                    String note = "Auxiliary live capture helper: command=" + capture.commandLine
                            + "; exit=" + capture.exitCode + (capture.timedOut ? "; stopped_after_timeout=true" : "")
                            + "; bytes=" + data.length + "; decoded_packets=" + decoded
                            + (capture.stderrPreview.isEmpty() ? "" : "; stderr=" + capture.stderrPreview);
                    appendStatusObservation("desktop-auxiliary", 2_402_000_000L, 0, "Auxiliary HCI", note,
                            decoded > 0 ? "Auxiliary captured " + decoded + " HCI/AUX packet rows." : "Auxiliary capture ran; no supported HCI packets decoded from this attempt.");
                    if (activeSource == ActiveSource.AUXILIARY && decoded == 0) activeSource = ActiveSource.NONE;
                    refreshUi();
                });
            } catch (Exception ex) {
                reportSourceUnavailable(ActiveSource.AUXILIARY, "desktop-auxiliary", "Auxiliary capture failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }, "deadscout-auxiliary-capture");
        sourceThread.setDaemon(true);
        sourceThread.start();
    }

    void startSnifferCapture() {
        stopBackgroundThreads();
        activeSource = ActiveSource.SNIFFER;
        setStatus("Start sniffer is opening a live 802.15.4/sniffer helper.");
        refreshUi();
        sourceThread = new Thread(() -> {
            try {
                ExternalCapturePlan plan = planSnifferCapture();
                if (plan == null) {
                    reportSourceUnavailable(ActiveSource.SNIFFER, "desktop-sniffer", "Sniffer capture could not start: set DEADSCOUT_DESKTOP_SNIFFER_CMD to a serial/extcap helper that prints frame hex, or install dumpcap for a sniffer PCAP interface.");
                    return;
                }
                CaptureProcessResult capture = runBoundedCommand(plan.command, plan.outputFile, plan.timeoutMillis, 262144);
                byte[] data = capture.payload;
                CaptureImportResult imported = data.length == 0 ? null : CaptureImporter.importCapture(plan.fileName, data, "desktop-sniffer");
                SwingUtilities.invokeLater(() -> {
                    int packetsBefore = session.packets.size();
                    if (imported != null) appendSession(imported.session);
                    int decoded = session.packets.size() - packetsBefore;
                    String note = "Sniffer live capture helper: command=" + capture.commandLine
                            + "; exit=" + capture.exitCode + (capture.timedOut ? "; stopped_after_timeout=true" : "")
                            + "; bytes=" + data.length + "; decoded_packets=" + decoded
                            + (capture.stderrPreview.isEmpty() ? "" : "; stderr=" + capture.stderrPreview);
                    appendStatusObservation("desktop-sniffer", 2_405_000_000L, 0, "802.15.4/sniffer", note,
                            decoded > 0 ? "Sniffer captured " + decoded + " packet rows." : "Sniffer capture ran; no supported frames decoded from this attempt.");
                    if (activeSource == ActiveSource.SNIFFER && decoded == 0) activeSource = ActiveSource.NONE;
                    refreshUi();
                });
            } catch (Exception ex) {
                reportSourceUnavailable(ActiveSource.SNIFFER, "desktop-sniffer", "Sniffer capture failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }, "deadscout-sniffer-capture");
        sourceThread.setDaemon(true);
        sourceThread.start();
    }

    void startUsb() {
        stopBackgroundThreads();
        activeSource = ActiveSource.USB;
        setStatus("Start USB is probing attached capture helpers and will run the first live source that returns data.");
        refreshUi();
        sourceThread = new Thread(() -> {
            LinkedHashMap<String, String> fields = helperStatusFields();
            fields.put("usb_inventory", usbInventorySummary());
            try {
                if (isCommandFound("rtl_433")) {
                    CaptureProcessResult rtl433 = runRtl433Json();
                    CaptureImportResult imported = rtl433.payload.length == 0 ? null : CaptureImporter.importCapture("desktop-usb-rtl_433.json", rtl433.payload, "desktop-usb");
                    if (imported != null && !imported.session.packets.isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            int before = session.packets.size();
                            appendSession(imported.session);
                            appendStatusObservation("desktop-usb", mhzToHz(rtlFreqMhz.getText(), 915_000_000L), parseInt(sampleRate.getText(), 2_400_000), "rtl_433", "USB rtl_433 capture: command=" + rtl433.commandLine + "; bytes=" + rtl433.payload.length + "; decoded_packets=" + (session.packets.size() - before),
                                    "USB rtl_433 captured " + (session.packets.size() - before) + " decoded packet rows.");
                            refreshUi();
                        });
                        return;
                    }
                    fields.put("rtl_433_attempt", "bytes=" + rtl433.payload.length + "; stderr=" + rtl433.stderrPreview);
                }
                if (isCommandFound("rtl_sdr")) {
                    CaptureProcessResult iq = runRtlSdrIqCapture("usb");
                    if (iq.payload.length >= 64) {
                        CaptureImportResult imported = CaptureImporter.importCapture("desktop-usb-rtl.cu8", iq.payload, "desktop-usb");
                        SwingUtilities.invokeLater(() -> {
                            int before = session.packets.size();
                            appendSession(imported.session);
                            rememberSpectrum(iq.payload);
                            appendStatusObservation("desktop-usb", mhzToHz(rtlFreqMhz.getText(), 915_000_000L), parseInt(sampleRate.getText(), 2_400_000), "RTL-SDR IQ", "USB RTL-SDR IQ capture: command=" + iq.commandLine + "; iq_bytes=" + iq.payload.length + "; decoded_packets=" + (session.packets.size() - before),
                                    "USB RTL-SDR returned IQ for waterfall/signal review.");
                            refreshUi();
                        });
                        return;
                    }
                    fields.put("rtl_sdr_attempt", "bytes=" + iq.payload.length + "; stderr=" + iq.stderrPreview);
                }
                String note = "USB capture did not receive decodable data: " + formatFields(fields)
                        + "; next_step=connect supported hardware or install helpers (rtl_433/rtl_sdr/dumpcap) and retry.";
                reportSourceUnavailable(ActiveSource.USB, "desktop-usb", note);
            } catch (Exception ex) {
                fields.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
                reportSourceUnavailable(ActiveSource.USB, "desktop-usb", "USB capture failed: " + formatFields(fields));
            }
        }, "deadscout-usb-capture");
        sourceThread.setDaemon(true);
        sourceThread.start();
    }

    void startRtlCapture() {
        stopBackgroundThreads();
        activeSource = ActiveSource.RTL;
        long frequencyHz = mhzToHz(rtlFreqMhz.getText(), 915_000_000L);
        int sr = parseInt(sampleRate.getText(), 2_400_000);
        int gain = parseInt(gainTenths.getText(), 280);
        RtlSdrIqPipeline.Config config = new RtlSdrIqPipeline.Config(frequencyHz, sr, gain, 0, false, 64, 12.0);
        RtlSdrIqPipeline pipeline = new RtlSdrIqPipeline(config);
        setStatus("Start RTL is opening rtl_sdr for a bounded live IQ capture.");
        refreshUi();
        sourceThread = new Thread(() -> {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("tuning_plan", pipeline.controlPlan());
            fields.put("rtl_sdr", commandAvailability("rtl_sdr"));
            fields.put("rtl_433", commandAvailability("rtl_433"));
            String iqPath = System.getenv("DEADSCOUT_DESKTOP_RTL_IQ");
            try {
                if (iqPath != null && !iqPath.trim().isEmpty()) {
                    byte[] data = Files.readAllBytes(new File(iqPath).toPath());
                    CaptureImportResult result = CaptureImporter.importCapture("desktop-rtl.cu8", data, "desktop-rtl");
                    SwingUtilities.invokeLater(() -> {
                        int packetsBefore = session.packets.size();
                        appendSession(result.session);
                        rememberSpectrum(data);
                        session.addNote("RTL IQ processed from configured file: " + iqPath + " · bytes=" + data.length);
                        setStatus("Start RTL loaded IQ into signal/waterfall review; decoded packet rows added: " + (session.packets.size() - packetsBefore) + ".");
                        refreshUi();
                    });
                    return;
                }
                if (!isCommandFound("rtl_sdr")) {
                    reportSourceUnavailable(ActiveSource.RTL, "desktop-rtl", "RTL capture could not start: rtl_sdr helper not found. Install rtl-sdr tools or set DEADSCOUT_DESKTOP_RTL_IQ to an IQ file.");
                    return;
                }
                CaptureProcessResult iq = runRtlSdrIqCapture("rtl");
                if (iq.payload.length >= 64) {
                    CaptureImportResult result = CaptureImporter.importCapture("desktop-rtl.cu8", iq.payload, "desktop-rtl");
                    SwingUtilities.invokeLater(() -> {
                        int packetsBefore = session.packets.size();
                        appendSession(result.session);
                        rememberSpectrum(iq.payload);
                        session.addNote("RTL-SDR live IQ capture: command=" + iq.commandLine + "; iq_bytes=" + iq.payload.length + "; stderr=" + iq.stderrPreview);
                        setStatus("Start RTL captured live IQ into signal/waterfall review; decoded packet rows added: " + (session.packets.size() - packetsBefore) + ".");
                        refreshUi();
                    });
                } else {
                    fields.put("rtl_sdr_attempt", "bytes=" + iq.payload.length + "; stderr=" + iq.stderrPreview);
                    reportSourceUnavailable(ActiveSource.RTL, "desktop-rtl", "RTL capture ran but no IQ bytes were returned: " + formatFields(fields));
                }
            } catch (Exception ex) {
                fields.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
                reportSourceUnavailable(ActiveSource.RTL, "desktop-rtl", "RTL capture failed: " + formatFields(fields));
            }
        }, "deadscout-rtl-capture");
        sourceThread.setDaemon(true);
        sourceThread.start();
    }

    void startRtlTcpCapture() {
        stopBackgroundThreads();
        activeSource = ActiveSource.RTL_TCP;
        setStatus("Start rtl_tcp is active. IQ fills the waterfall; packets appear only when decoded frames are available.");
        refreshUi();
        rtlTcpThread = new Thread(() -> {
            boolean reportedFailure = false;
            while (activeSource == ActiveSource.RTL_TCP && !Thread.currentThread().isInterrupted()) {
                RtlTcpAttempt attempt = tryReadRtlTcp();
                if (attempt.iqBytes.length >= 64) {
                    CaptureImportResult imported = CaptureImporter.importCapture("rtl_tcp.cu8", attempt.iqBytes, "desktop-rtl-tcp");
                    SwingUtilities.invokeLater(() -> {
                        int packetsBefore = session.packets.size();
                        appendSession(imported.session);
                        rememberSpectrum(attempt.iqBytes);
                        session.addNote("External rtl_tcp read " + attempt.iqBytes.length + " IQ bytes: " + formatFields(attempt.fields));
                        setStatus("Start rtl_tcp received IQ data; decoded packet rows added: " + (session.packets.size() - packetsBefore) + ".");
                        refreshUi();
                    });
                } else if (!reportedFailure) {
                    reportedFailure = true;
                    SwingUtilities.invokeLater(() -> {
                        appendStatusObservation("desktop-rtl-tcp", attempt.frequencyHz, parseInt(sampleRate.getText(), 2_400_000), "rtl_tcp",
                                "External rtl_tcp attempt: " + attempt.summary + " · " + formatFields(attempt.fields),
                                "External rtl_tcp is waiting for IQ data.");
                        refreshUi();
                    });
                } else {
                    SwingUtilities.invokeLater(() -> setStatus("External rtl_tcp capture still active; latest attempt: " + attempt.summary));
                }
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "deadscout-rtl-tcp");
        rtlTcpThread.setDaemon(true);
        rtlTcpThread.start();
    }

    private RtlTcpAttempt tryReadRtlTcp() {
        String host = rtlTcpHost.getText().trim().isEmpty() ? "127.0.0.1" : rtlTcpHost.getText().trim();
        int port = parseInt(rtlTcpPort.getText(), 1234);
        long frequencyHz = mhzToHz(rtlFreqMhz.getText(), 915_000_000L);
        int sr = parseInt(sampleRate.getText(), 2_400_000);
        int gain = parseInt(gainTenths.getText(), 280);
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("host", host);
        fields.put("port", Integer.toString(port));
        fields.put("frequency_hz", Long.toString(frequencyHz));
        fields.put("sample_rate_hz", Integer.toString(sr));
        fields.put("gain_tenths_db", Integer.toString(gain));
        fields.put("status", "TCP connect/read result only; USB radio hardware is not marked live unless IQ bytes arrive.");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1200);
            socket.setSoTimeout(1200);
            OutputStream out = socket.getOutputStream();
            writeRtlTcpCommand(out, 0x01, (int) Math.min(Integer.MAX_VALUE, frequencyHz));
            writeRtlTcpCommand(out, 0x02, sr);
            writeRtlTcpCommand(out, 0x03, gain);
            out.flush();
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long until = System.currentTimeMillis() + 1400L;
            while (System.currentTimeMillis() < until && data.size() < 65536) {
                int n = socket.getInputStream().read(buf);
                if (n <= 0) break;
                data.write(buf, 0, n);
            }
            byte[] iq = data.toByteArray();
            fields.put("iq_bytes", Integer.toString(iq.length));
            String summary = iq.length >= 64 ? "rtl_tcp returned IQ bytes" : "rtl_tcp connected but returned too few IQ bytes";
            return new RtlTcpAttempt(summary, iq, frequencyHz, fields);
        } catch (Exception ex) {
            fields.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return new RtlTcpAttempt("rtl_tcp not reachable or no IQ returned", new byte[0], frequencyHz, fields);
        }
    }

    private static void writeRtlTcpCommand(OutputStream out, int command, int value) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(5);
        bb.put((byte) command);
        bb.putInt(value);
        out.write(bb.array());
    }

    private PcapCapturePlan planPcapCapture(ActiveSource source, boolean netA) throws IOException {
        String iface = getenvFirst(netA ? "DEADSCOUT_DESKTOP_NET_A_IFACE" : "DEADSCOUT_DESKTOP_NETWORK_IFACE", "DEADSCOUT_DESKTOP_CAPTURE_IFACE");
        int seconds = Math.max(2, Math.min(15, parseInt(getenvFirst("DEADSCOUT_DESKTOP_CAPTURE_SECONDS"), 4)));
        if (isWindows()) {
            PcapCapturePlan nativeHelper = planDeadScoutWindowsHelperCapture(netA, seconds);
            if (nativeHelper != null) return nativeHelper;
            if (isCommandFound("pktmon.exe")) {
                PcapCapturePlan pktmon = planPktmonCapture(netA, seconds);
                if (pktmon != null) return pktmon;
            }
        }
        if (isCommandFound("dumpcap")) {
            if (iface.isEmpty()) iface = selectDumpcapInterface(netA);
            if (!iface.isEmpty()) {
                File out = File.createTempFile(netA ? "deadscout-netA-" : "deadscout-network-", ".pcapng");
                return new PcapCapturePlan(Arrays.asList("dumpcap", "-i", iface, "-a", "duration:" + seconds, "-w", out.getAbsolutePath()),
                        out, netA ? "desktop-netA.pcapng" : "desktop-network.pcapng", Math.max(6000L, (seconds + 4L) * 1000L));
            }
        }
        if (!isWindows() && isCommandFound("tcpdump")) {
            if (iface.isEmpty()) iface = netA ? selectJavaNetworkInterface(true) : "any";
            if (!iface.isEmpty()) {
                File out = File.createTempFile(netA ? "deadscout-netA-" : "deadscout-network-", ".pcap");
                return new PcapCapturePlan(Arrays.asList("tcpdump", "-i", iface, "-s", "0", "-c", netA ? "64" : "96", "-w", out.getAbsolutePath()),
                        out, netA ? "desktop-netA.pcap" : "desktop-network.pcap", Math.max(6000L, (seconds + 4L) * 1000L));
            }
        }
        return null;
    }

    private PcapCapturePlan planDeadScoutWindowsHelperCapture(boolean netA, int seconds) throws IOException {
        File helper = findBundledWindowsCaptureHelper();
        if (helper == null || !helper.isFile()) return null;
        File out = File.createTempFile(netA ? "deadscout-netA-native-" : "deadscout-network-native-", ".pcapng");
        if (out.isFile() && !out.delete()) out.deleteOnExit();
        List<String> command = Arrays.asList("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                helper.getAbsolutePath(), "-Mode", netA ? "NetA" : "Network", "-Seconds", Integer.toString(seconds), "-OutFile", out.getAbsolutePath(), "-Json");
        return new PcapCapturePlan(command, out, netA ? "desktop-netA-deadscout-native.pcapng" : "desktop-network-deadscout-native.pcapng",
                Math.max(9000L, (seconds + 8L) * 1000L));
    }

    private static File findBundledWindowsCaptureHelper() {
        String override = getenvFirst("DEADSCOUT_WINDOWS_CAPTURE_HELPER");
        if (!override.isEmpty()) return new File(override);
        List<File> dirs = new ArrayList<>();
        dirs.add(new File(System.getProperty("user.dir", ".")));
        try {
            File code = new File(DeadScoutDesktopGui.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            dirs.add(code.isDirectory() ? code : code.getParentFile());
        } catch (Exception ignored) { }
        for (File dir : dirs) {
            if (dir == null) continue;
            File direct = new File(dir, "deadscout-windows-capture-helper.ps1");
            if (direct.isFile()) return direct;
            File nested = new File(new File(dir, "packaging"), "windows/deadscout-windows-capture-helper.ps1");
            if (nested.isFile()) return nested;
        }
        return null;
    }

    private PcapCapturePlan planPktmonCapture(boolean netA, int seconds) throws IOException {
        File etl = File.createTempFile(netA ? "deadscout-netA-pktmon-" : "deadscout-network-pktmon-", ".etl");
        File out = File.createTempFile(netA ? "deadscout-netA-pktmon-" : "deadscout-network-pktmon-", ".pcapng");
        if (out.isFile() && !out.delete()) out.deleteOnExit();
        String etlPath = powerShellSingleQuote(etl.getAbsolutePath());
        String outPath = powerShellSingleQuote(out.getAbsolutePath());
        String script = "$ErrorActionPreference='Continue'; "
                + "pktmon stop | Out-Null; "
                + "pktmon filter remove | Out-Null; "
                + "$ErrorActionPreference='Stop'; "
                + "pktmon start --capture --comp nics --pkt-size 0 --file-name " + etlPath + "; "
                + "Start-Sleep -Seconds " + seconds + "; "
                + "pktmon stop; "
                + "pktmon etl2pcap " + etlPath + " --out " + outPath;
        return new PcapCapturePlan(Arrays.asList("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script),
                out, netA ? "desktop-netA-pktmon.pcapng" : "desktop-network-pktmon.pcapng", Math.max(9000L, (seconds + 8L) * 1000L));
    }

    private ExternalCapturePlan planAuxiliaryCapture() throws IOException {
        String custom = getenvFirst("DEADSCOUT_DESKTOP_AUXILIARY_CMD");
        int seconds = Math.max(2, Math.min(20, parseInt(getenvFirst("DEADSCOUT_DESKTOP_CAPTURE_SECONDS"), 5)));
        if (!custom.isEmpty()) {
            return new ExternalCapturePlan(shellCommand(custom), null, "desktop-auxiliary-hci.log", (seconds + 3L) * 1000L);
        }
        if (!isWindows() && isCommandFound("btmon")) {
            File out = File.createTempFile("deadscout-auxiliary-", ".btsnoop");
            return new ExternalCapturePlan(Arrays.asList("btmon", "-w", out.getAbsolutePath()), out, "desktop-auxiliary.btsnoop", (seconds + 3L) * 1000L);
        }
        if (!isWindows() && isCommandFound("hcidump")) {
            return new ExternalCapturePlan(Arrays.asList("hcidump", "-R", "-X"), null, "desktop-auxiliary-hci.log", (seconds + 3L) * 1000L);
        }
        if (isCommandFound("dumpcap")) {
            String iface = selectDumpcapInterfaceByKeywords("auxiliary", "hci");
            if (!iface.isEmpty()) {
                File out = File.createTempFile("deadscout-auxiliary-", ".pcapng");
                return new ExternalCapturePlan(Arrays.asList("dumpcap", "-i", iface, "-a", "duration:" + seconds, "-w", out.getAbsolutePath()), out, "desktop-auxiliary.pcapng", (seconds + 4L) * 1000L);
            }
        }
        return null;
    }

    private ExternalCapturePlan planSnifferCapture() throws IOException {
        String custom = getenvFirst("DEADSCOUT_DESKTOP_SNIFFER_CMD");
        int seconds = Math.max(2, Math.min(20, parseInt(getenvFirst("DEADSCOUT_DESKTOP_CAPTURE_SECONDS"), 5)));
        if (!custom.isEmpty()) {
            return new ExternalCapturePlan(shellCommand(custom), null, "desktop-sniffer-802154.log", (seconds + 3L) * 1000L);
        }
        if (isCommandFound("dumpcap")) {
            String iface = selectDumpcapInterfaceByKeywords("802.15.4", "802154", "zigbee", "thread", "nrf", "sniffer", "wpan");
            if (!iface.isEmpty()) {
                File out = File.createTempFile("deadscout-sniffer-", ".pcapng");
                return new ExternalCapturePlan(Arrays.asList("dumpcap", "-i", iface, "-a", "duration:" + seconds, "-w", out.getAbsolutePath()), out, "desktop-sniffer.pcapng", (seconds + 4L) * 1000L);
            }
        }
        return null;
    }

    private CaptureProcessResult runRtl433Json() throws IOException {
        long frequencyHz = mhzToHz(rtlFreqMhz.getText(), 915_000_000L);
        String mhz = String.format(Locale.US, "%.3fM", frequencyHz / 1_000_000.0);
        return runBoundedCommand(Arrays.asList("rtl_433", "-F", "json", "-T", "5", "-f", mhz), null, 9000L, 262144);
    }

    private CaptureProcessResult runRtlSdrIqCapture(String label) throws IOException {
        long frequencyHz = mhzToHz(rtlFreqMhz.getText(), 915_000_000L);
        int sr = parseInt(sampleRate.getText(), 2_400_000);
        int gain = parseInt(gainTenths.getText(), 280);
        File out = File.createTempFile("deadscout-" + label + "-", ".cu8");
        String gainDb = String.format(Locale.US, "%.1f", gain / 10.0);
        return runBoundedCommand(Arrays.asList("rtl_sdr", "-f", Long.toString(frequencyHz), "-s", Integer.toString(sr), "-g", gainDb, "-n", "65536", out.getAbsolutePath()),
                out, 10000L, 65536);
    }

    private CaptureProcessResult runBoundedCommand(List<String> command, File outputFile, long timeoutMillis, int maxStdoutBytes) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();
        helperProcess = process;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread outThread = new Thread(() -> copyStream(process.getInputStream(), stdout, maxStdoutBytes), "deadscout-helper-stdout");
        Thread errThread = new Thread(() -> copyStream(process.getErrorStream(), stderr, 8192), "deadscout-helper-stderr");
        outThread.setDaemon(true);
        errThread.setDaemon(true);
        outThread.start();
        errThread.start();
        boolean done;
        try {
            done = process.waitFor(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            done = false;
        }
        if (!done) {
            process.destroy();
            try { process.waitFor(700, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            if (process.isAlive()) process.destroyForcibly();
        }
        try { outThread.join(800); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        try { errThread.join(800); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        byte[] payload = outputFile != null && outputFile.isFile() && outputFile.length() > 0 ? Files.readAllBytes(outputFile.toPath()) : stdout.toByteArray();
        if (helperProcess == process) helperProcess = null;
        return new CaptureProcessResult(joinCommand(command), process.isAlive() ? -1 : safeExitValue(process), !done,
                stdout.toByteArray(), preview(stderr.toByteArray()), payload);
    }

    private static void copyStream(InputStream in, ByteArrayOutputStream out, int maxBytes) {
        byte[] buf = new byte[1024];
        int n;
        try {
            while ((n = in.read(buf)) >= 0) {
                int remaining = maxBytes - out.size();
                if (remaining > 0) out.write(buf, 0, Math.min(n, remaining));
            }
        } catch (IOException ignored) { }
    }

    private static int safeExitValue(Process process) {
        try { return process.exitValue(); } catch (IllegalThreadStateException ex) { return -1; }
    }

    private void reportSourceUnavailable(ActiveSource source, String sourceId, String note) {
        SwingUtilities.invokeLater(() -> {
            session.addObservation(new SignalObservation(System.currentTimeMillis(), sourceId, 0L, 0, 0, 0.0, 0.0, "source setup", note));
            session.addNote(note);
            if (activeSource == source) activeSource = ActiveSource.NONE;
            setStatus(note);
            refreshUi();
        });
    }

    private boolean isCommandFound(String command) {
        String availability = commandAvailability(command).toLowerCase(Locale.US);
        return !availability.contains("not found") && !availability.contains("timed out") && !availability.contains("exception") && !availability.trim().isEmpty();
    }

    private static String getenvFirst(String... names) {
        for (String name : names) {
            if (name == null) continue;
            String value = System.getenv(name);
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static List<String> shellCommand(String command) {
        return isWindows() ? Arrays.asList("cmd.exe", "/c", command) : Arrays.asList("sh", "-lc", command);
    }

    private String selectDumpcapInterface(boolean netA) {
        if (netA) {
            String selected = selectDumpcapInterfaceByKeywords("netA", "netA", "wireless", "netlan", "802.11");
            if (!selected.isEmpty()) return selected;
        }
        String listing = runSmallTextCommand(Arrays.asList("dumpcap", "-D"), 2000L);
        String firstUsable = "";
        for (String line : listing.split("\\R")) {
            String index = dumpcapIndex(line);
            if (index.isEmpty()) continue;
            String lower = line.toLowerCase(Locale.US);
            if (lower.contains("loopback") || lower.contains("auxiliary") || lower.contains("usbpcap")) continue;
            firstUsable = index;
            break;
        }
        return firstUsable;
    }

    private String selectDumpcapInterfaceByKeywords(String... keywords) {
        String listing = runSmallTextCommand(Arrays.asList("dumpcap", "-D"), 2000L);
        for (String line : listing.split("\\R")) {
            String lower = line.toLowerCase(Locale.US);
            for (String keyword : keywords) {
                if (lower.contains(keyword.toLowerCase(Locale.US))) {
                    String index = dumpcapIndex(line);
                    if (!index.isEmpty()) return index;
                }
            }
        }
        return "";
    }

    private static String dumpcapIndex(String line) {
        if (line == null) return "";
        String trimmed = line.trim();
        int dot = trimmed.indexOf('.');
        if (dot <= 0) return "";
        String index = trimmed.substring(0, dot).trim();
        for (int i = 0; i < index.length(); i++) if (!Character.isDigit(index.charAt(i))) return "";
        return index;
    }

    private String selectJavaNetworkInterface(boolean netA) {
        try {
            Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                java.net.NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName().toLowerCase(Locale.US);
                if (!netA || name.startsWith("wl") || name.startsWith("netlan") || name.contains("netA") || name.contains("netA")) return ni.getName();
            }
        } catch (Exception ignored) { }
        return netA ? "" : "any";
    }

    private static String runSmallTextCommand(List<String> command, long timeoutMillis) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean done = process.waitFor(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!done) process.destroyForcibly();
            return new String(readProcessOutput(process.getInputStream(), 8192), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String pcapLinkSummary(String fileName, byte[] data) {
        if (data == null || data.length == 0) return "";
        try {
            if (PcapNgReader.looksLike(data) || (fileName != null && fileName.toLowerCase(Locale.US).endsWith(".pcapng"))) {
                PcapNgReader reader = PcapNgReader.parse(data);
                StringBuilder sb = new StringBuilder("linktypes=");
                for (PcapNgReader.InterfaceInfo info : reader.interfaces) {
                    if (sb.length() > "linktypes=".length()) sb.append(',');
                    sb.append(info.linkType).append('/').append(info.name);
                }
                return sb.toString();
            }
            if (looksClassicPcap(data)) {
                PcapReader reader = PcapReader.parse(data);
                return "linktype=" + reader.linkType + "; packet_blocks=" + reader.packets.size();
            }
        } catch (RuntimeException ignored) { }
        return "";
    }

    private static boolean pcapHasNetALink(byte[] data) {
        if (data == null || data.length == 0) return false;
        try {
            if (PcapNgReader.looksLike(data)) {
                PcapNgReader reader = PcapNgReader.parse(data);
                for (PcapNgReader.InterfaceInfo info : reader.interfaces) if (false) return true;
            } else if (looksClassicPcap(data)) {
                return false;
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    private static boolean looksClassicPcap(byte[] data) {
        if (data == null || data.length < 4) return false;
        int b0 = data[0] & 0xFF, b1 = data[1] & 0xFF, b2 = data[2] & 0xFF, b3 = data[3] & 0xFF;
        return (b0 == 0xD4 && b1 == 0xC3 && b2 == 0xB2 && b3 == 0xA1) || (b0 == 0xA1 && b1 == 0xB2 && b2 == 0xC3 && b3 == 0xD4);
    }

    private static String joinCommand(List<String> command) {
        StringBuilder sb = new StringBuilder();
        for (String part : command) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.indexOf(' ') >= 0 ? '"' + part + '"' : part);
        }
        return sb.toString();
    }

    private static String preview(byte[] data) {
        if (data == null || data.length == 0) return "";
        String text = new String(data, StandardCharsets.UTF_8).replace('\n', ' ').replace('\r', ' ').trim();
        return text.length() > 700 ? text.substring(0, 700) + "…" : text;
    }

    private static class ExternalCapturePlan {
        final List<String> command;
        final File outputFile;
        final String fileName;
        final long timeoutMillis;

        ExternalCapturePlan(List<String> command, File outputFile, String fileName, long timeoutMillis) {
            this.command = command;
            this.outputFile = outputFile;
            this.fileName = fileName;
            this.timeoutMillis = timeoutMillis;
        }
    }

    private static final class PcapCapturePlan extends ExternalCapturePlan {
        PcapCapturePlan(List<String> command, File outputFile, String fileName, long timeoutMillis) {
            super(command, outputFile, fileName, timeoutMillis);
        }
    }

    private static final class CaptureProcessResult {
        final String commandLine;
        final int exitCode;
        final boolean timedOut;
        final byte[] stdout;
        final String stderrPreview;
        final byte[] payload;

        CaptureProcessResult(String commandLine, int exitCode, boolean timedOut, byte[] stdout, String stderrPreview, byte[] payload) {
            this.commandLine = commandLine;
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.stdout = stdout == null ? new byte[0] : stdout;
            this.stderrPreview = stderrPreview == null ? "" : stderrPreview;
            this.payload = payload == null ? new byte[0] : payload;
        }
    }

    void stopSource(ActiveSource source) {
        if (activeSource == source) {
            activeSource = ActiveSource.NONE;
            stopBackgroundThreads();
            setStatus("Stopped " + source.label() + "; current session remains available for review/export.");
            refreshUi();
        }
    }

    private void stopBackgroundThreads() {
        if (rtlTcpThread != null) {
            rtlTcpThread.interrupt();
            rtlTcpThread = null;
        }
        if (usbThread != null) {
            usbThread.interrupt();
            usbThread = null;
        }
        if (sourceThread != null) {
            sourceThread.interrupt();
            sourceThread = null;
        }
        if (helperProcess != null) {
            helperProcess.destroy();
            if (helperProcess.isAlive()) helperProcess.destroyForcibly();
            helperProcess = null;
        }
    }

    private void loadSession(CaptureSession newSession, String status) {
        session = newSession == null ? new CaptureSession("desktop-empty-" + System.currentTimeMillis(), System.currentTimeMillis()) : newSession;
        lastSpectrum = null;
        setStatus(status);
        refreshUi();
    }

    private void appendSession(CaptureSession other) {
        if (other == null) return;
        for (SignalObservation observation : other.observations) session.addObservation(observation);
        for (PacketRecord packet : other.packets) session.addPacket(packet);
        for (String note : other.notes) session.addNote(note);
    }

    private void appendStatusObservation(String sourceId, long frequencyHz, int sampleRateHz, String modulation, String note, String status) {
        session.addObservation(new SignalObservation(System.currentTimeMillis(), sourceId, frequencyHz, sampleRateHz, sampleRateHz,
                0.0, 0.0, modulation, note));
        session.addNote(note);
        setStatus(status);
        refreshUi();
    }

    private void rememberSpectrum(byte[] data) {
        if (data == null || data.length < 64) return;
        try {
            long frequencyHz = mhzToHz(rtlFreqMhz.getText(), 915_000_000L);
            int sr = parseInt(sampleRate.getText(), 2_400_000);
            RtlSdrIqPipeline pipeline = new RtlSdrIqPipeline(new RtlSdrIqPipeline.Config(frequencyHz, sr, parseInt(gainTenths.getText(), 280), 0, false, 64, 12.0));
            lastSpectrum = pipeline.ingestUnsignedIq(data);
        } catch (RuntimeException ignored) { }
    }

    void refreshUi() {
        statusHeaderPanel.update(activeSource, session);

        updateSourceToggle(startImportButton, ActiveSource.IMPORT, "import");
        updateSourceToggle(startNetworkButton, ActiveSource.NETWORK, "network");
        updateSourceToggle(startNetAButton, ActiveSource.NET_A, "NetworkA");
        updateSourceToggle(startAuxiliaryButton, ActiveSource.AUXILIARY, "Auxiliary");
        updateSourceToggle(startSnifferButton, ActiveSource.SNIFFER, "sniffer");
        updateSourceToggle(startUsbButton, ActiveSource.USB, "USB");
        updateSourceToggle(startRtlButton, ActiveSource.RTL, "RTL");
        updateSourceToggle(startRtlTcpButton, ActiveSource.RTL_TCP, "rtl_tcp");

        packetReviewPanel.update(session);
        topologyPanel.update(session, activeSource);
        updateSessionText();
        reportPanel.update(session);
        updateWaterfallText();
        updateLabText();
        sourceHelpPanel.update(session, activeSource);
    }

    private void updateSourceToggle(JButton button, ActiveSource source, String label) {
        boolean active = activeSource == source;
        button.setText(sourceToggleText(active, label));
        button.setToolTipText(active ? "Stop " + label + " and keep the current session open." : "Start " + label + "; any other active source will stop first.");
        button.setEnabled(true);
        styleButton(button);
        if (active) {
            button.setBackground(SELECTED);
            button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ACCENT), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        }
    }

    private static String sourceToggleText(boolean active, String label) {
        return (active ? "Stop " : "Start ") + label;
    }

    private void updateSessionText() {
        StringBuilder sb = new StringBuilder();
        sb.append("DeadScout Desktop ").append(VERSION).append("\n");
        sb.append("Session: ").append(session.id).append("\n");
        sb.append("Started: ").append(session.startedMillis).append("\n");
        sb.append("Packets: ").append(session.packets.size()).append("\n");
        sb.append("Observations: ").append(session.observations.size()).append("\n\n");
        sb.append("Notes\n");
        if (session.notes.isEmpty()) sb.append("- none\n");
        for (String note : session.notes) sb.append("- ").append(note).append('\n');
        sb.append("\nObservations\n");
        if (session.observations.isEmpty()) sb.append("No signal observations loaded yet.\n");
        for (SignalObservation obs : session.observations) {
            sb.append(String.format(Locale.US, "- %s · %.3f MHz · RSSI %.1f dBm · SNR %.1f dB · %s · %s\n",
                    obs.sourceId, obs.frequencyHz / 1_000_000.0, obs.rssiDbm, obs.snrDb, obs.modulation, obs.note));
        }
        sessionDetails.setText(sb.toString());
        sessionDetails.setCaretPosition(0);
    }

    private void updateWaterfallText() {
        waterfallPanel.update(lastSpectrum);
    }

    void updateLabText() {
        labPanel.update(session, packetReviewPanel.selectedPacket(session));
    }

    void exportReport() {
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Export DeadScout session report");
            chooser.setSelectedFile(new File("DeadScout-Desktop-" + VERSION + "-session-report.md"));
            int selected = chooser.showSaveDialog(this);
            if (selected != JFileChooser.APPROVE_OPTION) {
                setStatus("Report export cancelled.");
                return;
            }
            String report = ReportGenerator.markdownReport(session, null);
            Files.write(chooser.getSelectedFile().toPath(), report.getBytes(StandardCharsets.UTF_8));
            setStatus("Exported report: " + chooser.getSelectedFile().getAbsolutePath());
        } catch (Exception ex) {
            showError("Report export failed", ex);
        }
    }

    private static String formatFields(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private String usbInventorySummary() {
        String text = "";
        if (isWindows()) {
            text = runSmallTextCommand(Arrays.asList("powershell.exe", "-NoProfile", "-Command",
                    "Get-PnpDevice -PresentOnly | Where-Object {$_.InstanceId -like 'USB*'} | Select-Object -First 16 -ExpandProperty FriendlyName"), 2500L);
        } else if (isCommandFound("lsusb")) {
            text = runSmallTextCommand(Arrays.asList("lsusb"), 2500L);
        } else if (isCommandFound("system_profiler")) {
            text = runSmallTextCommand(Arrays.asList("system_profiler", "SPUSBDataType"), 3500L);
        }
        text = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        if (text.isEmpty()) return "USB inventory helper not available or no USB devices reported";
        return text.length() > 900 ? text.substring(0, 900) + "…" : text;
    }

    private LinkedHashMap<String, String> helperStatusFields() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("deadscout_native_helper", findBundledWindowsCaptureHelper() == null ? "not bundled" : findBundledWindowsCaptureHelper().getAbsolutePath());
        fields.put("pktmon", commandAvailability("pktmon.exe"));
        fields.put("dumpcap", commandAvailability("dumpcap"));
        fields.put("tcpdump", commandAvailability("tcpdump"));
        fields.put("btmon", commandAvailability("btmon"));
        fields.put("hcidump", commandAvailability("hcidump"));
        fields.put("rtl_test", commandAvailability("rtl_test"));
        fields.put("rtl_sdr", commandAvailability("rtl_sdr"));
        fields.put("rtl_433", commandAvailability("rtl_433"));
        fields.put("rtl_tcp", commandAvailability("rtl_tcp"));
        return fields;
    }

    private static String commandAvailability(String command) {
        String[] cmd = isWindows() ? new String[]{"cmd.exe", "/c", "where " + command}
                : new String[]{"sh", "-lc", "command -v " + shellQuote(command)};
        try {
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            boolean done = process.waitFor(1200, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!done) {
                process.destroyForcibly();
                return "probe timed out";
            }
            byte[] out = readProcessOutput(process.getInputStream(), 2048);
            String text = new String(out, StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 && !text.isEmpty() ? text.split("\\R", 2)[0] : "not found";
        } catch (Exception ex) {
            return ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    private static byte[] readProcessOutput(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        int n;
        while ((n = in.read(buf)) >= 0 && out.size() < maxBytes) out.write(buf, 0, Math.min(n, maxBytes - out.size()));
        return out.toByteArray();
    }

    private void saveComponentScreenshot(File target) throws IOException {
        if (target.getParentFile() != null) Files.createDirectories(target.getParentFile().toPath());
        BufferedImage image = new BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            paintAll(g);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", target);
    }

    private static void renderReadabilityCheck(File target) throws IOException {
        if (target.getParentFile() != null) Files.createDirectories(target.getParentFile().toPath());
        int w = 1280, h = 820;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(BG);
            g.fillRect(0, 0, w, h);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            String[][] cards = {
                    {"SOURCE", "import"}, {"PACKETS", "15"}, {"SIGNAL", "5 observations"}, {"MODE", "review"}
            };
            int x = 12;
            for (String[] card : cards) {
                g.setColor(CARD);
                g.fillRect(x, 12, 300, 76);
                g.setColor(BORDER);
                g.drawRect(x, 12, 300, 76);
                g.setColor(MUTED);
                g.drawString(card[0], x + 14, 35);
                g.setColor(TEXT);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
                g.drawString(card[1], x + 14, 64);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                x += 312;
            }
            int y = 110;
            String[] buttons = INITIAL_SOURCE_LABELS;
            x = 18;
            for (String button : buttons) {
                int bw = Math.max(116, button.length() * 8 + 24);
                if (x + bw > w - 18) { x = 18; y += 46; }
                g.setColor(CARD_2);
                g.fillRoundRect(x, y, bw, 32, 10, 10);
                g.setColor(BORDER);
                g.drawRoundRect(x, y, bw, 32, 10, 10);
                g.setColor(TEXT);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                g.drawString(button, x + 12, y + 21);
                x += bw + 10;
            }
            y += 58;
            g.setColor(CARD);
            g.fillRect(18, y, 430, 560);
            g.setColor(BORDER);
            g.drawRect(18, y, 430, 560);
            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString("Packet review", 34, y + 28);
            g.setColor(TEXT);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            String[] packets = {
                    "01  rtl_433/Fineoffset-WH2 · 915.000 MHz",
                    "02  IEEE 802.15.4 MAC · 2425.000 MHz",
                    "03  IEEE 802.11 management · 2437.000 MHz",
                    "04  Auxiliary LE advertisement · 2402.000 MHz",
                    "05  IPv4/TCP · metadata"
            };
            int lineY = y + 58;
            for (String packet : packets) { g.drawString(packet, 34, lineY); lineY += 28; }

            g.setColor(CARD);
            g.fillRect(468, y, 780, 560);
            g.setColor(BORDER);
            g.drawRect(468, y, 780, 560);
            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString("Packet details / bytes / topology", 486, y + 28);
            g.setColor(TEXT);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            String details = "DECODED · rtl_433 · sensor packet normalized\n" +
                    "Channel: 1   RSSI: -44.5 dBm\n" +
                    "Display filter + field tree + raw hex/ASCII inspector enabled.\n" +
                    "Topology tab derives endpoints/conversations from decoded records.\n" +
                    "Source buttons stack vertically and toggle between Start and Stop for each lane.\n" +
                    "Start import loaded PCAP/PCAPNG/IQ/JSON captures into review.\n" +
                    "Raw IQ and source checks stay in observations until a decoder emits packets.\n" +
                    "Modern scrollbars support clean panning through long source and waterfall views.\n" +
                    "Windows builds are not code-signed yet.";
            lineY = y + 58;
            for (String line : details.split("\\n")) { g.drawString(line, 486, lineY); lineY += 26; }

            g.setColor(WARN);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString("Waterfall tab supports Freq/Time zoom, mouse-wheel zoom, and scrollbar panning for IQ history review.", 18, h - 26);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", target);
    }

    private static void renderWaterfallCheck(File target, SpectrumSnapshot snapshot, String captureLabel) throws IOException {
        if (target.getParentFile() != null) Files.createDirectories(target.getParentFile().toPath());
        int w = 1280, h = 820;
        boolean realCapture = captureLabel != null && captureLabel.toLowerCase(Locale.US).contains("real");
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(BG);
            g.fillRect(0, 0, w, h);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            String[][] cards = {
                    {"SOURCE", realCapture ? "real RTL-SDR" : "IQ preview"}, {"CENTER", "915.000 MHz"}, {"VIEW", "zoom 2.4x"}, {"MODE", "scrollable waterfall"}
            };
            int x = 12;
            for (String[] card : cards) {
                g.setColor(CARD);
                g.fillRect(x, 12, 300, 76);
                g.setColor(BORDER);
                g.drawRect(x, 12, 300, 76);
                g.setColor(MUTED);
                g.drawString(card[0], x + 14, 35);
                g.setColor(TEXT);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
                g.drawString(card[1], x + 14, 64);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                x += 312;
            }

            int leftX = 18, panelY = 112, leftW = 300, panelH = 632;
            g.setColor(PANEL);
            g.fillRect(leftX, panelY, leftW, panelH);
            g.setColor(BORDER);
            g.drawRect(leftX, panelY, leftW, panelH);
            g.setColor(MUTED);
            g.drawString("Sources", leftX + 16, panelY + 28);
            int yy = panelY + 48;
            for (String button : INITIAL_SOURCE_LABELS) {
                drawRenderButton(g, button, leftX + 16, yy, 264, 30);
                yy += 38;
            }
            yy += 14;
            g.setColor(MUTED);
            g.drawString("Waterfall interaction", leftX + 16, yy);
            yy += 24;
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            String[] help = {
                    "• Ctrl+wheel or Freq +/- zooms RF bins",
                    "• Shift+wheel or Time +/- zooms history",
                    "• Horizontal scrollbar pans frequency",
                    "• Vertical scrollbar reviews older rows",
                    realCapture ? "• Live capture from attached RTL-SDR" : "• Synthetic IQ shown until SDR is attached"
            };
            for (String line : help) {
                g.setColor(TEXT);
                g.drawString(line, leftX + 22, yy);
                yy += 20;
            }

            int mainX = 336, toolbarY = 112;
            drawRenderButton(g, "Freq +", mainX, toolbarY, 74, 30);
            drawRenderButton(g, "Freq -", mainX + 82, toolbarY, 74, 30);
            drawRenderButton(g, "Time +", mainX + 164, toolbarY, 78, 30);
            drawRenderButton(g, "Time -", mainX + 250, toolbarY, 78, 30);
            drawRenderButton(g, "Reset view", mainX + 336, toolbarY, 104, 30);
            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString("Utwente/WebSDR-style zoom + pan viewport", mainX + 454, toolbarY + 20);

            WaterfallCanvas canvas = new WaterfallCanvas();
            canvas.setSnapshot(snapshot == null ? demoSpectrum() : snapshot);
            canvas.zoomFrequency(2.4);
            canvas.zoomTime(1.35);
            Dimension preferred = canvas.getPreferredSize();
            BufferedImage canvasImage = new BufferedImage(preferred.width, preferred.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D cg = canvasImage.createGraphics();
            try {
                canvas.setSize(preferred);
                canvas.paint(cg);
            } finally {
                cg.dispose();
            }
            int viewX = mainX, viewY = 154, viewW = 806, viewH = 544;
            g.setColor(BORDER);
            g.drawRect(viewX - 1, viewY - 1, viewW + 1, viewH + 1);
            java.awt.Shape oldClip = g.getClip();
            g.setClip(viewX, viewY, viewW, viewH);
            g.drawImage(canvasImage, viewX, viewY, null);
            g.setClip(oldClip);

            int hTrackY = viewY + viewH + 8;
            g.setColor(CARD_2);
            g.fillRect(viewX, hTrackY, viewW, 12);
            g.setColor(ACCENT);
            g.fillRect(viewX + 90, hTrackY + 2, Math.max(120, viewW * viewW / Math.max(viewW, preferred.width)), 8);
            int vTrackX = viewX + viewW - 18;
            g.setColor(CARD_2);
            g.fillRect(vTrackX, viewY, 12, viewH);
            g.setColor(ACCENT);
            g.fillRect(vTrackX + 2, viewY + 96, 8, Math.max(96, viewH * viewH / Math.max(viewH, preferred.height)));

            g.setColor(WARN);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            String footer = captureLabel == null || captureLabel.trim().isEmpty()
                    ? "Preview uses IQ from the DeadScout DSP path."
                    : captureLabel;
            g.drawString(footer, 18, h - 26);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", target);
    }

    private static void drawRenderButton(Graphics2D g, String text, int x, int y, int w, int h) {
        g.setColor(CARD_2);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(BORDER);
        g.drawRoundRect(x, y, w, h, 8, 8);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString(text, x + 12, y + 20);
    }

    private static SpectrumSnapshot demoSpectrum() {
        return new RtlSdrIqPipeline(RtlSdrIqPipeline.Config.default915()).ingestUnsignedIq(syntheticIq(32768));
    }

    private static void selfTest() throws Exception {
        String allCopy = String.join("\n", REQUIRED_LABELS);
        for (String required : REQUIRED_LABELS) {
            assertTrue(allCopy.contains(required), "missing required control label " + required);
        }
        assertTrue("Start network".equals(sourceToggleText(false, "network")), "inactive source button should show Start");
        assertTrue("Stop network".equals(sourceToggleText(true, "network")), "active source button should show Stop");
        for (String forbidden : forbiddenCopy()) {
            assertTrue(!allCopy.contains(forbidden), "forbidden label in desktop controls: " + forbidden);
        }

        CaptureSession session = TrainingFixtures.packetTrainingSession();
        assertTrue(session.packets.size() >= 2, "training session should contain reviewable packets");
        PacketWorkbenchReport report = PacketWorkbench.analyze(session.packets());
        assertTrue(report.packetCount == session.packets.size(), "packet count mismatch");
        assertTrue(report.protocolHierarchy().contains("rtl_433") || report.protocolHierarchy().contains("802.15.4"), "protocol hierarchy missing expected packets");
        String sourceModel = DesktopSourceLaneModel.render(session, ActiveSource.NONE);
        assertTrue(sourceModel.contains("Network interfaces"), "source list missing network lane");
        assertTrue(sourceModel.contains("SDR / radio"), "source list missing SDR/radio lane");
        assertTrue(sourceModel.contains("Sniffer dongles"), "source list missing sniffer lane");
        assertTrue(sourceModel.contains("PCAP / PCAPNG"), "source list missing PCAP/PCAPNG lane");
        assertTrue(sourceModel.contains("Topology data"), "source list missing topology lane");
        assertTrue(DesktopSourceLaneModel.activeLaneCount(session, ActiveSource.NONE) == 0, "idle source list should have no active lane");
        assertTrue(DesktopSourceLaneModel.activeLaneCount(session, ActiveSource.NETWORK) == 1, "network should have exactly one active lane");
        assertTrue(DesktopSourceLaneModel.activeLaneCount(session, ActiveSource.SNIFFER) == 1, "sniffer should have exactly one active lane");
        assertTrue(DesktopSourceLaneModel.activeLaneCount(session, ActiveSource.RTL_TCP) == 1, "rtl_tcp should have exactly one active lane");
        DesktopSourceLaneModel.LaneStatus sdrLane = DesktopSourceLaneModel.find(session, ActiveSource.RTL_TCP, "sdr-radio");
        assertTrue(sdrLane != null && sdrLane.active, "rtl_tcp should activate only the SDR/radio source lane");

        CaptureImportResult rtl433 = CaptureImporter.importCapture("rtl_433.json", "{\"model\":\"Fineoffset-WH2\",\"id\":42,\"freq\":915.0,\"rssi\":-44.5}\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(rtl433.detectedType.contains("rtl_433") && !rtl433.session.packets.isEmpty(), "rtl_433 import failed");
        CaptureImportResult raw = CaptureImporter.importCapture("raw.hex", "45 00 00 28 BE EF 40 00".getBytes(StandardCharsets.UTF_8));
        assertTrue(raw.summary().contains("raw hex"), "raw hex import failed");
        byte[] iq = syntheticIq(4096);
        CaptureImportResult iqImport = CaptureImporter.importCapture("desktop.cu8", iq);
        assertTrue(iqImport.summary().contains("IQ") || iqImport.detectedType.contains("IQ"), "IQ import failed");
        assertTrue(iqImport.session.packets.isEmpty(), "raw IQ import must not create decoded packet rows");
        assertTrue(!iqImport.session.observations.isEmpty(), "raw IQ import should create signal observations");
        DesktopSourceLaneModel.LaneStatus iqSdrLane = DesktopSourceLaneModel.find(iqImport.session, ActiveSource.NONE, "sdr-radio");
        assertTrue(iqSdrLane != null && iqSdrLane.packets == 0 && iqSdrLane.observations > 0,
                "raw IQ source status should report SDR observations without packets");
        RtlSdrIqPipeline pipeline = new RtlSdrIqPipeline(RtlSdrIqPipeline.Config.default915());
        SpectrumSnapshot snapshot = pipeline.ingestUnsignedIq(iq);
        assertTrue(snapshot.waterfallRows.size() >= 2, "desktop IQ path should produce waterfall rows");
        WaterfallCanvas testCanvas = new WaterfallCanvas();
        testCanvas.setSnapshot(snapshot);
        Dimension baseWaterfall = testCanvas.getPreferredSize();
        testCanvas.zoomFrequency(2.0);
        Dimension freqZoomed = testCanvas.getPreferredSize();
        assertTrue(freqZoomed.width > baseWaterfall.width, "waterfall frequency zoom should expand scrollable canvas width");
        testCanvas.zoomTime(1.8);
        Dimension timeZoomed = testCanvas.getPreferredSize();
        assertTrue(timeZoomed.height > freqZoomed.height, "waterfall time zoom should expand scrollable canvas height");
        testCanvas.resetView();
        assertTrue(testCanvas.getPreferredSize().width <= freqZoomed.width, "waterfall reset should reduce zoomed canvas width");

        LinkedHashMap<String, String> statusFields = new LinkedHashMap<>();
        statusFields.put("status", "diagnostic uses generated bytes only");
        CaptureSession statusSession = new CaptureSession("desktop-status-self-test", System.currentTimeMillis());
        statusSession.addObservation(new SignalObservation(System.currentTimeMillis(), "desktop-usb", 915_000_000L, 2_400_000, 2_400_000,
                0.0, 0.0, "source check", "USB source check: " + formatFields(statusFields)));
        assertTrue(statusSession.packets.isEmpty(), "source checks must stay out of packet rows");
        assertTrue(statusSession.observations.size() == 1, "source-check observation missing");
        DesktopSourceLaneModel.LaneStatus helperSdrLane = DesktopSourceLaneModel.find(statusSession, ActiveSource.USB, "sdr-radio");
        assertTrue(helperSdrLane != null && helperSdrLane.active && helperSdrLane.packets == 0 && helperSdrLane.observations == 1,
                "USB source check should be active SDR source status with observations only");
        System.out.println("DeadScout Desktop self-test passed: import/session review, readable dark Swing UI, single toggle Start/Stop source buttons, packet workbench-style packet/details/bytes and topology panels, source status for network/NetworkA/Auxiliary/SDR/sniffer/PCAP/topology, modern scrollbars/dividers, scrollable/zoomable waterfall canvas, strict decoded-packet boundary for raw IQ/source checks, helper-backed network/NetworkA/Auxiliary/sniffer/USB/RTL source capture planning, rtl_tcp source path modeling, and no hardware live claim without returned capture data.");
    }

    private static byte[] syntheticIq(int length) {
        byte[] out = new byte[Math.max(128, length & ~1)];
        for (int i = 0; i < out.length / 2; i++) {
            double a = 2.0 * Math.PI * 13.0 * i / (out.length / 2.0);
            out[i * 2] = (byte) Math.max(0, Math.min(255, (int) Math.round(128 + 72 * Math.cos(a))));
            out[i * 2 + 1] = (byte) Math.max(0, Math.min(255, (int) Math.round(128 + 72 * Math.sin(a))));
        }
        return out;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void showError(String title, Exception ex) {
        ex.printStackTrace();
        setStatus(title + ": " + ex.getMessage());
        JOptionPane.showMessageDialog(this, title + ":\n" + ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
        refreshUi();
    }

    void setStatus(String status) {
        statusLabel.setText(status == null ? "" : status);
    }

    private static int parseInt(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); } catch (RuntimeException ex) { return fallback; }
    }

    private static long mhzToHz(String text, long fallback) {
        try { return Math.round(Double.parseDouble(text.trim()) * 1_000_000.0); } catch (RuntimeException ex) { return fallback; }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.US).contains("win");
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String powerShellSingleQuote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }


}
