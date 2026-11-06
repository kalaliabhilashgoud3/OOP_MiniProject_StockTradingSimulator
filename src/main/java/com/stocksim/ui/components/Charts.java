package com.stocksim.ui.components;

import com.stocksim.model.PricePoint;
import com.stocksim.model.Stock;
import com.stocksim.model.Trade;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.OHLCDataItem;
import org.jfree.data.xy.OHLCDataset;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Data-backed interactive charts. JFreeChart supplies zoom, tooltips and point inspection. */
public final class Charts {
    private Charts() { }

    public static ChartPanel prices(Stock stock, String range, String style) {
        List<PricePoint> points = filter(stock.getPriceHistory(), range);
        JFreeChart chart;
        if ("Candlestick".equals(style)) {
            chart = ChartFactory.createCandlestickChart(stock.getSymbol() + " simulated price",
                    "Time", "Price (₹)", ohlc(stock, points), true);
            if (chart.getXYPlot().getRenderer() instanceof CandlestickRenderer renderer) {
                renderer.setUpPaint(Theme.GREEN);
                renderer.setDownPaint(Theme.RED);
                renderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);
            }
        } else if ("Volume".equals(style)) {
            TimeSeries volume = new TimeSeries("Volume per tick");
            long previous = 0;
            for (PricePoint point : points) {
                long delta = Math.max(0, point.volume() - previous);
                volume.addOrUpdate(new Millisecond(Date.from(point.time())), (double) delta);
                previous = point.volume();
            }
            chart = ChartFactory.createTimeSeriesChart(stock.getSymbol() + " simulated volume",
                    "Time", "Shares", new TimeSeriesCollection(volume), false, true, true);
            styleLine(chart, Theme.BLUE);
        } else {
            TimeSeries series = new TimeSeries(stock.getSymbol());
            points.forEach(point -> series.addOrUpdate(new Millisecond(Date.from(point.time())), point.price()));
            chart = ChartFactory.createTimeSeriesChart(stock.getSymbol() + " simulated price",
                    "Time", "Price (₹)", new TimeSeriesCollection(series), false, true, true);
            styleLine(chart, stock.getChange() >= 0 ? Theme.GREEN : Theme.RED);
        }
        return panel(chart);
    }

    private static OHLCDataset ohlc(Stock stock, List<PricePoint> points) {
        OHLCDataItem[] items = new OHLCDataItem[points.size()];
        double previous = stock.getPreviousClose();
        for (int i = 0; i < points.size(); i++) {
            PricePoint point = points.get(i);
            double close = point.price();
            double open = previous;
            items[i] = new OHLCDataItem(Date.from(point.time()), open,
                    Math.max(open, close), Math.min(open, close), close, point.volume());
            previous = close;
        }
        return new DefaultOHLCDataset(stock.getSymbol(), items);
    }

    public static ChartPanel portfolioAllocation(Map<String, Integer> holdings,
                                                 Map<String, Stock> stocks) {
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        holdings.forEach((symbol, quantity) -> {
            Stock stock = stocks.get(symbol);
            if (stock != null && quantity > 0) dataset.setValue(symbol, quantity * stock.getPrice());
        });
        if (dataset.getItemCount() == 0) dataset.setValue("No holdings", 1);
        JFreeChart chart = ChartFactory.createPieChart("Portfolio allocation · simulated value",
                dataset, true, true, false);
        chart.getTitle().setPaint(Theme.NAVY);
        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setBackgroundPaint(Theme.WHITE);
        plot.setOutlineVisible(false);
        plot.setLabelPaint(Theme.TEXT);
        return panel(chart);
    }

    public static ChartPanel profitLoss(List<Trade> trades, String traderId) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        double running = 0;
        int index = 0;
        for (Trade trade : trades) {
            if (!trade.buyer().equals(traderId) && !trade.seller().equals(traderId)) continue;
            double signed = trade.seller().equals(traderId) ? 1 : -1;
            running += signed * trade.quantity() * trade.price();
            dataset.addValue(running, "Trade cash flow", Integer.toString(++index));
        }
        if (index == 0) dataset.addValue(0, "Trade cash flow", "No trades");
        JFreeChart chart = ChartFactory.createLineChart("Cumulative trade cash flow · ₹",
                "Trade", "Virtual rupees", dataset);
        styleCategory(chart);
        return panel(chart);
    }

    public static ChartPanel gainersBar(List<Stock> stocks) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        stocks.stream().limit(8).forEach(stock -> dataset.addValue(stock.getChangePercent(),
                "Change %", stock.getSymbol()));
        JFreeChart chart = ChartFactory.createBarChart("Market movers · simulated change",
                "Stock", "Change (%)", dataset);
        styleCategory(chart);
        if (chart.getCategoryPlot().getRenderer() instanceof BarRenderer renderer) renderer.setSeriesPaint(0, Theme.BLUE);
        return panel(chart);
    }

    public static ChartPanel marketDepth(List<? extends com.stocksim.model.Order> buys,
                                         List<? extends com.stocksim.model.Order> sells) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        buys.stream().limit(8).forEach(order -> dataset.addValue(order.getRemainingQuantity(), "Bids",
                String.format(java.util.Locale.ROOT, "₹%.2f", order.getPrice())));
        sells.stream().limit(8).forEach(order -> dataset.addValue(order.getRemainingQuantity(), "Asks",
                String.format(java.util.Locale.ROOT, "₹%.2f", order.getPrice())));
        if (dataset.getColumnCount() == 0) dataset.addValue(0, "No pending depth", "Empty book");
        JFreeChart chart = ChartFactory.createBarChart("Pending market depth · shares",
                "Price level", "Remaining shares", dataset);
        styleCategory(chart);
        if (chart.getCategoryPlot().getRenderer() instanceof BarRenderer renderer) {
            renderer.setSeriesPaint(0, Theme.GREEN);
            if (dataset.getRowCount() > 1) renderer.setSeriesPaint(1, Theme.RED);
        }
        return panel(chart);
    }

    public static ChartPanel portfolioValue(List<PortfolioPoint> points) {
        TimeSeries series = new TimeSeries("Portfolio value");
        points.forEach(point -> series.addOrUpdate(new Millisecond(Date.from(point.time())), point.value()));
        if (points.isEmpty()) series.addOrUpdate(new Millisecond(), 0.0);
        JFreeChart chart = ChartFactory.createTimeSeriesChart("Portfolio value · virtual ₹",
                "Time", "Value (₹)", new TimeSeriesCollection(series), false, true, true);
        styleLine(chart, Theme.BLUE);
        return panel(chart);
    }

    public static ChartPanel periodPnl(double daily, double weekly, double monthly) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(daily, "Realized P/L", "Today");
        dataset.addValue(weekly, "Realized P/L", "7 days");
        dataset.addValue(monthly, "Realized P/L", "30 days");
        JFreeChart chart = ChartFactory.createBarChart("Realized P/L · virtual ₹",
                "Period", "Profit / loss (₹)", dataset);
        styleCategory(chart);
        return panel(chart);
    }

    private static void styleCategory(JFreeChart chart) {
        chart.setBackgroundPaint(Theme.WHITE);
        chart.getTitle().setPaint(Theme.NAVY);
        chart.getCategoryPlot().setBackgroundPaint(Theme.WHITE);
        chart.getCategoryPlot().setRangeGridlinePaint(new Color(228, 235, 244));
        chart.getCategoryPlot().setDomainGridlinePaint(new Color(240, 243, 248));
    }

    private static void styleLine(JFreeChart chart, Color color) {
        chart.setBackgroundPaint(Theme.WHITE);
        chart.getTitle().setPaint(Theme.NAVY);
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Theme.WHITE);
        plot.setRangeGridlinePaint(new Color(228, 235, 244));
        plot.setDomainGridlinePaint(new Color(240, 243, 248));
        if (plot.getRenderer() instanceof XYLineAndShapeRenderer renderer) {
            renderer.setSeriesPaint(0, color);
            renderer.setSeriesStroke(0, new java.awt.BasicStroke(2f));
            renderer.setDefaultToolTipGenerator((dataset, series, item) ->
                    String.format("%s · ₹%.2f", dataset.getSeriesKey(series), dataset.getYValue(series, item)));
        }
    }

    private static ChartPanel panel(JFreeChart chart) {
        ChartPanel panel = new ChartPanel(chart, true, true, true, true, true);
        panel.setMouseWheelEnabled(true);
        panel.setBackground(Theme.WHITE);
        return panel;
    }

    private static List<PricePoint> filter(List<PricePoint> points, String range) {
        long duration = switch (range) {
            case "1D" -> 24L * 60 * 60 * 1000;
            case "1W" -> 7L * 24 * 60 * 60 * 1000;
            case "1M" -> 30L * 24 * 60 * 60 * 1000;
            case "3M" -> 90L * 24 * 60 * 60 * 1000;
            case "6M" -> 180L * 24 * 60 * 60 * 1000;
            case "1Y" -> 365L * 24 * 60 * 60 * 1000;
            default -> Long.MAX_VALUE;
        };
        if (duration == Long.MAX_VALUE || points.isEmpty()) return points;
        long after = System.currentTimeMillis() - duration;
        List<PricePoint> filtered = points.stream().filter(point -> point.time().toEpochMilli() >= after).toList();
        return filtered.isEmpty() ? List.of(points.get(points.size() - 1)) : filtered;
    }

    public record PortfolioPoint(java.time.Instant time, double value) { }
}
