package com.stocksim.ui.screens;

import com.stocksim.model.Stock;
import com.stocksim.model.Trader;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.Charts;
import com.stocksim.ui.components.Formatters;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Theme;
import com.stocksim.ui.components.Ui;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class StockDetailsScreen extends JPanel implements Refreshable {
    private final TradingPlatform platform;
    private final Supplier<Trader> traderSupplier;
    private final Consumer<Stock> openTrade;
    private final Consumer<String> notify;
    private final JComboBox<String> symbols = new JComboBox<>();
    private final JComboBox<String> style = new JComboBox<>(new String[]{"Line", "Candlestick", "Volume"});
    private final JComboBox<String> range = new JComboBox<>(new String[]{"1D", "1W", "1M", "3M", "6M", "1Y", "ALL"});
    private final JLabel price = new JLabel();
    private final JLabel change = new JLabel();
    private final JLabel stats = new JLabel();
    private final JPanel chartHost = new JPanel(new BorderLayout());
    private boolean updating;

    public StockDetailsScreen(TradingPlatform platform, Supplier<Trader> traderSupplier,
                              Consumer<Stock> openTrade, Consumer<String> notify) {
        this.platform = platform;
        this.traderSupplier = traderSupplier;
        this.openTrade = openTrade;
        this.notify = notify;
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Stock details", "Explore simulated price action and company metrics. No real-time exchange data is connected.");
        JPanel body = new JPanel(new BorderLayout(0, 14)); body.setOpaque(false);
        var watch = Ui.button("Add to watchlist", false);
        var alert = Ui.button("Set price alert", false);
        var trade = Ui.button("Buy / Sell", true);
        JPanel bar = Ui.row(new JLabel("Stock"), symbols, style, range, watch, alert, trade);
        body.add(bar, BorderLayout.NORTH);
        RoundedPanel detail = Ui.card("Simulated quote");
        JPanel header = new JPanel(new BorderLayout(12, 6)); header.setOpaque(false);
        price.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 28)); price.setForeground(Theme.NAVY);
        change.setFont(Theme.LABEL);
        stats.setForeground(Theme.MUTED);
        JPanel labels = new JPanel(new GridLayout(2, 1, 0, 3)); labels.setOpaque(false);
        labels.add(price); labels.add(change);
        header.add(labels, BorderLayout.WEST); header.add(stats, BorderLayout.CENTER);
        detail.add(header, BorderLayout.NORTH);
        chartHost.setBackground(Theme.WHITE);
        detail.add(chartHost, BorderLayout.CENTER);
        body.add(detail, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);

        symbols.addActionListener(event -> { if (!updating) refreshScreen(); });
        style.addActionListener(event -> refreshScreen());
        range.addActionListener(event -> refreshScreen());
        watch.addActionListener(event -> {
            Stock stock = selected(); if (stock == null) return;
            Trader traderNow = traderSupplier.get();
            String list = platform.getWatchlists().getNames(traderNow.getId()).get(0);
            platform.getWatchlists().add(traderNow.getId(), list, stock.getSymbol());
            notify.accept(stock.getSymbol() + " added to " + list + ".");
        });
        alert.addActionListener(event -> {
            Stock stock = selected();
            if (stock == null) return;
            String target = JOptionPane.showInputDialog(this, "Alert target price for " + stock.getSymbol(),
                    String.format(java.util.Locale.ROOT, "%.2f", stock.getPrice()));
            if (target == null) return;
            Object[] choices = {"When price rises above", "When price falls below"};
            int direction = JOptionPane.showOptionDialog(this, "Choose when to notify you.", "Price alert",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
            if (direction < 0) return;
            try {
                double threshold = Double.parseDouble(target.trim());
                var trader = traderSupplier.get();
                var alertItem = platform.getPriceAlerts().add(trader.getId(), stock.getSymbol(),
                        direction == 0 ? com.stocksim.service.PriceAlertService.Direction.ABOVE
                                : com.stocksim.service.PriceAlertService.Direction.BELOW, threshold);
                notify.accept("Price alert " + alertItem.id() + " set for " + stock.getSymbol() + ".");
            } catch (IllegalArgumentException ex) {
                notify.accept("Enter a valid positive alert price.");
            }
        });
        trade.addActionListener(event -> { Stock stock = selected(); if (stock != null) openTrade.accept(stock); });
        refreshScreen();
    }

    public void setStock(Stock stock) {
        if (stock != null) symbols.setSelectedItem(stock.getSymbol());
    }

    private Stock selected() {
        return symbols.getSelectedItem() == null ? null : platform.findStock(symbols.getSelectedItem().toString()).orElse(null);
    }

    @Override public void refreshScreen() {
        if (symbols.getItemCount() != platform.getStockList().size()) {
            String old = (String) symbols.getSelectedItem();
            updating = true;
            symbols.removeAllItems(); platform.getStockList().forEach(stock -> symbols.addItem(stock.getSymbol()));
            if (old != null) symbols.setSelectedItem(old);
            updating = false;
        }
        Stock stock = selected();
        if (stock == null) return;
        price.setText(Formatters.money(stock.getPrice()) + "   " + stock.getName());
        change.setText(Formatters.money(stock.getChange()) + "  (" + Formatters.percent(stock.getChangePercent()) + ")");
        change.setForeground(stock.getChangePercent() >= 0 ? Theme.GREEN : Theme.RED);
        stats.setText("Open " + Formatters.money(stock.getOpen()) + "    Day range " + Formatters.money(stock.getDayLow())
                + " – " + Formatters.money(stock.getDayHigh()) + "    52W " + Formatters.money(stock.getWeekLow())
                + " – " + Formatters.money(stock.getWeekHigh()) + "    Volume " + Formatters.number(stock.getVolume())
                + "    Market cap " + Formatters.money(stock.getMarketCap()) + " (simulated)");
        chartHost.removeAll();
        chartHost.add(Charts.prices(stock, (String) range.getSelectedItem(), (String) style.getSelectedItem()), BorderLayout.CENTER);
        chartHost.revalidate(); chartHost.repaint();
    }
}
