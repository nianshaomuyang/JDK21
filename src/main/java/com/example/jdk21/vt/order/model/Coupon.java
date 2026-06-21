package com.example.jdk21.vt.order.model;

import java.math.BigDecimal;

/**
 * 优惠券模型
 */
public record Coupon(
    String couponId,
    String type,
    BigDecimal discount,
    boolean used
) {}
