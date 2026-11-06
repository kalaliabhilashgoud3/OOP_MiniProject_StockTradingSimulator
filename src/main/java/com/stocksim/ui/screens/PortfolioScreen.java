package com.stocksim.ui.screens;

import com.stocksim.model.Portfolio;
import com.stocksim.model.Stock;
import com.stocksim.model.Trader;
import com.stocksim.service.AnalyticsService;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.Charts;
import com.stocksim.ui.components.Formatters;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Theme;
import com.stocksim.ui.components.Ui;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PortfolioScreen extends JPanel implements Refreshable {
    private final TradingPlatform platform;
    private final Supplier<Trader> traderSupplier;
    private final Consumer<Stock> openDetails;
    private final Consumer<Stock> openTrade;
    private final DefaultTableModel holdings = new DefaultTableModel(new Object[]{
            "Company", "Symbol", "Quantity", "Average buy", "Current price", "Invested", "Market value", "Unrealized P/L", "Return"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JPanel cards = new JPanel(new GridLayout(1, 6, 10, 0));
    private final JPanel allocation = new JPanel(new BorderLayout());
    private final JPanel profitLoss = new JPanel(new BorderLayout());
    private final JTable table = new JTable(holdings);

    public PortfolioScreen(TradingPlatform platform, Supplier<Trader> traderSupplier,
                           Consumer<Stock> openDetails, Consumer<Stock> openTrade) {
        this.platform = platform; this.traderSupplier = traderSupplier;
        this.openDetails = openDetails; this.openTrade = openTrade;
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Portfolio", "Review cost basis, current simulated value and realized and unrealized performance.");
        JPanel body = new JPanel(new BorderLayout(0, 12)); body.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout(0, 8)); top.setOpaque(false);
        cards.setOpaque(false);
        top.add(cards, BorderLayout.NORTH);
        var sell = Ui.button("Sell selected holding", true);
        var refresh = Ui.button("Refresh portfolio", false);
        JPanel actions = Ui.row(refresh, sell);
        top.add(actions, BorderLayout.SOUTH);
        body.add(top, BorderLayout.NORTH);
        RoundedPanel tableCard = Ui.card("Holdings");
        Ui.styleTable(table);
        tableCard.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel charts = new JPanel(new GridLayout(1, 2, 12, 0)); charts.setOpaque(false);
        RoundedPanel allocationCard = Ui.card("Portfolio allocation · simulated values");
        allocationCard.add(allocation, BorderLayout.CENTER);
        RoundedPanel pnlCard = Ui.card("Trade cash flow");
        pnlCard.add(profitLoss, BorderLayout.CENTER);
        charts.add(allocationCard); charts.add(pnlCard);
        JPanel middle = new JPanel(new GridLayout(2, 1, 0, 12)); middle.setOpaque(false);
        middle.add(tableCard); middle.add(charts);
        body.add(middle, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);

        refresh.addActionListener(event -> refreshScreen());
        sell.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            String symbol = holdings.getValueAt(table.convertRowIndexToModel(row), 1).toString();
            platform.findStock(symbol).ifPresent(openTrade);
        });
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) platform.findStock(holdings.getValueAt(table.convertRowIndexToModel(row), 1).toString()).ifPresent(openDetails);
                }
            }
        });
        refreshScreen();
    }

    @Override public void refreshScreen() {
        Trader trader = traderSupplier.get();
        if (trader == null) return;
        var portfolio = trader.getPortfolio();
        double marketValue = portfolio.value(platform.getStocks());
        double invested = portfolio.cost();
        double unrealized = marketValue - invested;
        double realized = portfolio.getRealizedProfitLoss();
        double returns = invested == 0 ? 0 : unrealized / invested * 100;
        cards.removeAll();
        cards.add(card("Available cash", Formatters.money(trader.getWallet()), Theme.BLUE));
        cards.add(card("Invested", Formatters.money(invested), Theme.NAVY));
        cards.add(card("Market value", Formatters.money(marketValue), Theme.BLUE));
        cards.add(card("Realized P/L", Formatters.money(realized), realized >= 0 ? Theme.GREEN : Theme.RED));
        cards.add(card("Unrealized P/L", Formatters.money(unrealized), unrealized >= 0 ? Theme.GREEN : Theme.RED));
        cards.add(card("Return", Formatters.percent(returns), returns >= 0 ? Theme.GREEN : Theme.RED));
        cards.revalidate(); cards.repaint();

        holdings.setRowCount(0);
        for (Portfolio.Holding position : portfolio.getPositions().values().stream()
                .sorted(java.util.Comparator.comparing(Portfolio.Holding::symbol)).toList()) {
            Stock stock = platform.getStocks().get(position.symbol());
            if (stock == null) continue;
            double cost = position.quantity() * position.averagePrice();
            double value = position.quantity() * stock.getPrice();
            double pnl = value - cost;
            holdings.addRow(new Object[]{stock.getName(), stock.getSymbol(), position.quantity(),
                    Formatters.money(position.averagePrice()), Formatters.money(stock.getPrice()),
                    Formatters.money(cost), Formatters.money(value), Formatters.money(pnl),
                    Formatters.percent(cost == 0 ? 0 : pnl * 100 / cost)});
        }
        allocation.removeAll(); allocation.add(Charts.portfolioAllocation(portfolio.getHoldings(), platform.getStocks()), BorderLayout.CENTER);
        profitLoss.removeAll(); profitLoss.add(Charts.profitLoss(platform.getTradeHistory().getAll(), trader.getId()), BorderLayout.CENTER);
        allocation.revalidate(); allocation.repaint(); profitLoss.revalidate(); profitLoss.repaint();
    }

    private RoundedPanel card(String title, String value, java.awt.Color color) {
        RoundedPanel panel = Ui.card(title);
        javax.swing.JLabel label = new javax.swing.JLabel(value);
        label.setForeground(color); label.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 16));
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }
}
