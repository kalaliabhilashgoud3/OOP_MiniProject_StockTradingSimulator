package com.stocksim.service;

import com.stocksim.model.Stock;
import java.util.LinkedHashMap;
import java.util.Map;

/** Starter listings use illustrative values only; they are not NSE or BSE quotes. */
public final class StockCatalog {
    private StockCatalog() { }

    public static Map<String, Stock> simulatedIndianStocks() {
        Map<String, Stock> stocks = new LinkedHashMap<>();
        add(stocks, "RELIANCE", "Reliance Industries", 1_420, .018, 1_900_000_000_000L);
        add(stocks, "TCS", "Tata Consultancy Services", 3_780, .014, 1_350_000_000_000L);
        add(stocks, "INFY", "Infosys", 1_560, .019, 650_000_000_000L);
        add(stocks, "HDFCBANK", "HDFC Bank", 1_690, .016, 1_250_000_000_000L);
        add(stocks, "ICICIBANK", "ICICI Bank", 1_180, .018, 820_000_000_000L);
        add(stocks, "SBIN", "State Bank of India", 760, .022, 680_000_000_000L);
        add(stocks, "ITC", "ITC Limited", 445, .012, 550_000_000_000L);
        add(stocks, "TATAMOTORS", "Tata Motors", 885, .027, 330_000_000_000L);
        add(stocks, "WIPRO", "Wipro Limited", 505, .021, 260_000_000_000L);
        add(stocks, "BHARTIARTL", "Bharti Airtel", 1_580, .017, 900_000_000_000L);
        add(stocks, "LT", "Larsen & Toubro", 3_420, .019, 470_000_000_000L);
        add(stocks, "AXISBANK", "Axis Bank", 1_080, .021, 340_000_000_000L);
        add(stocks, "ADANIENT", "Adani Enterprises", 2_640, .035, 300_000_000_000L);
        add(stocks, "MARUTI", "Maruti Suzuki India", 12_250, .017, 390_000_000_000L);
        add(stocks, "SUNPHARMA", "Sun Pharmaceutical", 1_820, .016, 440_000_000_000L);
        add(stocks, "M&M", "Mahindra & Mahindra", 2_940, .022, 360_000_000_000L);
        add(stocks, "NTPC", "NTPC Limited", 365, .015, 350_000_000_000L);
        add(stocks, "TATASTEEL", "Tata Steel", 155, .028, 190_000_000_000L);
        return stocks;
    }

    private static void add(Map<String, Stock> stocks, String symbol, String name,
                            double price, double volatility, double cap) {
        double previous = price * (1 + (symbol.hashCode() % 121 - 60) / 10_000.0);
        long volume = 1_000_000L + Math.abs(symbol.hashCode() % 8_000_000);
        stocks.put(symbol, new Stock(symbol, name, price, previous, price * .997,
                price * 1.018, price * .982, price * 1.22, price * .74,
                volume, cap, volatility));
    }
}
