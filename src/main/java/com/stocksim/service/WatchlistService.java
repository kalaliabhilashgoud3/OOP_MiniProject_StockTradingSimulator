package com.stocksim.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Supports named watchlists for each sample trader account. */
public class WatchlistService {
    private final Map<String, Map<String, LinkedHashSet<String>>> byTrader = new LinkedHashMap<>();

    public synchronized List<String> getNames(String traderId) {
        return List.copyOf(listsFor(traderId).keySet());
    }

    public synchronized List<String> getSymbols(String traderId, String listName) {
        return List.copyOf(listsFor(traderId).getOrDefault(listName, new LinkedHashSet<>()));
    }

    public synchronized void create(String traderId, String listName) {
        requireName(listName);
        if (listsFor(traderId).containsKey(listName)) throw new IllegalArgumentException("That watchlist already exists.");
        listsFor(traderId).put(listName.trim(), new LinkedHashSet<>());
    }

    public synchronized void rename(String traderId, String oldName, String newName) {
        requireName(newName);
        Map<String, LinkedHashSet<String>> lists = listsFor(traderId);
        if (!lists.containsKey(oldName)) throw new IllegalArgumentException("Watchlist was not found.");
        if (!oldName.equals(newName) && lists.containsKey(newName)) throw new IllegalArgumentException("That watchlist already exists.");
        LinkedHashSet<String> symbols = lists.remove(oldName);
        lists.put(newName.trim(), symbols);
    }

    public synchronized void add(String traderId, String listName, String symbol) {
        if (!listsFor(traderId).containsKey(listName)) create(traderId, listName);
        listsFor(traderId).get(listName).add(symbol.toUpperCase());
    }

    public synchronized void remove(String traderId, String listName, String symbol) {
        listsFor(traderId).getOrDefault(listName, new LinkedHashSet<>()).remove(symbol.toUpperCase());
    }

    public synchronized Map<String, Map<String, List<String>>> snapshot() {
        Map<String, Map<String, List<String>>> copy = new LinkedHashMap<>();
        byTrader.forEach((trader, lists) -> {
            Map<String, List<String>> userLists = new LinkedHashMap<>();
            lists.forEach((name, symbols) -> userLists.put(name, List.copyOf(symbols)));
            copy.put(trader, Map.copyOf(userLists));
        });
        return Map.copyOf(copy);
    }

    public synchronized void restore(String traderId, String listName, List<String> symbols) {
        requireName(listName);
        listsFor(traderId).put(listName, new LinkedHashSet<>(symbols));
    }

    private Map<String, LinkedHashSet<String>> listsFor(String traderId) {
        return byTrader.computeIfAbsent(traderId, ignored -> {
            Map<String, LinkedHashSet<String>> defaults = new LinkedHashMap<>();
            defaults.put("My Watchlist", new LinkedHashSet<>());
            return defaults;
        });
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Enter a watchlist name.");
    }
}
