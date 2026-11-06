package com.stocksim.model;

import java.util.HashMap;
import java.util.Map;

public class Portfolio {
    private final Map<String,Integer> holdings=new HashMap<>();
    private final Map<String,Double> costBasis=new HashMap<>();
    public synchronized void add(String symbol,int quantity,double price){holdings.merge(symbol,quantity,Integer::sum);costBasis.merge(symbol,price*quantity,Double::sum);}
    public synchronized void remove(String symbol,int quantity){int left=holdings.getOrDefault(symbol,0)-quantity;if(left<0)throw new IllegalArgumentException("Not enough shares.");if(left==0){holdings.remove(symbol);costBasis.remove(symbol);}else{double old=costBasis.get(symbol);holdings.put(symbol,left);costBasis.put(symbol,old*(left/(double)(left+quantity)));}}
    public synchronized int getQuantity(String symbol){return holdings.getOrDefault(symbol,0);}
    public synchronized Map<String,Integer> getHoldings(){return Map.copyOf(holdings);}
    public synchronized double value(Map<String,Stock> stocks){return holdings.entrySet().stream().mapToDouble(e->e.getValue()*stocks.get(e.getKey()).getPrice()).sum();}
    public synchronized double cost(){return costBasis.values().stream().mapToDouble(Double::doubleValue).sum();}
}
