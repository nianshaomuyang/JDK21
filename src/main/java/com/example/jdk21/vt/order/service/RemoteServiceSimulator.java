package com.example.jdk21.vt.order.service;

import com.example.jdk21.vt.order.model.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟远程服务（带延迟）
 * 实际场景中这些是 HTTP/RPC 调用
 */
public class RemoteServiceSimulator {

    private static final Map<Long, User> USERS = Map.of(
        1001L, new User(1001, "张三", "VIP", "138****1234"),
        1002L, new User(1002, "李四", "普通", "139****5678"),
        1003L, new User(1003, "王五", "SVIP", "137****9012")
    );

    private static final Map<String, Inventory> INVENTORY = Map.of(
        "SKU-001", new Inventory("SKU-001", 100, "北京仓"),
        "SKU-002", new Inventory("SKU-002", 5, "上海仓"),
        "SKU-003", new Inventory("SKU-003", 0, "广州仓")
    );

    private static final Map<String, Coupon> COUPONS = Map.of(
        "CPN-001", new Coupon("CPN-001", "满减", new BigDecimal("50.00"), false),
        "CPN-002", new Coupon("CPN-002", "折扣", new BigDecimal("20.00"), true),
        "CPN-003", new Coupon("CPN-003", "免邮", new BigDecimal("10.00"), false)
    );

    /**
     * 查询用户信息（模拟 200ms 延迟）
     */
    public User queryUser(long userId) throws Exception {
        Thread.sleep(200);
        User user = USERS.get(userId);
        if (user == null) throw new RuntimeException("用户不存在: " + userId);
        return user;
    }

    /**
     * 查询库存（模拟 150ms 延迟）
     */
    public Inventory queryInventory(String skuId) throws Exception {
        Thread.sleep(150);
        Inventory inv = INVENTORY.get(skuId);
        if (inv == null) throw new RuntimeException("SKU 不存在: " + skuId);
        return inv;
    }

    /**
     * 查询优惠券（模拟 100ms 延迟）
     */
    public Coupon queryCoupon(String couponId) throws Exception {
        Thread.sleep(100);
        Coupon coupon = COUPONS.get(couponId);
        if (coupon == null) throw new RuntimeException("优惠券不存在: " + couponId);
        return coupon;
    }

    /**
     * 查询订单（模拟 80ms 延迟）
     */
    public Order queryOrder(String orderId) throws Exception {
        Thread.sleep(80);
        return new Order(orderId, 1001L, "SKU-001", "CPN-001",
                new BigDecimal("299.00"), java.time.LocalDateTime.now());
    }
}
