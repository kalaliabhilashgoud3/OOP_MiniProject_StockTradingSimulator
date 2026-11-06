package com.stocksim.ui;

import com.stocksim.model.Stock;
import com.stocksim.model.Trader;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.Theme;
import com.stocksim.ui.screens.*;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.function.Supplier;

/** Main navigation window for the stock market simulator. */
public class MainWindow extends JFrame {
    private final TradingPlatform platform;
    private final Map<String, JPanel> screens = new LinkedHashMap<>();
    private final Map<String, Refreshable> refreshables = new LinkedHashMap<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel pageTitle = new JLabel("Dashboard");
    private final JLabel marketState = new JLabel("SIMULATED MARKET · OPEN");
    private final JLabel toast = new JLabel("Ready");
    private final javax.swing.JComboBox<String> traderChoice = new javax.swing.JComboBox<>();
    private final javax.swing.JTextField quickSearch = new javax.swing.JTextField(15);
    private final ArrayDeque<String> notificationItems = new ArrayDeque<>();
    private JButton activityButton;
    private String activeTrader;
    private String selectedSymbol;

    public MainWindow() {
        super("Stock Trading Simulator 2.0");
        Theme.install();
        try {
            platform = TradingPlatform.open(Path.of("data"));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load simulator data: " + ex.getMessage(), ex);
        }
        if (platform.getTraderList().isEmpty()) throw new IllegalStateException("No sample trader accounts were found.");
        Theme.setDark(platform.isDarkTheme());
        Theme.install();
        com.stocksim.ui.components.Formatters.setUseCurrencyCode(platform.isUseCurrencyCode());
        activeTrader = platform.getDefaultTraderId().isBlank()
                ? platform.getTraderList().get(0).getId() : platform.getDefaultTraderId();
        platform.getTraderList().forEach(t -> traderChoice.addItem(t.getId() + " · " + t.getName()));
        traderChoice.addActionListener(e -> {
            int index = traderChoice.getSelectedIndex();
            if (index >= 0 && index < platform.getTraderList().size()) {
                activeTrader = platform.getTraderList().get(index).getId();
                platform.setDefaultTraderId(activeTrader);
                refreshVisible();
            }
        });
        for (int i = 0; i < platform.getTraderList().size(); i++) {
            if (platform.getTraderList().get(i).getId().equals(activeTrader)) traderChoice.setSelectedIndex(i);
        }
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 720));
        setSize(1440, 900);
        setLocationRelativeTo(null);
        setIconImage(createIcon());
        buildScreens();
        setContentPane(buildShell());
        platform.getOrderBook().addEventListener(this::onTradingEvent);
        showScreen("Dashboard");
        platform.getPriceUpdater().setDelayMillis(platform.getMarket().getTickMillis());
        Thread worker = new Thread(platform.getPriceUpdater(), "stock-price-updater");
        worker.setDaemon(true);
        worker.start();
        new Timer(1800, e -> {
            marketState.setText("SIMULATED MARKET · " + (platform.getMarket().isMarketOpen() ? "OPEN" : "CLOSED"));
            refreshVisible();
        }).start();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { closeAndSave(); }
        });
    }

    private Supplier<Trader> traderSupplier() { return () -> platform.getTrader(activeTrader); }

    private void buildScreens() {
        addScreen("Dashboard", new DashboardScreen(platform, traderSupplier()));
        addScreen("Markets", new MarketsScreen(platform, traderSupplier(), this::showDetails, this::showTrade, this::notifyUser));
        addScreen("Watchlists", new WatchlistsScreen(platform, traderSupplier(), this::showDetails, this::showTrade, this::notifyUser));
        addScreen("Stock details", new StockDetailsScreen(platform, traderSupplier(), this::showTrade, this::notifyUser));
        addScreen("Buy / Sell", new TradeScreen(platform, traderSupplier(), this::notifyUser));
        addScreen("Portfolio", new PortfolioScreen(platform, traderSupplier(), this::showDetails, this::showTrade));
        addScreen("Orders", new OrdersScreen(platform, traderSupplier(), this::notifyUser));
        addScreen("Order book", new OrderBookScreen(platform, traderSupplier(), this::notifyUser));
        addScreen("History", new HistoryScreen(platform, traderSupplier(), this::notifyUser));
        addScreen("Market activity", new NewsScreen(platform));
        addScreen("Analytics", new AnalyticsScreen(platform, traderSupplier()));
        addScreen("Leaderboard", new LeaderboardScreen(platform));
        addScreen("Account", new AccountScreen(platform, traderSupplier(), this::notifyUser));
        addScreen("Settings", new SettingsScreen(platform, this::saveNow, this::selectDefaultTrader,
                this::rebuildForTheme, this::notifyUser));
    }

    private void addScreen(String name, JPanel screen) {
        screens.put(name, screen);
        if (screen instanceof Refreshable refreshable) refreshables.put(name, refreshable);
        content.add(screen, name);
    }

    private JPanel buildShell() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BACKGROUND);
        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.setBackground(Theme.NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));
        JLabel brand = new JLabel("◈  STOCKSIM  /  PAPER TRADING");
        brand.setForeground(Color.WHITE); brand.setFont(new Font("Segoe UI", Font.BOLD, 16));
        pageTitle.setForeground(Color.WHITE); pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 19));
        marketState.setForeground(new Color(148, 233, 198));
        JPanel centerHeader = new JPanel(new BorderLayout(20, 0)); centerHeader.setOpaque(false);
        JPanel search = new JPanel(new BorderLayout(5, 0)); search.setOpaque(false);
        quickSearch.setToolTipText("Find a stock by symbol or company name");
        JButton searchButton = new JButton("Search"); searchButton.addActionListener(e -> searchStock());
        quickSearch.addActionListener(e -> searchStock());
        search.add(quickSearch, BorderLayout.CENTER); search.add(searchButton, BorderLayout.EAST);
        JPanel right = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 9, 0)); right.setOpaque(false);
        activityButton = new JButton("Notifications");
        activityButton.setToolTipText("Show recent order and market alerts");
        activityButton.addActionListener(e -> showNotifications(activityButton));
        JButton settings = new JButton("Settings"); settings.addActionListener(e -> showScreen("Settings"));
        right.add(marketState); right.add(activityButton); right.add(traderChoice); right.add(settings);
        centerHeader.add(pageTitle, BorderLayout.WEST); centerHeader.add(search, BorderLayout.CENTER);
        header.add(brand, BorderLayout.WEST); header.add(centerHeader, BorderLayout.CENTER); header.add(right, BorderLayout.EAST);
        JPanel sidebar = new JPanel();
        sidebar.setBackground(Color.WHITE); sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(229, 235, 243)));
        sidebar.setLayout(new java.awt.GridLayout(0, 1, 0, 5));
        sidebar.setPreferredSize(new Dimension(205, 0));
        String[] names = {"Dashboard", "Markets", "Watchlists", "Stock details", "Buy / Sell", "Portfolio", "Orders", "Order book", "History", "Market activity", "Analytics", "Leaderboard", "Account", "Settings"};
        for (String name : names) {
            JButton button = new JButton(name);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setFocusPainted(false); button.setBorderPainted(false); button.setBackground(Color.WHITE); button.setForeground(Theme.TEXT);
            button.setBorder(BorderFactory.createEmptyBorder(11, 18, 11, 8));
            button.addActionListener(e -> showScreen(name));
            sidebar.add(button);
        }
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Theme.BACKGROUND); bottom.setBorder(BorderFactory.createEmptyBorder(4, 12, 5, 12));
        toast.setForeground(Theme.MUTED); bottom.add(toast, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH); root.add(sidebar, BorderLayout.WEST); root.add(content, BorderLayout.CENTER); root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private void showScreen(String name) {
        if (!screens.containsKey(name)) return;
        pageTitle.setText(name);
        cards.show(content, name);
        Refreshable screen = refreshables.get(name);
        if (screen != null) screen.refreshScreen();
    }

    private void showDetails(Stock stock) {
        selectedSymbol = stock.getSymbol();
        ((StockDetailsScreen) screens.get("Stock details")).setStock(stock);
        showScreen("Stock details");
    }
    private void showTrade(Stock stock) {
        selectedSymbol = stock.getSymbol();
        ((TradeScreen) screens.get("Buy / Sell")).setStock(stock);
        showScreen("Buy / Sell");
    }
    private void refreshVisible() {
        Refreshable current = refreshables.get(pageTitle.getText());
        if (current != null) current.refreshScreen();
    }
    private void notifyUser(String message) { toast.setText(message); }

    private void onTradingEvent(String message) {
        if (!platform.isNotificationsEnabled()) return;
        SwingUtilities.invokeLater(() -> {
            notificationItems.addFirst(java.time.LocalTime.now().withNano(0) + " · " + message);
            while (notificationItems.size() > 30) notificationItems.removeLast();
            if (activityButton != null) activityButton.setText("Notifications (" + notificationItems.size() + ")");
            notifyUser(message);
        });
    }

    private void showNotifications(java.awt.Component anchor) {
        if (notificationItems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No recent trading notifications.", "Notifications", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JTextArea text = new JTextArea(String.join("\n", notificationItems), 10, 48);
        text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
        menu.add(new JScrollPane(text));
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void selectDefaultTrader(String id) {
        activeTrader = id;
        for (int i = 0; i < platform.getTraderList().size(); i++) {
            if (platform.getTraderList().get(i).getId().equals(id)) traderChoice.setSelectedIndex(i);
        }
        refreshVisible();
    }

    private void rebuildForTheme() {
        Theme.setDark(platform.isDarkTheme());
        Theme.install();
        content.removeAll(); screens.clear(); refreshables.clear();
        buildScreens(); setContentPane(buildShell()); SwingUtilities.updateComponentTreeUI(this);
        showScreen("Settings"); revalidate(); repaint();
    }

    private void searchStock() {
        String query = quickSearch.getText().trim();
        if (query.isEmpty()) { notifyUser("Type a stock symbol or company name to search."); return; }
        Stock stock = platform.getStockList().stream()
                .filter(item -> item.getSymbol().equalsIgnoreCase(query) || item.getName().toLowerCase().contains(query.toLowerCase()))
                .findFirst().orElse(null);
        if (stock == null) { notifyUser("No matching stock found in the simulated market."); return; }
        showDetails(stock);
    }

    private void saveNow() {
        try { platform.save(); notifyUser("All simulator data saved to the data folder."); }
        catch (IOException ex) { notifyUser("Save failed: " + ex.getMessage()); }
    }

    private void closeAndSave() {
        try { platform.close(); dispose(); }
        catch (IOException ex) {
            int choice = JOptionPane.showConfirmDialog(this, "Could not save all data: " + ex.getMessage() + "\nClose anyway?", "Save error", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) dispose();
        }
    }

    private Image createIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.NAVY); g.fillRoundRect(0, 0, 64, 64, 15, 15);
        g.setColor(new Color(49, 211, 157)); g.setStroke(new java.awt.BasicStroke(5, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.drawLine(10, 47, 24, 35); g.drawLine(24, 35, 34, 40); g.drawLine(34, 40, 53, 15); g.dispose();
        return image;
    }
}
