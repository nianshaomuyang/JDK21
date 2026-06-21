package com.example.jdk21.vt.order;

import com.example.jdk21.vt.order.model.OrderVO;
import com.example.jdk21.vt.order.service.OrderAggregator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * 高并发订单聚合系统 — Virtual Threads 实战 Demo — 由简单到复杂
 * ============================================================================
 *
 * 递进路线：
 *   ① 单订单查询（Before vs After）      → 最基础的对比
 *   ② 批量查询 100 订单                 → 看到并发优势
 *   ③ 高并发压测 1000 订单              → 极限验证
 *
 * 场景：电商订单详情页，需要并发调用 3 个远程服务
 *   - 用户服务（200ms）
 *   - 库存服务（150ms）
 *   - 优惠券服务（100ms）
 *
 * Before: 固定线程池 + CompletableFuture
 *   - 线程池大小 = 并发上限
 *   - 代码复杂，调试困难
 *
 * After: 虚拟线程
 *   - 每个请求一个虚拟线程，无并发上限
 *   - 代码如同单线程，清晰易懂
 *
 * 运行方式：
 *   mvn exec:java -Dexec.mainClass="com.example.jdk21.vt.order.OrderServiceDemo"
 */
public class OrderServiceDemo {

    private static final OrderAggregator aggregator = new OrderAggregator();

    // ========================================================================
    // ① 单订单查询对比：最基础的 Before vs After
    // ========================================================================

    /**
     * 单个订单查询对比
     *   - Before: CompletableFuture 编排
     *   - After:  虚拟线程直接阻塞
     *
     * 单订单时差异不大，主要看代码风格
     */
    public static void singleOrderDemo() throws Exception {
        System.out.println("① 单订单查询对比\n");

        // Before: CompletableFuture
        Instant start = Instant.now();
        OrderVO vo1 = aggregator.aggregateWithCompletableFuture("ORD-001");
        long cfTime = Duration.between(start, Instant.now()).toMillis();
        System.out.println("  [Before] CompletableFuture:");
        System.out.println("    结果: " + vo1);
        System.out.println("    耗时: " + cfTime + "ms");

        // After: 虚拟线程
        start = Instant.now();
        OrderVO vo2 = aggregator.aggregateWithVirtualThread("ORD-001");
        long vtTime = Duration.between(start, Instant.now()).toMillis();
        System.out.println("\n  [After] 虚拟线程:");
        System.out.println("    结果: " + vo2);
        System.out.println("    耗时: " + vtTime + "ms");

        System.out.println("\n  说明: 单订单差异不大，主要看代码风格");
    }

    // ========================================================================
    // ② 批量查询 100 订单：看到并发优势
    // ========================================================================

    /**
     * 批量订单查询对比 — 核心价值体现
     *
     * 100 个订单同时查询：
     *   - 线程池(10): 需要排队，串行执行 ~10 轮
     *   - 虚拟线程: 全部并发，1 轮完成
     *
     * 这里开始看到虚拟线程的真正价值
     */
    public static void batchOrderDemo() throws Exception {
        int batchSize = 100;
        System.out.println("\n② 批量订单查询对比 (" + batchSize + " 订单)\n");

        List<String> orderIds = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            orderIds.add("ORD-" + String.format("%03d", i));
        }

        // Before: 线程池 + CompletableFuture
        Instant start = Instant.now();
        List<OrderVO> results1 = aggregator.batchWithCompletableFuture(orderIds);
        long cfTime = Duration.between(start, Instant.now()).toMillis();
        long cfSuccess = results1.stream().filter(r -> r != null).count();
        System.out.println("  [Before] 线程池 (10 threads) + CompletableFuture:");
        System.out.println("    成功: " + cfSuccess + "/" + batchSize);
        System.out.println("    耗时: " + cfTime + "ms");
        System.out.println("    吞吐: " + String.format("%.0f", batchSize * 1000.0 / cfTime) + " orders/s");

        // After: 虚拟线程
        start = Instant.now();
        List<OrderVO> results2 = aggregator.batchWithVirtualThread(orderIds);
        long vtTime = Duration.between(start, Instant.now()).toMillis();
        long vtSuccess = results2.stream().filter(r -> r != null).count();
        System.out.println("\n  [After] 虚拟线程:");
        System.out.println("    成功: " + vtSuccess + "/" + batchSize);
        System.out.println("    耗时: " + vtTime + "ms");
        System.out.println("    吞吐: " + String.format("%.0f", batchSize * 1000.0 / vtTime) + " orders/s");

        // 提升倍数
        double speedup = cfTime * 1.0 / vtTime;
        System.out.println("\n  提升倍数: " + String.format("%.1fx", speedup));
        System.out.println("  说明: 线程池受限于线程数量，虚拟线程无上限");
    }

    // ========================================================================
    // ③ 高并发压测 1000 订单：极限验证
    // ========================================================================

    /**
     * 高并发压测 — 极限验证
     *
     * 1000 个订单并发查询：
     *   - 线程池(10): 需要 100 轮，耗时 ~20 秒
     *   - 虚拟线程: 全部并发，~250ms 完成
     *
     * 这是虚拟线程的杀手级场景
     */
    public static void stressTest() throws Exception {
        int totalOrders = 1000;
        System.out.println("\n③ 高并发压测 (" + totalOrders + " 订单)\n");

        List<String> orderIds = new ArrayList<>();
        for (int i = 0; i < totalOrders; i++) {
            orderIds.add("ORD-" + String.format("%04d", i));
        }

        // 只测虚拟线程（线程池 10 个线程跑 1000 订单太慢）
        Instant start = Instant.now();
        List<OrderVO> results = aggregator.batchWithVirtualThread(orderIds);
        long elapsed = Duration.between(start, Instant.now()).toMillis();
        long success = results.stream().filter(r -> r != null).count();

        System.out.println("  [虚拟线程] " + totalOrders + " 订单并发查询:");
        System.out.println("    成功: " + success + "/" + totalOrders);
        System.out.println("    耗时: " + elapsed + "ms");
        System.out.println("    吞吐: " + String.format("%.0f", totalOrders * 1000.0 / elapsed) + " orders/s");
        System.out.println("    平均: " + String.format("%.1f", elapsed * 1.0 / totalOrders) + " ms/order");

        System.out.println("\n  说明: 线程池(10) 跑 1000 订单需要 ~20 秒");
        System.out.println("        虚拟线程只需 ~250ms，提升 ~80 倍");
    }

    // ========================================================================
    // 主入口：由简单到复杂
    // ========================================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  高并发订单聚合系统 — Virtual Threads 实战 Demo  ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        // ---- ① 单订单查询 ----
        singleOrderDemo();

        // ---- ② 批量查询 ----
        batchOrderDemo();

        // ---- ③ 高并发压测 ----
        stressTest();

        // ---- 总结 ----
        System.out.println("\n=== 总结 ===");
        System.out.println("虚拟线程让并发编程回归简单:");
        System.out.println("  1. 代码如同单线程 — 无需 CompletableFuture 编排");
        System.out.println("  2. 并发无上限 — 不受线程池大小限制");
        System.out.println("  3. 资源消耗低 — 万级并发只需少量载体线程");
        System.out.println("  4. 批量场景提升 10-80 倍");
    }
}
