package com.example.jdk21.vt.order.service;

import com.example.jdk21.vt.order.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 订单聚合服务
 * 演示 Before（线程池 + CompletableFuture）vs After（虚拟线程）的对比
 */
public class OrderAggregator {

    private final RemoteServiceSimulator remoteService = new RemoteServiceSimulator();

    // ==================== Before: CompletableFuture 编排 ====================

    /**
     * Before: 传统线程池 + CompletableFuture
     * - 需要手动编排异步链
     * - 异常处理复杂
     * - 代码可读性差
     */
    public OrderVO aggregateWithCompletableFuture(String orderId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            // 先查订单
            Order order = remoteService.queryOrder(orderId);

            // 并发查询三个服务
            Future<User> userF = CompletableFuture.supplyAsync(
                () -> {
                    try { return remoteService.queryUser(order.userId()); }
                    catch (Exception e) { throw new CompletionException(e); }
                }, pool);

            Future<Inventory> invF = CompletableFuture.supplyAsync(
                () -> {
                    try { return remoteService.queryInventory(order.skuId()); }
                    catch (Exception e) { throw new CompletionException(e); }
                }, pool);

            Future<Coupon> couponF = CompletableFuture.supplyAsync(
                () -> {
                    try { return remoteService.queryCoupon(order.couponId()); }
                    catch (Exception e) { throw new CompletionException(e); }
                }, pool);

            // 手动组装结果
            OrderVO vo = new OrderVO(order.orderId(), order.amount());
            vo.setUser(userF.get(5, TimeUnit.SECONDS));
            vo.setInventory(invF.get(5, TimeUnit.SECONDS));
            vo.setCoupon(couponF.get(5, TimeUnit.SECONDS));
            return vo;

        } finally {
            pool.shutdown();
        }
    }

    /**
     * Before: 批量查询（线程池受限）
     * 问题：线程池大小 = 10，100 个订单需要排队
     */
    public List<OrderVO> batchWithCompletableFuture(List<String> orderIds) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<OrderVO>> futures = new ArrayList<>();
            for (String orderId : orderIds) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try { return aggregateWithCompletableFuture(orderId); }
                    catch (Exception e) { throw new CompletionException(e); }
                }, pool));
            }

            List<OrderVO> results = new ArrayList<>();
            for (Future<OrderVO> f : futures) {
                try {
                    results.add(f.get(10, TimeUnit.SECONDS));
                } catch (Exception e) {
                    results.add(null);
                }
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    // ==================== After: 虚拟线程 ====================

    /**
     * After: 虚拟线程直接写阻塞代码
     * - 代码如同单线程般清晰
     * - 无需 CompletableFuture 编排
     * - 异常自然传播
     */
    public OrderVO aggregateWithVirtualThread(String orderId) throws Exception {
        // 先查订单
        Order order = remoteService.queryOrder(orderId);

        // 虚拟线程并发查询
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<User> userF = executor.submit(() -> remoteService.queryUser(order.userId()));
            Future<Inventory> invF = executor.submit(() -> remoteService.queryInventory(order.skuId()));
            Future<Coupon> couponF = executor.submit(() -> remoteService.queryCoupon(order.couponId()));

            // 直接 get()，如同单线程
            OrderVO vo = new OrderVO(order.orderId(), order.amount());
            vo.setUser(userF.get());
            vo.setInventory(invF.get());
            vo.setCoupon(couponF.get());
            return vo;
        }
    }

    /**
     * After: 批量查询（虚拟线程无上限）
     * 优势：每个订单一个虚拟线程，不受线程池大小限制
     */
    public List<OrderVO> batchWithVirtualThread(List<String> orderIds) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<OrderVO>> futures = new ArrayList<>();
            for (String orderId : orderIds) {
                futures.add(executor.submit(() -> aggregateWithVirtualThread(orderId)));
            }

            List<OrderVO> results = new ArrayList<>();
            for (Future<OrderVO> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }

    // ==================== 高级场景：嵌套虚拟线程 ====================

    /**
     * 高级场景：订单详情 + 每个商品的库存 + 物流信息
     * 多级并发，虚拟线程天然支持嵌套
     */
    public OrderVO aggregateNested(String orderId) throws Exception {
        Order order = remoteService.queryOrder(orderId);

        try (var outer = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<User> userF = outer.submit(() -> remoteService.queryUser(order.userId()));
            Future<Inventory> invF = outer.submit(() -> {
                // 内层并发：查库存 + 查仓库物流
                try (var inner = Executors.newVirtualThreadPerTaskExecutor()) {
                    Future<Inventory> inv = inner.submit(() -> remoteService.queryInventory(order.skuId()));
                    // 可以在这里再发起其他并发查询
                    return inv.get();
                }
            });
            Future<Coupon> couponF = outer.submit(() -> remoteService.queryCoupon(order.couponId()));

            OrderVO vo = new OrderVO(order.orderId(), order.amount());
            vo.setUser(userF.get());
            vo.setInventory(invF.get());
            vo.setCoupon(couponF.get());
            return vo;
        }
    }
}
