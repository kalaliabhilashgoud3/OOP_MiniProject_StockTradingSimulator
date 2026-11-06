package com.stocksim.service;
import com.stocksim.exception.InsufficientFundsException;import com.stocksim.model.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class OrderBookTest {
 @Test void matchesCrossingOrdersAndRecordsTrade() throws Exception {var h=new TradeHistory();var b=new OrderBook(h);var s=new Stock("ABC","Alpha",100);var buyer=new Trader("B","Buyer",1000);var seller=new Trader("S","Seller",0);seller.getPortfolio().add("ABC",3,80);b.placeSell(new SellOrder(seller,s,90,2));b.placeBuy(new BuyOrder(buyer,s,100,2));assertEquals(1,h.getAll().size());assertEquals(2,buyer.getPortfolio().getQuantity("ABC"));assertEquals(180,seller.getWallet(),0.01);assertEquals(820,buyer.getWallet(),0.01);assertTrue(b.getBuys().isEmpty());}
 @Test void rejectsNegativePrice(){var s=new Stock("ABC","Alpha",100);assertThrows(IllegalArgumentException.class,()->new BuyOrder(new Trader("B","Buyer",1000),s,-1,1));}
 @Test void reportsInsufficientWallet() {var b=new OrderBook(new TradeHistory());var s=new Stock("ABC","Alpha",10);assertThrows(InsufficientFundsException.class,()->b.placeBuy(new BuyOrder(new Trader("B","Buyer",5),s,10,1)));}
 @Test void keepsNonCrossingOrdersPending() throws Exception {var b=new OrderBook(new TradeHistory());var s=new Stock("ABC","Alpha",10);var t=new Trader("B","Buyer",100);b.placeBuy(new BuyOrder(t,s,9,1));assertEquals(1,b.getBuys().size());assertTrue(b.getSells().isEmpty());}
 @Test void givesHigherBidPriority(){var b=new OrderBook(new TradeHistory());var s=new Stock("ABC","Alpha",10);var t=new Trader("B","Buyer",1000);assertDoesNotThrow(()->{b.placeBuy(new BuyOrder(t,s,10,1));b.placeBuy(new BuyOrder(t,s,20,1));});assertEquals(20,b.getBuys().get(0).getPrice());}
}
