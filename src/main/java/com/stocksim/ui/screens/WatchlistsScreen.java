package com.stocksim.ui.screens;

import com.stocksim.model.Stock;
import com.stocksim.model.Trader;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.Formatters;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Ui;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class WatchlistsScreen extends JPanel implements Refreshable {
    private final TradingPlatform platform;
    private final Supplier<Trader> traderSupplier;
    private final Consumer<Stock> openDetails;
    private final Consumer<Stock> openTrade;
    private final Consumer<String> notify;
    private final JComboBox<String> lists = new JComboBox<>();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Symbol", "Company", "Price (₹)", "Change %", "Volume"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable table = new JTable(model);

    public WatchlistsScreen(TradingPlatform platform, Supplier<Trader> traderSupplier,
                            Consumer<Stock> openDetails, Consumer<Stock> openTrade,
                            Consumer<String> notify) {
        this.platform = platform;
        this.traderSupplier = traderSupplier;
        this.openDetails = openDetails;
        this.openTrade = openTrade;
        this.notify = notify;
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Watchlists", "Keep your own groups of simulated listings and open them directly from here.");
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        lists.setToolTipText("Choose one of your saved watchlists");
        var create = Ui.button("New list", false);
        var rename = Ui.button("Rename", false);
        var remove = Ui.button("Remove stock", false);
        var details = Ui.button("Details", false);
        var trade = Ui.button("Buy / Sell", true);
        actions.add(lists); actions.add(create); actions.add(rename); actions.add(remove); actions.add(details); actions.add(trade);
        body.add(actions, BorderLayout.NORTH);
        RoundedPanel card = Ui.card("Saved watchlist");
        Ui.styleTable(table);
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        body.add(card, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);

        create.addActionListener(event -> {
            String name = JOptionPane.showInputDialog(this, "Name your new watchlist:", "New watchlist", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.isBlank()) return;
            try { platform.getWatchlists().create(traderSupplier.get().getId(), name.trim()); refreshScreen(); lists.setSelectedItem(name.trim()); }
            catch (IllegalArgumentException ex) { notify.accept(ex.getMessage()); }
        });
        rename.addActionListener(event -> {
            String current = (String) lists.getSelectedItem();
            if (current == null) return;
            String name = JOptionPane.showInputDialog(this, "New watchlist name:", current);
            if (name == null || name.isBlank()) return;
            try { platform.getWatchlists().rename(traderSupplier.get().getId(), current, name.trim()); refreshScreen(); lists.setSelectedItem(name.trim()); }
            catch (IllegalArgumentException ex) { notify.accept(ex.getMessage()); }
        });
        remove.addActionListener(event -> {
            Stock stock = selectedStock();
            if (stock == null) return;
            platform.getWatchlists().remove(traderSupplier.get().getId(), (String) lists.getSelectedItem(), stock.getSymbol());
            refreshScreen();
        });
        details.addActionListener(event -> { Stock stock = selectedStock(); if (stock != null) openDetails.accept(stock); });
        trade.addActionListener(event -> { Stock stock = selectedStock(); if (stock != null) openTrade.accept(stock); });
        lists.addActionListener(event -> refreshRows());
        refreshScreen();
    }

    private Stock selectedStock() {
        int row = table.getSelectedRow();
        if (row < 0) { notify.accept("Select a stock first."); return null; }
        return platform.findStock(model.getValueAt(table.convertRowIndexToModel(row), 0).toString()).orElse(null);
    }

    @Override public void refreshScreen() {
        String oldSelection = (String) lists.getSelectedItem();
        lists.setModel(new DefaultComboBoxModel<>(platform.getWatchlists().getNames(traderSupplier.get().getId()).toArray(String[]::new)));
        if (oldSelection != null) lists.setSelectedItem(oldSelection);
        refreshRows();
    }

    private void refreshRows() {
        model.setRowCount(0);
        Trader trader = traderSupplier.get();
        String list = (String) lists.getSelectedItem();
        if (trader == null || list == null) return;
        for (String symbol : platform.getWatchlists().getSymbols(trader.getId(), list)) {
            platform.findStock(symbol).ifPresent(stock -> model.addRow(new Object[]{stock.getSymbol(), stock.getName(),
                    Formatters.money(stock.getPrice()), Formatters.percent(stock.getChangePercent()),
                    Formatters.number(stock.getVolume())}));
        }
    }
}
