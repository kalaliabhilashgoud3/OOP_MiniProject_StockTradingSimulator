package com.stocksim.model;
public class SellOrder extends Order { public SellOrder(Trader t,Stock s,double p,int q){super(t,s,p,q);} @Override public String getSide(){return "SELL";} }
