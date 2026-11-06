package com.stocksim.service;
import com.stocksim.model.Stock;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
public class PriceUpdater implements Runnable {
    private final Collection<Stock> stocks;private final AtomicBoolean running=new AtomicBoolean(true);
    public PriceUpdater(Collection<Stock> stocks){this.stocks=stocks;}
    public void stop(){running.set(false);}
    @Override public void run(){while(running.get()){synchronized(stocks){Iterator<Stock> it=stocks.iterator();while(it.hasNext()){Stock s=it.next();double next=s.getPrice()*(1+ThreadLocalRandom.current().nextDouble(-.005,.005));s.setPrice(Math.max(.01,next));}}try{Thread.sleep(3000);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}}}
}
