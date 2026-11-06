package com.stocksim.model;

public enum MarketSentiment {
    BULL("Bull Market", 0.00035),
    BEAR("Bear Market", -0.00035),
    SIDEWAYS("Sideways Market", 0.0),
    HIGH_VOLATILITY("High Volatility", 0.0);

    private final String label;
    private final double drift;

    MarketSentiment(String label, double drift) {
        this.label = label;
        this.drift = drift;
    }

    public String getLabel() { return label; }
    public double getDrift() { return drift; }
}
