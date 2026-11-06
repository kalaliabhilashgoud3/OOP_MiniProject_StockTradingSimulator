package com.stocksim.ui.components;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/** Shared colors, fonts and Swing defaults for every screen. */
public final class Theme {
    public static final Color NAVY = Color.decode("#102A43");
    public static final Color BLUE = Color.decode("#1677FF");
    public static final Color RED = Color.decode("#E53945");
    public static final Color YELLOW = Color.decode("#FFC928");
    public static Color BACKGROUND = Color.decode("#F4F7FC");
    public static Color WHITE = Color.decode("#FFFFFF");
    public static Color TEXT = Color.decode("#172B4D");
    public static Color MUTED = Color.decode("#718096");
    public static final Color GREEN = Color.decode("#16A36A");
    public static final Font BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font LABEL = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font TITLE = new Font("Segoe UI", Font.BOLD, 24);

    private Theme() { }

    public static void setDark(boolean dark) {
        BACKGROUND = Color.decode(dark ? "#101820" : "#F4F7FC");
        WHITE = Color.decode(dark ? "#1B2733" : "#FFFFFF");
        TEXT = Color.decode(dark ? "#E6EDF5" : "#172B4D");
        MUTED = Color.decode(dark ? "#A8B5C4" : "#718096");
    }

    public static void install() {
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", BODY);
        UIManager.put("Button.font", LABEL);
        UIManager.put("Button.background", BLUE);
        UIManager.put("Button.foreground", WHITE);
        UIManager.put("TextField.font", BODY);
        UIManager.put("TextField.background", WHITE);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("ComboBox.font", BODY);
        UIManager.put("Table.font", BODY);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.background", WHITE);
        UIManager.put("Table.selectionBackground", new Color(45, 75, 108));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.gridColor", darkGrid());
        UIManager.put("TableHeader.background", darkHeader());
        UIManager.put("TableHeader.foreground", NAVY);
        UIManager.put("TableHeader.font", LABEL);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
    }

    private static Color darkGrid() { return Color.decode(BACKGROUND.getRed() < 30 ? "#334150" : "#E5EBF3"); }
    private static Color darkHeader() { return Color.decode(BACKGROUND.getRed() < 30 ? "#243341" : "#ECF2F9"); }
}
