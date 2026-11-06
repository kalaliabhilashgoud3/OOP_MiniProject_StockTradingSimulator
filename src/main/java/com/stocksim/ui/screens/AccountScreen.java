package com.stocksim.ui.screens;

import com.stocksim.exception.InsufficientFundsException;
import com.stocksim.model.Trader;
import com.stocksim.service.TradingPlatform;
import com.stocksim.model.TransactionRecord;
import com.stocksim.ui.components.Formatters;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Theme;
import com.stocksim.ui.components.Ui;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.function.Supplier;

public class AccountScreen extends JPanel implements Refreshable {
    private final TradingPlatform platform;
    private final Supplier<Trader> traderSupplier;
    private final java.util.function.Consumer<String> notify;
    private final JLabel balance = new JLabel();
    private final JTextField amount = new JTextField("10000", 12);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Time", "Type", "Amount", "Description"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };

    public AccountScreen(TradingPlatform platform, Supplier<Trader> traderSupplier,
                         java.util.function.Consumer<String> notify) {
        this.platform = platform;
        this.traderSupplier = traderSupplier;
        this.notify = notify;
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Account", "Sample trader accounts use virtual funds only. No real money or brokerage account is connected.");
        JPanel body = new JPanel(new BorderLayout(0, 12)); body.setOpaque(false);
        JPanel top = new JPanel(new GridLayout(1, 2, 12, 0)); top.setOpaque(false);
        RoundedPanel summary = Ui.card("Account summary · virtual cash");
        balance.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 24)); balance.setForeground(Theme.NAVY);
        summary.add(balance, BorderLayout.CENTER);
        RoundedPanel controls = Ui.card("Add or withdraw virtual funds");
        JPanel actions = new JPanel(new GridLayout(0, 2, 8, 8)); actions.setOpaque(false);
        actions.add(new JLabel("Amount (₹)")); actions.add(amount);
        var deposit = Ui.button("Deposit virtual funds", true);
        var withdraw = Ui.button("Withdraw virtual funds", false);
        actions.add(deposit); actions.add(withdraw);
        controls.add(actions, BorderLayout.CENTER);
        top.add(summary); top.add(controls);
        body.add(top, BorderLayout.NORTH);
        RoundedPanel activity = Ui.card("Account activity · deposits, withdrawals, settlements and charges");
        JTable table = new JTable(model); Ui.styleTable(table);
        activity.add(new JScrollPane(table), BorderLayout.CENTER);
        body.add(activity, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);
        deposit.addActionListener(event -> transact(true));
        withdraw.addActionListener(event -> transact(false));
        refreshScreen();
    }

    private void transact(boolean deposit) {
        Trader trader = traderSupplier.get();
        try {
            double value = Double.parseDouble(amount.getText().trim());
            String prompt = (deposit ? "Deposit " : "Withdraw ") + Formatters.money(value)
                    + " of virtual funds for " + trader.getName() + "?";
            if (JOptionPane.showConfirmDialog(this, prompt, "Confirm virtual account action",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
            if (deposit) platform.getAccounts().deposit(trader, value);
            else platform.getAccounts().withdraw(trader, value);
            notify.accept(Formatters.money(value) + (deposit ? " added to" : " withdrawn from") + " virtual balance.");
            refreshScreen();
        } catch (NumberFormatException ex) {
            notify.accept("Enter a valid amount greater than zero.");
        } catch (InsufficientFundsException | IllegalArgumentException ex) {
            notify.accept(ex.getMessage());
        }
    }

    @Override public void refreshScreen() {
        Trader trader = traderSupplier.get();
        if (trader == null) return;
        balance.setText(Formatters.money(trader.getWallet()));
        model.setRowCount(0);
        for (TransactionRecord record : platform.getTransactionLedger().getAll().stream()
                .filter(item -> item.traderId().equals(trader.getId())).sorted(java.util.Comparator.comparing(TransactionRecord::time).reversed()).toList()) {
            model.addRow(new Object[]{record.time().toString().replace('T', ' ').substring(0, 19),
                    record.type(), Formatters.money(record.amount()), record.description()});
        }
    }
}
