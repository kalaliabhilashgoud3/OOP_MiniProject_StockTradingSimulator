package com.stocksim.ui.screens;

import com.stocksim.model.Order;
import com.stocksim.model.OrderStatus;
import com.stocksim.model.Trader;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.Formatters;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Ui;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class OrdersScreen extends JPanel implements Refreshable {
    private final TradingPlatform platform;
    private final Supplier<Trader> traderSupplier;
    private final Consumer<String> notify;
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{
            "Order ID", "Time", "Trader", "Stock", "Type", "Side", "Quantity", "Remaining", "Limit / ref (₹)", "Trigger (₹)", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable table = new JTable(model);

    public OrdersScreen(TradingPlatform platform, Supplier<Trader> traderSupplier, Consumer<String> notify) {
        this.platform = platform;
        this.traderSupplier = traderSupplier;
        this.notify = notify;
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Orders", "Review active and completed orders. Buy orders reserve virtual funds; sells reserve share quantity.");
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.setOpaque(false);
        var cancel = Ui.button("Cancel selected order", false);
        var refresh = Ui.button("Refresh", false);
        tools.add(cancel); tools.add(refresh);
        body.add(tools, BorderLayout.NORTH);
        RoundedPanel card = Ui.card("Order history");
        Ui.styleTable(table);
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        body.add(card, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);

        cancel.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) { notify.accept("Select an active order first."); return; }
            String id = model.getValueAt(table.convertRowIndexToModel(row), 0).toString();
            try {
                platform.getOrderBook().cancel(id, traderSupplier.get());
                notify.accept("Order " + id + " cancelled. Reserved funds or shares were released.");
                refreshScreen();
            } catch (IllegalArgumentException | IllegalStateException ex) { notify.accept(ex.getMessage()); }
        });
        refresh.addActionListener(event -> refreshScreen());
        refreshScreen();
    }

    @Override public void refreshScreen() {
        model.setRowCount(0);
        for (Order order : platform.getOrderBook().getOrders()) {
            model.addRow(new Object[]{order.getId(), order.getTime().toString().replace('T', ' ').substring(0, 19),
                    order.getTrader().getName(), order.getStock().getSymbol(), order.getType(), order.getSide(), order.getOriginalQuantity(),
                    order.getRemainingQuantity(), order.getPrice() == 0 ? "Market" : Formatters.money(order.getPrice()),
                    order.getTriggerPrice() == 0 ? "—" : Formatters.money(order.getTriggerPrice()), order.getStatus()});
        }
    }
}
