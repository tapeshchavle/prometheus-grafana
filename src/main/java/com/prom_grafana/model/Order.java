package com.prom_grafana.model;

import java.time.Instant;
import java.util.UUID;

public class Order {
    private final String  id;
    private final String  product;
    private final int     quantity;
    private       String  status;  // PENDING | COMPLETED | FAILED
    private final Instant createdAt;

    public Order(String product, int quantity) {
        this.id        = UUID.randomUUID().toString();
        this.product   = product;
        this.quantity  = quantity;
        this.status    = "PENDING";
        this.createdAt = Instant.now();
    }

    public String  getId()        { return id; }
    public String  getProduct()   { return product; }
    public int     getQuantity()  { return quantity; }
    public String  getStatus()    { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void    setStatus(String s) { this.status = s; }
}
