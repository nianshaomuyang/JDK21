package com.example.jdk21.vt.order.model;

/**
 * 库存模型
 */
public record Inventory(
    String skuId,
    int available,
    String warehouse
) {}
