package com.example.jdk21.vt.order.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单模型
 */
public record Order(
    String orderId,
    long userId,
    String skuId,
    String couponId,
    BigDecimal amount,
    LocalDateTime createTime
) {}
