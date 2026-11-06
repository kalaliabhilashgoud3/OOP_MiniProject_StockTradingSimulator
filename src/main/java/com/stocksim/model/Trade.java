package com.stocksim.model;

import java.time.Instant;
import java.util.UUID;

public record Trade(String id,String symbol,String buyer,String seller,int quantity,double price,Instant time) {
    public Trade(String symbol,String buyer,String seller,int quantity,double price){this(UUID.randomUUID().toString().substring(0,8),symbol,buyer,seller,quantity,price,Instant.now());}
}
