package com.stocksim.ui.screens;

import com.stocksim.model.Trader;
import com.stocksim.model.MarketSentiment;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Theme;
import com.stocksim.ui.components.Ui;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Lets the user customize the local trading simulation. */
public class SettingsScreen extends JPanel {
    private final TradingPlatform platform;
    public SettingsScreen(TradingPlatform platform, Runnable saved, Consumer<String> traderChanged,
                          Runnable themeChanged, Consumer<String> notify) {
        this.platform = platform;
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Settings", "Personalize the offline simulation and manage local data.");
        RoundedPanel card = Ui.card("App and simulation preferences");
        JPanel rows = new JPanel(new GridLayout(0, 2, 12, 12));
        rows.setOpaque(false);

        JComboBox<String> speed = new JComboBox<>(new String[]{"Slow · 5000 ms", "Normal · 2000 ms", "Fast · 750 ms", "Very fast · 250 ms"});
        int ms = platform.getMarket().getTickMillis();
        speed.setSelectedIndex(ms <= 500 ? 3 : ms <= 1000 ? 2 : ms <= 2500 ? 1 : 0);
        speed.addActionListener(e -> {
            int[] values = {5000, 2000, 750, 250};
            platform.getPriceUpdater().setDelayMillis(values[speed.getSelectedIndex()]); saved.run();
        });

        JComboBox<String> state = new JComboBox<>(new String[]{"Open", "Closed"});
        state.setSelectedItem(platform.getMarket().isMarketOpen() ? "Open" : "Closed");
        state.addActionListener(e -> {
            boolean open = state.getSelectedIndex() == 0;
            platform.getMarket().setMarketOpen(open);
            notify.accept(open ? "Simulated market opened." : "Simulated market closed.");
            saved.run();
        });

        JComboBox<String> trader = new JComboBox<>();
        for (Trader account : platform.getTraderList()) trader.addItem(account.getId() + " · " + account.getName());
        for (int i = 0; i < platform.getTraderList().size(); i++) {
            if (platform.getTraderList().get(i).getId().equals(platform.getDefaultTraderId())) trader.setSelectedIndex(i);
        }
        trader.addActionListener(e -> {
            int index = trader.getSelectedIndex();
            if (index < 0 || index >= platform.getTraderList().size()) return;
            String id = platform.getTraderList().get(index).getId();
            platform.setDefaultTraderId(id); traderChanged.accept(id); saved.run();
        });

        JCheckBox notifications = new JCheckBox("Show order and market alerts", platform.isNotificationsEnabled());
        notifications.setOpaque(false);
        notifications.addActionListener(e -> { platform.setNotificationsEnabled(notifications.isSelected()); saved.run(); });

        JComboBox<String> theme = new JComboBox<>(new String[]{"Light", "Dark"});
        theme.setSelectedIndex(platform.isDarkTheme() ? 1 : 0);
        theme.addActionListener(e -> {
            platform.setDarkTheme(theme.getSelectedIndex() == 1); themeChanged.run(); saved.run();
        });

        JComboBox<String> currency = new JComboBox<>(new String[]{"₹ Indian Rupees", "INR code"});
        currency.setSelectedIndex(platform.isUseCurrencyCode() ? 1 : 0);
        currency.addActionListener(e -> {
            platform.setUseCurrencyCode(currency.getSelectedIndex() == 1);
            com.stocksim.ui.components.Formatters.setUseCurrencyCode(platform.isUseCurrencyCode());
            saved.run();
        });

        rows.add(new JLabel("Market update speed")); rows.add(speed);
        rows.add(new JLabel("Simulated market state")); rows.add(state);
        rows.add(new JLabel("Default trader account")); rows.add(trader);
        rows.add(new JLabel("Color theme")); rows.add(theme);
        rows.add(new JLabel("Currency display")); rows.add(currency);
        rows.add(new JLabel("Notifications")); rows.add(notifications);
        card.add(rows, BorderLayout.NORTH);

        JPanel actions = new JPanel(); actions.setOpaque(false);
        var save = Ui.button("Save all data", true);
        save.addActionListener(e -> saved.run());
        var backup = Ui.button("Create data backup", false);
        backup.addActionListener(e -> createBackup());
        var about = Ui.button("About", false);
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Stock Trading Simulator 2.0\nMNR University · OOP Java · II-Year I-Sem\nOffline paper-trading simulator. Quotes and funds are virtual.",
                "About", JOptionPane.INFORMATION_MESSAGE));
        var reset = Ui.button("Reset market session", false);
        reset.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Reset simulated day highs, lows, volume, and market mood? Accounts, orders, and history will be kept.",
                    "Reset market session", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            platform.getStocks().values().forEach(stock -> stock.resetSession());
            platform.getMarket().setSentiment(MarketSentiment.SIDEWAYS);
            saved.run();
            notify.accept("Market session stats reset. Your account and trade history were kept.");
        });
        actions.add(save); actions.add(backup); actions.add(reset); actions.add(about);
        card.add(actions, BorderLayout.SOUTH);
        page.add(card, BorderLayout.NORTH);
        add(page, BorderLayout.CENTER);
    }

    private void createBackup() {
        Path data = Path.of("data");
        Path destination = data.resolve("backups").resolve("simulator-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip");
        try {
            platform.save();
            Files.createDirectories(destination.getParent());
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination))) {
                if (Files.exists(data)) {
                    try (var files = Files.list(data)) {
                        for (Path file : files.filter(Files::isRegularFile)
                                .filter(path -> path.getFileName().toString().endsWith(".csv")).toList()) {
                            zip.putNextEntry(new ZipEntry(file.getFileName().toString()));
                            Files.copy(file, zip);
                            zip.closeEntry();
                        }
                    }
                }
            }
            JOptionPane.showMessageDialog(this, "CSV backup created at:\n" + destination.toAbsolutePath(),
                    "Backup complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not create the backup: " + ex.getMessage(),
                    "Backup failed", JOptionPane.ERROR_MESSAGE);
        }
    }
}
