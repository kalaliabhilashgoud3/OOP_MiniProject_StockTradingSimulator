package com.stocksim.ui.components;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;

public final class Ui {
    private Ui() { }

    public static JPanel page(String title, String subtitle) {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        JPanel heading = new JPanel(new BorderLayout(0, 4));
        heading.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setFont(Theme.TITLE);
        label.setForeground(Theme.NAVY);
        heading.add(label, BorderLayout.NORTH);
        JLabel helper = new JLabel(subtitle);
        helper.setForeground(Theme.MUTED);
        heading.add(helper, BorderLayout.CENTER);
        root.add(heading, BorderLayout.NORTH);
        return root;
    }

    public static RoundedPanel card(String title) {
        RoundedPanel panel = new RoundedPanel();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBackground(Theme.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        if (title != null && !title.isBlank()) {
            JLabel label = new JLabel(title);
            label.setFont(Theme.LABEL);
            label.setForeground(Theme.NAVY);
            panel.add(label, BorderLayout.NORTH);
        }
        return panel;
    }

    public static JButton button(String label, boolean primary) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.setMargin(new Insets(9, 15, 9, 15));
        button.setForeground(primary ? Theme.WHITE : Theme.NAVY);
        button.setBackground(primary ? Theme.BLUE : Theme.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? Theme.BLUE : new Color(216, 226, 238)),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        button.setPreferredSize(new Dimension(130, 38));
        return button;
    }

    public static JPanel row(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setOpaque(false);
        for (Component component : components) panel.add(component);
        return panel;
    }

    public static void styleTable(JTable table) {
        table.setRowHeight(34);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setSelectionBackground(new Color(221, 235, 255));
        table.setGridColor(new Color(235, 239, 245));
        JTableHeader header = table.getTableHeader();
        header.setPreferredSize(new Dimension(header.getWidth(), 38));
        header.setFont(Theme.LABEL);
        header.setBackground(new Color(236, 242, 249));
        header.setForeground(Theme.NAVY);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        table.setDefaultRenderer(Object.class, renderer);
    }
}
