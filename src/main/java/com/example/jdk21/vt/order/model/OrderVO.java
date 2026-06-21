package com.example.jdk21.vt.order.model;

import java.math.BigDecimal;

/**
 * 订单聚合视图
 */
public class OrderVO {
    private String orderId;
    private BigDecimal amount;
    private User user;
    private Inventory inventory;
    private Coupon coupon;

    public OrderVO(String orderId, BigDecimal amount) {
        this.orderId = orderId;
        this.amount = amount;
    }

    public String getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public Coupon getCoupon() { return coupon; }
    public void setCoupon(Coupon coupon) { this.coupon = coupon; }

    @Override
    public String toString() {
        return String.format(
            "OrderVO{orderId='%s', amount=%s, user=%s, inventory=%s, coupon=%s}",
            orderId, amount,
            user != null ? user.name() : "null",
            inventory != null ? inventory.available() + "@" + inventory.warehouse() : "null",
            coupon != null ? coupon.type() + "(-" + coupon.discount() + ")" : "null"
        );
    }
}
