package com.stocksim.ui.screens;

import com.stocksim.model.Stock;
import com.stocksim.model.Trader;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.Formatters;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Theme;
import com.stocksim.ui.components.Ui;
import javax.swing.DefaultRowSorter;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MarketsScreen extends JPanel implements Refreshable {
    private final TradingPlatform platform;
    private final Supplier<Trader> traderSupplier;
    private final Consumer<Stock> openDetails;
    private final Consumer<Stock> openTrade;
    private final Consumer<String> notify;
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{
            "Symbol", "Company", "Price (₹)", "Prev. close", "Open", "Day high", "Day low",
            "Change %", "Volume", "Market cap (sim.)", "52W high", "52W low"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = new JTextField(22);

    public MarketsScreen(TradingPlatform platform, Supplier<Trader> traderSupplier,
                         Consumer<Stock> openDetails, Consumer<Stock> openTrade,
                         Consumer<String> notify) {
        this.platform = platform;
        this.traderSupplier = traderSupplier;
        this.openDetails = openDetails;
        this.openTrade = openTrade;
        this.notify = notify;
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Markets", "Browse simulated listings. Prices are generated offline and are not NSE/BSE quotes.");
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.setOpaque(false);
        search.setToolTipText("Search by symbol or company name");
        JButton watch = Ui.button("Add to watchlist", false);
        JButton details = Ui.button("Stock details", false);
        JButton buy = Ui.button("Buy / Sell", true);
        tools.add(new JLabel("Search")); tools.add(search); tools.add(watch); tools.add(details); tools.add(buy);
        content.add(tools, BorderLayout.NORTH);
        RoundedPanel card = Ui.card("Market watch · simulated market data");
        table.setRowSorter(sorter);
        Ui.styleTable(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(card, BorderLayout.CENTER);
        page.add(content, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);

        search.getDocument().addDocumentListener((SimpleDocumentListener) () -> {
            String query = search.getText().trim().toLowerCase();
            sorter.setRowFilter(query.isEmpty() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(query), 0, 1));
        });
        watch.addActionListener(event -> {
            Stock stock = selectedStock();
            if (stock == null) return;
            Trader trader = traderSupplier.get();
            String list = platform.getWatchlists().getNames(trader.getId()).stream().findFirst().orElse("My Watchlist");
            platform.getWatchlists().add(trader.getId(), list, stock.getSymbol());
            notify.accept(stock.getSymbol() + " added to " + list + ".");
        });
        details.addActionListener(event -> { Stock stock = selectedStock(); if (stock != null) openDetails.accept(stock); });
        buy.addActionListener(event -> { Stock stock = selectedStock(); if (stock != null) openTrade.accept(stock); });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) { Stock stock = selectedStock(); if (stock != null) openDetails.accept(stock); }
            }
        });
        refreshScreen();
    }

    private Stock selectedStock() {
        int row = table.getSelectedRow();
        if (row < 0) { notify.accept("Select a stock row first."); return null; }
        String symbol = model.getValueAt(table.convertRowIndexToModel(row), 0).toString();
        return platform.findStock(symbol).orElse(null);
    }

    @Override public void refreshScreen() {
        model.setRowCount(0);
        for (Stock stock : platform.getStockList()) model.addRow(new Object[]{stock.getSymbol(), stock.getName(),
                Formatters.money(stock.getPrice()), Formatters.money(stock.getPreviousClose()),
                Formatters.money(stock.getOpen()), Formatters.money(stock.getDayHigh()),
                Formatters.money(stock.getDayLow()), Formatters.percent(stock.getChangePercent()),
                Formatters.number(stock.getVolume()), Formatters.money(stock.getMarketCap()),
                Formatters.money(stock.getWeekHigh()), Formatters.money(stock.getWeekLow())});
        for (int i = 0; i < table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setPreferredWidth(i < 2 ? 145 : 116);
    }

    @FunctionalInterface private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
        void changed();
        @Override default void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
        @Override default void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
        @Override default void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
    }
}
