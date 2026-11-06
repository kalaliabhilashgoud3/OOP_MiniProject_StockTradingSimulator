package com.stocksim.model;

import java.time.Instant;

public record PricePoint(Instant time, double price, long volume) { }
