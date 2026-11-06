package com.stocksim.ui.screens;

import com.stocksim.exception.InsufficientFundsException;
import com.stocksim.model.OrderType;
import com.stocksim.model.Stock;
import com.stocksim.model.Trader;
import com.stocksim.service.OrderBook;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.Formatters;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Theme;
import com.stocksim.ui.components.Ui;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Supplier;

public class TradeScreen extends JPanel implements Refreshable {
    private final TradingPlatform platform;
    private final Supplier<Trader> traderSupplier;
    private final java.util.function.Consumer<String> notify;
    private final JComboBox<String> symbol = new JComboBox<>();
    private final JComboBox<String> side = new JComboBox<>(new String[]{"BUY", "SELL"});
    private final JComboBox<OrderType> type = new JComboBox<>(OrderType.values());
    private final JTextField quantity = new JTextField("1", 14);
    private final JTextField limitPrice = new JTextField(14);
    private final JTextField triggerPrice = new JTextField(14);
    private final JLabel estimate = new JLabel();
    private final JLabel available = new JLabel();
    private final JLabel typeHelp = new JLabel("Market orders use the current simulated price.");

    public TradeScreen(TradingPlatform platform, Supplier<Trader> traderSupplier,
                       java.util.function.Consumer<String> notify) {
        this.platform = platform;
        this.traderSupplier = traderSupplier;
        this.notify = notify;
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Buy / Sell", "Orders use virtual balances and simulated prices. Charges shown are simulator estimates.");
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        RoundedPanel card = Ui.card("Order ticket");
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = .35;
        int row = 0;
        addRow(form, c, row++, "Account", accountLabel());
        addRow(form, c, row++, "Stock", symbol);
        addRow(form, c, row++, "Side", side);
        addRow(form, c, row++, "Order type", type);
        addRow(form, c, row++, "Quantity", quantity);
        addRow(form, c, row++, "Limit price (₹)", limitPrice);
        addRow(form, c, row++, "Trigger price (₹)", triggerPrice);
        typeHelp.setForeground(Theme.MUTED);
        addRow(form, c, row++, "Order rules", typeHelp);
        estimate.setFont(Theme.LABEL);
        addRow(form, c, row++, "Estimated value + charges", estimate);
        available.setForeground(Theme.BLUE);
        addRow(form, c, row++, "Available virtual cash / shares", available);
        var submit = Ui.button("Review and place", true);
        c.gridx = 1; c.gridy = row; c.weightx = .65;
        form.add(submit, c);
        card.add(form, BorderLayout.CENTER);
        body.add(card, BorderLayout.NORTH);
        page.add(body, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);

        type.addActionListener(event -> updateVisibility());
        side.addActionListener(event -> updateEstimate());
        symbol.addActionListener(event -> updateEstimate());
        quantity.getDocument().addDocumentListener(new LiveListener(this::updateEstimate));
        limitPrice.getDocument().addDocumentListener(new LiveListener(this::updateEstimate));
        triggerPrice.getDocument().addDocumentListener(new LiveListener(this::updateEstimate));
        submit.addActionListener(event -> placeOrder());
        refreshScreen();
    }

    private JLabel accountLabel() {
        Trader trader = traderSupplier.get();
        return new JLabel(trader == null ? "No account" : trader.getName() + " · Virtual funds");
    }

    public void setStock(Stock stock) {
        if (stock != null) {
            symbol.setSelectedItem(stock.getSymbol());
            limitPrice.setText(String.format(java.util.Locale.ROOT, "%.2f", stock.getPrice()));
            triggerPrice.setText(String.format(java.util.Locale.ROOT, "%.2f", stock.getPrice()));
            updateEstimate();
        }
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String name, java.awt.Component component) {
        c.gridy = row; c.gridx = 0; c.weightx = .35;
        JLabel label = new JLabel(name); label.setForeground(Theme.MUTED); panel.add(label, c);
        c.gridx = 1; c.weightx = .65; panel.add(component, c);
    }

    private Stock selectedStock() {
        return symbol.getSelectedItem() == null ? null
                : platform.findStock(symbol.getSelectedItem().toString()).orElse(null);
    }

    private void updateVisibility() {
        OrderType selected = (OrderType) type.getSelectedItem();
        limitPrice.setEnabled(selected == OrderType.LIMIT || selected == OrderType.STOP_LIMIT);
        triggerPrice.setEnabled(selected == OrderType.STOP_LIMIT || selected == OrderType.STOP_LOSS);
        typeHelp.setText(switch (selected) {
            case MARKET -> "Executes at the current simulated price against simulated liquidity.";
            case LIMIT -> "Executes only at your limit price or better; may remain pending.";
            case STOP_LOSS -> "Triggers a simulated market order when the trigger is reached.";
            case STOP_LIMIT -> "Triggers a limit order when the trigger is reached.";
        });
        updateEstimate();
    }

    private void updateEstimate() {
        Stock stock = selectedStock();
        Trader trader = traderSupplier.get();
        if (stock == null || trader == null) return;
        int qty = parseQuantity();
        OrderType selectedType = (OrderType) type.getSelectedItem();
        double px = selectedType == OrderType.MARKET ? stock.getPrice() : parsePrice(limitPrice, stock.getPrice());
        double value = Math.max(0, qty * px);
        double charge = value * OrderBook.CHARGE_RATE;
        estimate.setText(Formatters.money(value) + " + " + Formatters.money(charge)
                + " charges = " + Formatters.money(value + charge));
        available.setText(Formatters.money(trader.getWallet()) + " · "
                + trader.getPortfolio().getQuantity(stock.getSymbol()) + " " + stock.getSymbol() + " shares");
    }

    private int parseQuantity() {
        try { return Math.max(0, Integer.parseInt(quantity.getText().trim())); }
        catch (NumberFormatException ex) { return 0; }
    }

    private static double parsePrice(JTextField field, double fallback) {
        try { return Double.parseDouble(field.getText().trim()); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private void placeOrder() {
        Trader trader = traderSupplier.get();
        Stock stock = selectedStock();
        try {
            if (trader == null || stock == null) throw new IllegalArgumentException("Choose a trader and stock.");
            int qty = Integer.parseInt(quantity.getText().trim());
            if (qty <= 0) throw new IllegalArgumentException("Quantity must be a positive whole number.");
            OrderType selectedType = (OrderType) type.getSelectedItem();
            double limit = selectedType == OrderType.MARKET ? stock.getPrice() : Double.parseDouble(limitPrice.getText().trim());
            double trigger = selectedType == OrderType.STOP_LIMIT || selectedType == OrderType.STOP_LOSS
                    ? Double.parseDouble(triggerPrice.getText().trim()) : 0;
            if (selectedType != OrderType.MARKET && (!Double.isFinite(limit) || limit <= 0)) {
                throw new IllegalArgumentException("Limit price must be greater than zero.");
            }
            if ((selectedType == OrderType.STOP_LIMIT || selectedType == OrderType.STOP_LOSS)
                    && (!Double.isFinite(trigger) || trigger <= 0)) {
                throw new IllegalArgumentException("Trigger price must be greater than zero.");
            }
            if (selectedType == OrderType.STOP_LOSS) {
                boolean buy = "BUY".equals(side.getSelectedItem());
                if ((buy && trigger <= stock.getPrice()) || (!buy && trigger >= stock.getPrice())) {
                    throw new IllegalArgumentException(buy
                            ? "A buy stop trigger must be above the current simulated price."
                            : "A sell stop trigger must be below the current simulated price.");
                }
            }
            double value = qty * (selectedType == OrderType.MARKET ? stock.getPrice() : limit);
            if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Estimated order value is invalid.");
            double charges = value * OrderBook.CHARGE_RATE;
            String summary = side.getSelectedItem() + " " + qty + " " + stock.getSymbol()
                    + " · " + selectedType + " · estimate " + Formatters.money(value + charges)
                    + " including simulated charges.\nAll prices and balances are virtual simulator data.";
            if (JOptionPane.showConfirmDialog(this, summary, "Confirm virtual order",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE) != JOptionPane.OK_OPTION) return;
            var order = platform.submitOrder(trader.getId(), stock.getSymbol(), "BUY".equals(side.getSelectedItem()),
                    selectedType, qty, limit, trigger);
            notify.accept("Order " + order.getId() + " · " + order.getStatus() + " · " + stock.getSymbol());
            updateEstimate();
        } catch (NumberFormatException ex) {
            notify.accept("Enter a valid whole quantity and numeric prices.");
        } catch (InsufficientFundsException | IllegalArgumentException ex) {
            notify.accept(ex.getMessage());
        }
    }

    @Override public void refreshScreen() {
        String selected = (String) symbol.getSelectedItem();
        symbol.removeAllItems();
        platform.getStockList().forEach(stock -> symbol.addItem(stock.getSymbol()));
        if (selected != null) symbol.setSelectedItem(selected);
        updateVisibility();
        updateEstimate();
    }

    private record LiveListener(Runnable action) implements DocumentListener {
        @Override public void insertUpdate(DocumentEvent e) { action.run(); }
        @Override public void removeUpdate(DocumentEvent e) { action.run(); }
        @Override public void changedUpdate(DocumentEvent e) { action.run(); }
    }
}
