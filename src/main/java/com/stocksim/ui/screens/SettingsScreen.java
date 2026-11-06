package com.stocksim.ui.screens;

import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Ui;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;

/** Lets the user control the local simulation. */
public class SettingsScreen extends JPanel {
    public SettingsScreen(TradingPlatform platform, Runnable saved) {
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Settings", "Control the offline market simulation and save its data.");
        RoundedPanel card = Ui.card("Simulation controls");
        JPanel rows = new JPanel(new GridLayout(0, 2, 12, 12));
        rows.setOpaque(false);
        JComboBox<String> speed = new JComboBox<>(new String[]{"Slow · 5000 ms", "Normal · 2000 ms", "Fast · 750 ms", "Very fast · 250 ms"});
        int ms = platform.getMarket().getTickMillis();
        speed.setSelectedIndex(ms <= 500 ? 3 : ms <= 1000 ? 2 : ms <= 2500 ? 1 : 0);
        speed.addActionListener(e -> {
            int[] values = {5000, 2000, 750, 250};
            platform.getPriceUpdater().setDelayMillis(values[speed.getSelectedIndex()]);
            saved.run();
        });
        JComboBox<String> state = new JComboBox<>(new String[]{"Open", "Closed"});
        state.setSelectedItem(platform.getMarket().isMarketOpen() ? "Open" : "Closed");
        state.addActionListener(e -> {
            platform.getMarket().setMarketOpen(state.getSelectedIndex() == 0);
            saved.run();
        });
        rows.add(new JLabel("Price update speed")); rows.add(speed);
        rows.add(new JLabel("Simulated market state")); rows.add(state);
        card.add(rows, BorderLayout.NORTH);
        JPanel actions = new JPanel(); actions.setOpaque(false);
        var save = Ui.button("Save all data now", true);
        save.addActionListener(e -> saved.run());
        actions.add(save);
        card.add(actions, BorderLayout.SOUTH);
        page.add(card, BorderLayout.NORTH);
        add(page, BorderLayout.CENTER);
    }
}
