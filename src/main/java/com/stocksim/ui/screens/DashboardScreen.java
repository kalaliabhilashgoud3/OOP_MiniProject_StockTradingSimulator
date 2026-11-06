package com.stocksim.ui.screens;

import com.stocksim.model.MarketIndex;
import com.stocksim.model.Stock;
import com.stocksim.model.Trader;
import com.stocksim.service.AnalyticsService;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.Charts;
import com.stocksim.ui.components.Formatters;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Theme;
import com.stocksim.ui.components.Ui;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class DashboardScreen extends JPanel implements Refreshable {
    private final TradingPlatform platform;
    private final Supplier<Trader> traderSupplier;
    private final JPanel kpis = new JPanel(new GridLayout(1, 6, 12, 0));
    private final JPanel indices = new JPanel(new GridLayout(1, 3, 12, 0));
    private final JPanel chartHost = new JPanel(new BorderLayout());
    private final DefaultTableModel movers = new DefaultTableModel(
            new Object[]{"Symbol", "Company", "Price", "Change"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };

    public DashboardScreen(TradingPlatform platform, Supplier<Trader> traderSupplier) {
        this.platform = platform;
        this.traderSupplier = traderSupplier;
        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        JPanel page = Ui.page("Good morning, trader", "Your virtual portfolio and simulated Indian market at a glance.");
        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        kpis.setOpaque(false);
        indices.setOpaque(false);
        body.add(kpis, BorderLayout.NORTH);
        JPanel lower = new JPanel(new GridLayout(1, 2, 14, 0));
        lower.setOpaque(false);
        RoundedPanel performance = Ui.card("Portfolio performance · virtual value");
        chartHost.setBackground(Theme.WHITE);
        performance.add(chartHost, BorderLayout.CENTER);
        RoundedPanel marketMovers = Ui.card("Market movers · simulated");
        JTable table = new JTable(movers);
        Ui.styleTable(table);
        marketMovers.add(new JScrollPane(table), BorderLayout.CENTER);
        lower.add(performance);
        lower.add(marketMovers);
        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(indices, BorderLayout.NORTH);
        center.add(lower, BorderLayout.CENTER);
        body.add(center, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);
        refreshScreen();
    }

    @Override public void refreshScreen() {
        Trader trader = traderSupplier.get();
        if (trader == null) return;
        var analytics = new AnalyticsService(platform.getTradeHistory().getAll(),
                platform.getTransactionLedger().getAll(), platform.getStocks()).forTrader(trader);
        kpis.removeAll();
        kpis.add(metric("Available balance", Formatters.money(trader.getWallet()), Theme.BLUE));
        kpis.add(metric("Total investment", Formatters.money(analytics.investedAmount()), Theme.NAVY));
        kpis.add(metric("Portfolio value", Formatters.money(analytics.marketValue()), Theme.BLUE));
        double totalPnl = analytics.realizedProfitLoss() + analytics.unrealizedProfitLoss();
        kpis.add(metric("Total P/L", Formatters.money(totalPnl), totalPnl >= 0 ? Theme.GREEN : Theme.RED));
        kpis.add(metric("Today's P/L", Formatters.money(analytics.dailyProfitLoss()),
                analytics.dailyProfitLoss() >= 0 ? Theme.GREEN : Theme.RED));
        kpis.add(metric("Total trades", Integer.toString(analytics.totalTrades()), Theme.YELLOW));
        kpis.revalidate(); kpis.repaint();

        indices.removeAll();
        for (MarketIndex index : platform.getMarket().getIndices()) {
            indices.add(metric(index.name() + " · simulated", String.format("%,.2f", index.value())
                    + "  " + Formatters.percent(index.changePercent()),
                    index.changePercent() >= 0 ? Theme.GREEN : Theme.RED));
        }
        indices.revalidate(); indices.repaint();

        movers.setRowCount(0);
        List<Stock> ranked = platform.getStockList().stream()
                .sorted((a, b) -> Double.compare(Math.abs(b.getChangePercent()), Math.abs(a.getChangePercent())))
                .limit(10).toList();
        for (Stock stock : ranked) movers.addRow(new Object[]{stock.getSymbol(), stock.getName(),
                Formatters.money(stock.getPrice()), Formatters.percent(stock.getChangePercent())});

        chartHost.removeAll();
        List<Charts.PortfolioPoint> points = platform.getPerformanceHistory().get(trader.getId()).stream()
                .map(point -> new Charts.PortfolioPoint(point.time(), point.value())).toList();
        if (points.isEmpty()) points = List.of(new Charts.PortfolioPoint(java.time.Instant.now(),
                trader.getWallet() + trader.getPortfolio().value(platform.getStocks())));
        chartHost.add(Charts.portfolioValue(points), BorderLayout.CENTER);
        chartHost.revalidate(); chartHost.repaint();
    }

    private JPanel metric(String title, String value, Color accent) {
        RoundedPanel card = Ui.card(null);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(3, 0, 0, 0, accent),
                BorderFactory.createEmptyBorder(16, 14, 14, 14)));
        JLabel name = new JLabel(title);
        name.setForeground(Theme.MUTED);
        name.setFont(Theme.BODY);
        JLabel amount = new JLabel(value);
        amount.setForeground(Theme.TEXT);
        amount.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 16));
        card.add(name, BorderLayout.NORTH);
        card.add(amount, BorderLayout.CENTER);
        return card;
    }
}
