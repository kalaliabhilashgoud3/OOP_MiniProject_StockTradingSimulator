package com.stocksim.util;
import com.stocksim.model.*;import com.stocksim.service.TradeHistory;import java.io.*;import java.nio.file.*;import java.util.*;
public final class CsvStore {
 private CsvStore(){}
 public static void saveTraders(Collection<Trader> traders,Path file)throws IOException{Files.createDirectories(file.getParent());try(var w=Files.newBufferedWriter(file)){w.write("id,name,wallet\n");for(Trader t:traders)w.write(t.getId()+","+t.getName()+","+t.getWallet()+"\n");}}
 public static void saveStocks(Collection<Stock> stocks,Path file)throws IOException{Files.createDirectories(file.getParent());try(var w=Files.newBufferedWriter(file)){w.write("symbol,name,price\n");for(Stock s:stocks)w.write(s.getSymbol()+","+s.getName()+","+s.getPrice()+"\n");}}
 public static void saveTrades(TradeHistory history,Path file)throws IOException{Files.createDirectories(file.getParent());try(var w=Files.newBufferedWriter(file)){w.write("id,symbol,buyer,seller,quantity,price,time\n");for(Trade t:history.getAll())w.write(t.id()+","+t.symbol()+","+t.buyer()+","+t.seller()+","+t.quantity()+","+t.price()+","+t.time()+"\n");}}
 public static List<Trader> loadTraders(Path file)throws IOException{var out=new ArrayList<Trader>();for(String l:Files.readAllLines(file).stream().skip(1).toList()){String[] p=l.split(",");out.add(new Trader(p[0],p[1],Double.parseDouble(p[2])));}return out;}
 public static List<Stock> loadStocks(Path file)throws IOException{var out=new ArrayList<Stock>();for(String l:Files.readAllLines(file).stream().skip(1).filter(x->!x.isBlank()).toList()){String[] p=l.split(",");out.add(new Stock(p[0],p[1],Double.parseDouble(p[2])));}return out;}
 public static List<Trade> loadTrades(Path file)throws IOException{var out=new ArrayList<Trade>();for(String l:Files.readAllLines(file).stream().skip(1).filter(x->!x.isBlank()).toList()){String[] p=l.split(",");out.add(new Trade(p[0],p[1],p[2],p[3],Integer.parseInt(p[4]),Double.parseDouble(p[5]),java.time.Instant.parse(p[6])));}return out;}
}
