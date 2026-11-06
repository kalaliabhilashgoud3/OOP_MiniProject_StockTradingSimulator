package com.stocksim.ui.screens;

import com.stocksim.model.Trader;
import com.stocksim.service.AnalyticsService;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.Formatters;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Theme;
import com.stocksim.ui.components.Ui;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Font;

public class LeaderboardScreen extends JPanel implements Refreshable {
    private final TradingPlatform platform;
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{
            "Rank", "Trader", "Virtual P/L", "Return", "Trades", "Win rate"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };

    public LeaderboardScreen(TradingPlatform platform) {
        this.platform = platform;
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Trading leaderboard", "Friendly simulator ranking. Results use virtual balances and simulated prices only.");
        JPanel body = new JPanel(new BorderLayout(0, 12)); body.setOpaque(false);
        RoundedPanel challenge = Ui.card("Practice challenge");
        JLabel description = new JLabel("Sample accounts start with virtual capital. Rank by total virtual P/L; no real trades or prizes are involved.");
        description.setForeground(Theme.MUTED);
        challenge.add(description, BorderLayout.CENTER);
        body.add(challenge, BorderLayout.NORTH);
        RoundedPanel tableCard = Ui.card("Account standings");
        JTable table = new JTable(model); Ui.styleTable(table);
        tableCard.add(new JScrollPane(table), BorderLayout.CENTER);
        body.add(tableCard, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);
        refreshScreen();
    }

    @Override public void refreshScreen() {
        model.setRowCount(0);
        var analytics = new AnalyticsService(platform.getTradeHistory().getAll(),
                platform.getTransactionLedger().getAll(), platform.getStocks());
        var rows = platform.getTraderList().stream().map(trader -> {
            var metrics = analytics.forTrader(trader);
            double pnl = metrics.realizedProfitLoss() + metrics.unrealizedProfitLoss();
            double returns = trader.getInitialBalance() == 0 ? 0 : pnl * 100 / trader.getInitialBalance();
            return new Entry(trader, pnl, returns, metrics.totalTrades(), metrics.winRate());
        }).sorted(java.util.Comparator.comparingDouble(Entry::profitLoss).reversed()).toList();
        int rank = 1;
        for (Entry row : rows) model.addRow(new Object[]{rank++, row.trader().getName(),
                Formatters.money(row.profitLoss()), Formatters.percent(row.returns()),
                row.trades(), Formatters.percent(row.winRate())});
    }

    private record Entry(Trader trader, double profitLoss, double returns, int trades, double winRate) { }
}
