package org.deadscout.desktop;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.Locale;

final class DesktopTheme {
    static final Color BG = new Color(8, 13, 18);
    static final Color PANEL = new Color(18, 27, 35);
    static final Color CARD = new Color(24, 36, 46);
    static final Color CARD_2 = new Color(29, 44, 56);
    static final Color TEXT = new Color(232, 240, 244);
    static final Color MUTED = new Color(178, 196, 207);
    static final Color ACCENT = new Color(42, 157, 143);
    static final Color WARN = new Color(233, 196, 106);
    static final Color BORDER = new Color(58, 82, 98);
    static final Color SELECTED = new Color(31, 104, 96);

    static final Font UI_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    static final Font UI_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    static final Font UI_VALUE = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    static final Font MONO_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    private DesktopTheme() { }

    static void installReadableDefaults() {
        UIManager.put("Panel.background", PANEL);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", UI_FONT);
        UIManager.put("Button.background", CARD_2);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.font", UI_BOLD);
        UIManager.put("Button.select", SELECTED);
        UIManager.put("Button.disabledText", MUTED);
        UIManager.put("TextField.background", CARD);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("TextField.selectionBackground", SELECTED);
        UIManager.put("TextField.selectionForeground", Color.WHITE);
        UIManager.put("TextField.font", UI_FONT);
        UIManager.put("TextArea.background", CARD);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", TEXT);
        UIManager.put("TextArea.selectionBackground", SELECTED);
        UIManager.put("TextArea.selectionForeground", Color.WHITE);
        UIManager.put("TextArea.font", MONO_FONT);
        UIManager.put("List.background", CARD);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", SELECTED);
        UIManager.put("List.selectionForeground", Color.WHITE);
        UIManager.put("List.font", MONO_FONT);
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("Viewport.background", CARD);
        UIManager.put("TabbedPane.background", PANEL);
        UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("TabbedPane.selected", CARD_2);
        UIManager.put("TabbedPane.contentAreaColor", PANEL);
        UIManager.put("TabbedPane.font", UI_BOLD);
        UIManager.put("SplitPane.background", PANEL);
        UIManager.put("SplitPane.dividerSize", 10);
        UIManager.put("SplitPane.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPaneDivider.draggingColor", SELECTED);
        UIManager.put("ToolTip.background", CARD_2);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("TitledBorder.titleColor", MUTED);
    }

    static JPanel section(String title, JComponent body) {
        JPanel panel = darkPanel(new java.awt.BorderLayout(6, 6));
        TitledBorder titleBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER), title);
        titleBorder.setTitleColor(MUTED);
        titleBorder.setTitleFont(UI_BOLD);
        panel.setBorder(BorderFactory.createCompoundBorder(titleBorder, BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        panel.add(body, java.awt.BorderLayout.CENTER);
        return panel;
    }

    static JPanel fieldRow(String labelText, JTextField field) {
        JPanel panel = darkPanel(new java.awt.BorderLayout(4, 2));
        panel.add(smallLabel(labelText), java.awt.BorderLayout.NORTH);
        panel.add(styleField(field), java.awt.BorderLayout.CENTER);
        return panel;
    }

    static JLabel smallLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(UI_FONT);
        return label;
    }

    static JLabel cardValue(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(UI_VALUE);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    static JPanel card(String title, JLabel value) {
        JPanel panel = darkPanel(new java.awt.BorderLayout(4, 4));
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel t = new JLabel(title.toUpperCase(Locale.US));
        t.setForeground(MUTED);
        t.setFont(UI_BOLD.deriveFont(11f));
        panel.add(t, java.awt.BorderLayout.NORTH);
        panel.add(value, java.awt.BorderLayout.CENTER);
        return panel;
    }

    static JPanel darkPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(PANEL);
        panel.setForeground(TEXT);
        panel.setFont(UI_FONT);
        return panel;
    }

    static JTextArea textArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(MONO_FONT);
        area.setBackground(CARD);
        area.setForeground(TEXT);
        area.setCaretColor(TEXT);
        area.setSelectionColor(SELECTED);
        area.setSelectedTextColor(Color.WHITE);
        area.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return area;
    }

    static JScrollPane scroll(Component component) {
        JScrollPane pane = new JScrollPane(component);
        pane.setBorder(BorderFactory.createLineBorder(BORDER));
        pane.getViewport().setBackground(CARD);
        pane.setBackground(PANEL);
        styleScrollBar(pane.getVerticalScrollBar());
        styleScrollBar(pane.getHorizontalScrollBar());
        pane.getVerticalScrollBar().setUnitIncrement(22);
        pane.getHorizontalScrollBar().setUnitIncrement(28);
        return pane;
    }

    static void styleScrollBar(JScrollBar bar) {
        if (bar == null) return;
        bar.setUI(new DeadScoutScrollBarUI());
        bar.setOpaque(false);
        int thickness = 12;
        if (bar.getOrientation() == JScrollBar.VERTICAL) {
            bar.setPreferredSize(new Dimension(thickness, 0));
        } else {
            bar.setPreferredSize(new Dimension(0, thickness));
        }
        bar.setUnitIncrement(22);
        bar.setBlockIncrement(96);
    }

    static void styleSplitPane(JSplitPane splitPane) {
        if (splitPane == null) return;
        int dividerLocation = splitPane.getDividerLocation();
        if (!(splitPane.getUI() instanceof DeadScoutSplitPaneUI)) {
            splitPane.setUI(new DeadScoutSplitPaneUI());
            if (dividerLocation >= 0) splitPane.setDividerLocation(dividerLocation);
        }
        splitPane.setBackground(PANEL);
        splitPane.setForeground(TEXT);
        splitPane.setDividerSize(10);
        splitPane.setContinuousLayout(true);
        if (splitPane.getBorder() == null) {
            splitPane.setBorder(BorderFactory.createLineBorder(BORDER));
        }
    }

    static void styleButton(JButton button) {
        button.setBackground(CARD_2);
        button.setForeground(TEXT);
        button.setFont(UI_BOLD);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
    }

    static JTextField styleField(JTextField field) {
        field.setBackground(CARD);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setFont(UI_FONT);
        field.setSelectionColor(SELECTED);
        field.setSelectedTextColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(6, 7, 6, 7)));
        return field;
    }

    static void styleComponentTree(Component component) {
        if (component == null) return;
        if (!(component instanceof JLabel)) component.setFont(component instanceof JTextArea ? MONO_FONT : UI_FONT);
        if (component instanceof JPanel) {
            component.setBackground(PANEL);
            component.setForeground(TEXT);
        } else if (component instanceof JTextArea) {
            JTextArea area = (JTextArea) component;
            area.setBackground(CARD);
            area.setForeground(TEXT);
            area.setCaretColor(TEXT);
            area.setSelectionColor(SELECTED);
            area.setSelectedTextColor(Color.WHITE);
        } else if (component instanceof JTextField) {
            styleField((JTextField) component);
        } else if (component instanceof JButton) {
            styleButton((JButton) component);
        } else if (component instanceof JLabel) {
            component.setForeground(component.getForeground() == null ? TEXT : component.getForeground());
        } else if (component instanceof JScrollPane) {
            component.setBackground(PANEL);
            JScrollPane pane = (JScrollPane) component;
            styleScrollBar(pane.getVerticalScrollBar());
            styleScrollBar(pane.getHorizontalScrollBar());
        } else if (component instanceof JViewport) {
            component.setBackground(CARD);
        } else if (component instanceof JTabbedPane) {
            component.setBackground(PANEL);
            component.setForeground(TEXT);
            component.setFont(UI_BOLD);
        } else if (component instanceof JSplitPane) {
            styleSplitPane((JSplitPane) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) styleComponentTree(child);
        }
    }

    private static final class DeadScoutSplitPaneUI extends BasicSplitPaneUI {
        @Override public BasicSplitPaneDivider createDefaultDivider() {
            return new DeadScoutSplitPaneDivider(this);
        }
    }

    private static final class DeadScoutSplitPaneDivider extends BasicSplitPaneDivider {
        DeadScoutSplitPaneDivider(BasicSplitPaneUI ui) {
            super(ui);
            setBackground(PANEL);
            setBorder(BorderFactory.createEmptyBorder());
        }

        @Override public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                int width = Math.max(1, getWidth());
                int height = Math.max(1, getHeight());
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(PANEL);
                g2.fillRect(0, 0, width, height);

                boolean verticalDivider = true;
                if (getBasicSplitPaneUI() != null && getBasicSplitPaneUI().getSplitPane() != null) {
                    verticalDivider = getBasicSplitPaneUI().getSplitPane().getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
                }
                g2.setColor(new Color(35, 52, 65));
                if (verticalDivider) {
                    int trackX = Math.max(1, width / 2 - 3);
                    g2.fillRoundRect(trackX, 3, Math.max(1, Math.min(6, width - 2)), Math.max(1, height - 6), 8, 8);
                    g2.setColor(new Color(73, 114, 122));
                    int gripX = Math.max(1, width / 2 - 2);
                    int centerY = height / 2;
                    for (int offset = -7; offset <= 7; offset += 7) {
                        g2.fillRoundRect(gripX, centerY + offset - 1, Math.max(1, Math.min(4, width - 2)), 2, 2, 2);
                    }
                } else {
                    int trackY = Math.max(1, height / 2 - 3);
                    g2.fillRoundRect(3, trackY, Math.max(1, width - 6), Math.max(1, Math.min(6, height - 2)), 8, 8);
                    g2.setColor(new Color(73, 114, 122));
                    int gripY = Math.max(1, height / 2 - 2);
                    int centerX = width / 2;
                    for (int offset = -7; offset <= 7; offset += 7) {
                        g2.fillRoundRect(centerX + offset - 1, gripY, 2, Math.max(1, Math.min(4, height - 2)), 2, 2);
                    }
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class DeadScoutScrollBarUI extends BasicScrollBarUI {
        @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

        @Override protected void configureScrollBarColors() {
            thumbColor = new Color(62, 104, 112);
            thumbDarkShadowColor = new Color(62, 104, 112);
            thumbHighlightColor = new Color(83, 132, 138);
            thumbLightShadowColor = new Color(83, 132, 138);
            trackColor = new Color(13, 21, 28);
        }

        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(new Color(13, 21, 28));
                g2.fillRoundRect(bounds.x + 2, bounds.y + 2,
                        Math.max(1, bounds.width - 4), Math.max(1, bounds.height - 4), 10, 10);
            } finally {
                g2.dispose();
            }
        }

        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
            if (!c.isEnabled() || bounds.width <= 0 || bounds.height <= 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                Color thumb = isDragging ? ACCENT : (isThumbRollover() ? new Color(75, 128, 132) : new Color(54, 91, 101));
                g2.setColor(thumb);
                g2.fillRoundRect(bounds.x + 2, bounds.y + 2,
                        Math.max(4, bounds.width - 4), Math.max(4, bounds.height - 4), 10, 10);
            } finally {
                g2.dispose();
            }
        }

        private static JButton zeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setBorder(BorderFactory.createEmptyBorder());
            button.setOpaque(false);
            button.setFocusable(false);
            return button;
        }
    }
}
