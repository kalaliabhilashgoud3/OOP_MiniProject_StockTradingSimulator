package com.stocksim.model;
public class BuyOrder extends Order { public BuyOrder(Trader t,Stock s,double p,int q){super(t,s,p,q);} @Override public String getSide(){return "BUY";} }
