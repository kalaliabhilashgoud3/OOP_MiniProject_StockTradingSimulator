package com.stocksim.model;

import com.stocksim.exception.InsufficientFundsException;

public class Trader {
    private final String id; private final String name; private double wallet; private final Portfolio portfolio=new Portfolio();
    public Trader(String id,String name,double wallet){if(id==null||id.isBlank()||name==null||name.isBlank()||wallet<0||!Double.isFinite(wallet))throw new IllegalArgumentException("Trader details are invalid.");this.id=id;this.name=name;this.wallet=wallet;}
    public String getId(){return id;} public String getName(){return name;} public synchronized double getWallet(){return wallet;} public Portfolio getPortfolio(){return portfolio;}
    public synchronized void debit(double amount)throws InsufficientFundsException{if(amount<0||wallet+1e-9<amount)throw new InsufficientFundsException("Not enough wallet balance. Required: ₹"+String.format("%.2f",amount));wallet-=amount;}
    public synchronized void credit(double amount){if(amount<0)throw new IllegalArgumentException("Amount cannot be negative.");wallet+=amount;}
}
