package com.stocksim.ui.screens;

import com.stocksim.model.BuyOrder;
import com.stocksim.model.Order;
import com.stocksim.model.SellOrder;
import com.stocksim.model.Stock;
import com.stocksim.model.Trader;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.Charts;
import com.stocksim.ui.components.Formatters;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Ui;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class OrderBookScreen extends JPanel implements Refreshable {
    private final TradingPlatform platform;
    private final Supplier<Trader> traderSupplier;
    private final Consumer<String> notify;
    private final JComboBox<String> symbols = new JComboBox<>();
    private final JPanel quoteCards = new JPanel(new GridLayout(1, 3, 12, 0));
    private final JPanel depthHost = new JPanel(new BorderLayout());
    private final DefaultTableModel bids = orderModel();
    private final DefaultTableModel asks = orderModel();
    private boolean updatingSymbols;

    public OrderBookScreen(TradingPlatform platform, Supplier<Trader> traderSupplier, Consumer<String> notify) {
        this.platform = platform;
        this.traderSupplier = traderSupplier;
        this.notify = notify;
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Order book", "Limit orders are ranked by price and arrival time; each symbol has an independent book.");
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout(0, 10)); top.setOpaque(false);
        symbols.addActionListener(event -> { if (!updatingSymbols) refreshScreen(); });
        top.add(Ui.row(new JLabel("Symbol"), symbols), BorderLayout.WEST);
        quoteCards.setOpaque(false);
        top.add(quoteCards, BorderLayout.CENTER);
        content.add(top, BorderLayout.NORTH);

        JPanel sides = new JPanel(new GridLayout(1, 2, 12, 0)); sides.setOpaque(false);
        RoundedPanel bidCard = Ui.card("BUY ORDERS · best bid first");
        JTable bidTable = new JTable(bids); Ui.styleTable(bidTable); bidCard.add(new JScrollPane(bidTable), BorderLayout.CENTER);
        RoundedPanel askCard = Ui.card("SELL ORDERS · best ask first");
        JTable askTable = new JTable(asks); Ui.styleTable(askTable); askCard.add(new JScrollPane(askTable), BorderLayout.CENTER);
        sides.add(bidCard); sides.add(askCard);
        JPanel body = new JPanel(new GridLayout(2, 1, 0, 12)); body.setOpaque(false);
        body.add(sides);
        RoundedPanel depthCard = Ui.card("Market depth visualization");
        depthCard.add(depthHost, BorderLayout.CENTER);
        body.add(depthCard);
        content.add(body, BorderLayout.CENTER);
        page.add(content, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);
        refreshScreen();
    }

    private static DefaultTableModel orderModel() {
        return new DefaultTableModel(new Object[]{"Trader", "Price (₹)", "Original qty", "Remaining", "Order time", "Status"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
    }

    private JPanel quote(String title, String value, java.awt.Color color) {
        RoundedPanel panel = Ui.card(title);
        JLabel label = new JLabel(value, JLabel.CENTER);
        label.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 20));
        label.setForeground(color);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private void addOrder(DefaultTableModel model, Order order) {
        model.addRow(new Object[]{order.getTrader().getName(), Formatters.money(order.getPrice()),
                order.getOriginalQuantity(), order.getRemainingQuantity(), order.getTime().toString().substring(11, 19),
                order.getStatus()});
    }

    @Override public void refreshScreen() {
        String old = (String) symbols.getSelectedItem();
        if (symbols.getItemCount() != platform.getStockList().size()) {
            updatingSymbols = true;
            symbols.removeAllItems(); platform.getStockList().forEach(stock -> symbols.addItem(stock.getSymbol()));
            if (old != null) symbols.setSelectedItem(old);
            updatingSymbols = false;
        }
        String symbol = (String) symbols.getSelectedItem();
        if (symbol == null) return;
        Stock stock = platform.findStock(symbol).orElse(null);
        List<BuyOrder> buyOrders = platform.getOrderBook().getBuys().stream()
                .filter(order -> order.getStock().getSymbol().equals(symbol)).toList();
        List<SellOrder> sellOrders = platform.getOrderBook().getSells().stream()
                .filter(order -> order.getStock().getSymbol().equals(symbol)).toList();
        bids.setRowCount(0); buyOrders.forEach(order -> addOrder(bids, order));
        asks.setRowCount(0); sellOrders.forEach(order -> addOrder(asks, order));
        quoteCards.removeAll();
        double bid = platform.getOrderBook().getBestBid(symbol);
        double ask = platform.getOrderBook().getBestAsk(symbol);
        quoteCards.add(quote("Best bid", bid == 0 ? "—" : Formatters.money(bid), com.stocksim.ui.components.Theme.GREEN));
        quoteCards.add(quote("Best ask", ask == 0 ? "—" : Formatters.money(ask), com.stocksim.ui.components.Theme.RED));
        quoteCards.add(quote("Bid / ask spread", Formatters.money(platform.getOrderBook().getSpread(symbol)), com.stocksim.ui.components.Theme.BLUE));
        quoteCards.revalidate(); quoteCards.repaint();
        depthHost.removeAll();
        depthHost.add(Charts.marketDepth(buyOrders, sellOrders), BorderLayout.CENTER);
        depthHost.revalidate(); depthHost.repaint();
    }
}
