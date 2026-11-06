package com.stocksim.ui.screens;

import com.stocksim.model.Trade;
import com.stocksim.model.TransactionRecord;
import com.stocksim.model.Trader;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.Formatters;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Ui;
import com.stocksim.util.CsvStore;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class HistoryScreen extends JPanel implements Refreshable {
    private final TradingPlatform platform;
    private final Supplier<Trader> traderSupplier;
    private final java.util.function.Consumer<String> notify;
    private final DefaultTableModel trades = model(new String[]{"Trade ID", "Time", "Stock", "Buyer", "Seller", "Qty", "Execution price"});
    private final DefaultTableModel transactions = model(new String[]{"ID", "Time", "Trader", "Type", "Amount", "Description"});
    private final JTable tradeTable = new JTable(trades);
    private final JTable transactionTable = new JTable(transactions);
    private final TableRowSorter<DefaultTableModel> tradeSorter = new TableRowSorter<>(trades);
    private final TableRowSorter<DefaultTableModel> transactionSorter = new TableRowSorter<>(transactions);
    private final JTextField search = new JTextField(18);
    private final JComboBox<String> dateRange = new JComboBox<>(new String[]{"All time", "1D", "1W", "1M"});
    private JTable activeTable = tradeTable;
    private DefaultTableModel activeModel = trades;

    public HistoryScreen(TradingPlatform platform, Supplier<Trader> traderSupplier,
                         java.util.function.Consumer<String> notify) {
        this.platform = platform; this.traderSupplier = traderSupplier; this.notify = notify;
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Trade history", "Search, filter and export executed trades and the virtual account transaction ledger.");
        JPanel content = new JPanel(new BorderLayout(0, 12)); content.setOpaque(false);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); tools.setOpaque(false);
        var export = Ui.button("Export visible CSV", true);
        tools.add(new javax.swing.JLabel("Search")); tools.add(search); tools.add(dateRange); tools.add(export);
        content.add(tools, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        RoundedPanel tradeCard = Ui.card("Completed simulated trades");
        tradeTable.setRowSorter(tradeSorter); Ui.styleTable(tradeTable);
        tradeCard.add(new JScrollPane(tradeTable), BorderLayout.CENTER);
        RoundedPanel transactionCard = Ui.card("Virtual account transactions");
        transactionTable.setRowSorter(transactionSorter); Ui.styleTable(transactionTable);
        transactionCard.add(new JScrollPane(transactionTable), BorderLayout.CENTER);
        tabs.addTab("Trades", tradeCard); tabs.addTab("Transactions", transactionCard);
        content.add(tabs, BorderLayout.CENTER);
        page.add(content, BorderLayout.CENTER); add(page, BorderLayout.CENTER);

        tabs.addChangeListener(event -> {
            boolean tradeView = tabs.getSelectedIndex() == 0;
            activeTable = tradeView ? tradeTable : transactionTable;
            activeModel = tradeView ? trades : transactions;
            applyFilters();
        });
        search.getDocument().addDocumentListener(new SimpleListener(this::applyFilters));
        dateRange.addActionListener(event -> applyFilters());
        export.addActionListener(event -> exportVisible());
        refreshScreen();
    }

    private static DefaultTableModel model(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
    }

    private void applyFilters() {
        String query = search.getText().trim();
        RowFilter<DefaultTableModel, Integer> filter = null;
        List<RowFilter<DefaultTableModel, Integer>> filters = new ArrayList<>();
        if (!query.isEmpty()) filters.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(query)));
        Instant after = switch ((String) dateRange.getSelectedItem()) {
            case "1D" -> Instant.now().minus(1, ChronoUnit.DAYS);
            case "1W" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "1M" -> Instant.now().minus(30, ChronoUnit.DAYS);
            default -> null;
        };
        if (after != null) filters.add(new RowFilter<>() {
            @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                try { return Instant.parse(entry.getStringValue(1)).isAfter(after); }
                catch (RuntimeException ex) { return true; }
            }
        });
        if (!filters.isEmpty()) filter = RowFilter.andFilter(filters);
        if (activeModel == trades) tradeSorter.setRowFilter(filter);
        else transactionSorter.setRowFilter(filter);
    }

    private void exportVisible() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(activeModel == trades ? "trade-history.csv" : "transaction-history.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            List<List<String>> rows = new ArrayList<>();
            for (int col = 0; col < activeModel.getColumnCount(); col++) rows.add(new ArrayList<>());
            for (int col = 0; col < activeModel.getColumnCount(); col++) rows.get(col).add(activeModel.getColumnName(col));
            for (int row = 0; row < activeTable.getRowCount(); row++) {
                for (int col = 0; col < activeModel.getColumnCount(); col++) {
                    rows.get(col).add(String.valueOf(activeTable.getValueAt(row, col)));
                }
            }
            List<List<String>> byRows = transpose(rows);
            CsvStore.writeRows(chooser.getSelectedFile().toPath(), byRows);
            notify.accept("CSV report exported to " + chooser.getSelectedFile().getName());
        } catch (Exception ex) { notify.accept("Export failed: " + ex.getMessage()); }
    }

    private static List<List<String>> transpose(List<List<String>> columns) {
        if (columns.isEmpty()) return List.of();
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < columns.get(0).size(); i++) {
            List<String> row = new ArrayList<>();
            for (List<String> column : columns) row.add(column.get(i));
            rows.add(row);
        }
        return rows;
    }

    @Override public void refreshScreen() {
        trades.setRowCount(0);
        for (Trade trade : platform.getTradeHistory().getAll()) trades.addRow(new Object[]{trade.id(), trade.time().toString(),
                trade.symbol(), trade.buyer(), trade.seller(), trade.quantity(), Formatters.money(trade.price())});
        transactions.setRowCount(0);
        for (TransactionRecord record : platform.getTransactionLedger().getAll()) transactions.addRow(new Object[]{
                record.id(), record.time().toString(), record.traderId(), record.type(), Formatters.money(record.amount()), record.description()});
        applyFilters();
    }

    private record SimpleListener(Runnable action) implements javax.swing.event.DocumentListener {
        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
    }
}
