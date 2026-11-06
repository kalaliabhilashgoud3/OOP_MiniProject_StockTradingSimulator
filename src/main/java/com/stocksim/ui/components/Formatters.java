package com.stocksim.ui.components;

import java.text.NumberFormat;
import java.util.Locale;

public final class Formatters {
    private static final NumberFormat INR = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
    private static final NumberFormat NUMBER = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
    private static volatile boolean useCurrencyCode;
    private Formatters() { }
    public static String money(double value) {
        return useCurrencyCode ? NUMBER.format(value) + " INR" : INR.format(value);
    }
    public static void setUseCurrencyCode(boolean enabled) { useCurrencyCode = enabled; }
    public static String number(long value) { return NUMBER.format(value); }
    public static String percent(double value) { return String.format(Locale.ROOT, "%+.2f%%", value); }
}
