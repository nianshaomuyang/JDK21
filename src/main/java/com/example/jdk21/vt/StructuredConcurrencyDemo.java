package com.example.jdk21.vt;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;

/**
 * ============================================================================
 * 2.6 Structured Concurrency 演示 — 由简单到复杂
 * ============================================================================
 *
 * 递进路线：
 *   ① Before: CompletableFuture 手动管理  → 看到问题
 *   ② After:  StructuredTaskScope         → 看到解决方案
 *   ③ 错误处理对比                        → 理解自动传播
 *
 * 核心思想：
 *   将并发任务视为代码块结构 —— 任务的生命周期被限定在作用域内
 *   异常自动传播，取消自动级联
 *
 * 注意：这是 Preview 特性，需要：
 *   --enable-preview --add-modules jdk.incubator.concurrent
 */
public class StructuredConcurrencyDemo {

    // 模拟远程服务调用
    static String queryUser(long userId) throws Exception {
        Thread.sleep(Duration.ofMillis(200));
        return "User-" + userId;
    }

    static Double queryBalance(long userId) throws Exception {
        Thread.sleep(Duration.ofMillis(150));
        return 12345.67;
    }

    static int queryOrderCount(long userId) throws Exception {
        Thread.sleep(Duration.ofMillis(100));
        return 42;
    }

    // ========================================================================
    // ① Before: CompletableFuture 手动管理
    // ========================================================================

    /**
     * Before: CompletableFuture 编排
     *   - 手动管理 Future 生命周期
     *   - 手动处理超时
     *   - 手动取消任务
     *   - 异常处理复杂
     */
    public static String withCompletableFuture(long userId) throws Exception {
        Instant start = Instant.now();

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> userF = pool.submit(() -> queryUser(userId));
            Future<Double> balanceF = pool.submit(() -> queryBalance(userId));
            Future<Integer> ordersF = pool.submit(() -> queryOrderCount(userId));

            // 手动 get + 超时
            String user = userF.get(5, TimeUnit.SECONDS);
            Double balance = balanceF.get(5, TimeUnit.SECONDS);
            Integer orders = ordersF.get(5, TimeUnit.SECONDS);

            long elapsed = Duration.between(start, Instant.now()).toMillis();
            System.out.println("  [CompletableFuture] " + elapsed + "ms");
            return user + " | 余额: " + balance + " | 订单数: " + orders;
        }
    }

    // ========================================================================
    // ② After: StructuredTaskScope（Preview）
    // ========================================================================

    /**
     * After: StructuredTaskScope（Preview）
     *   - 自动管理任务生命周期
     *   - 自动处理超时
     *   - 自动取消任务
     *   - 异常自动传播
     *
     * 需要 --enable-preview --add-modules jdk.incubator.concurrent
     */
    /*
    public static String withStructuredConcurrency(long userId) throws Exception {
        Instant start = Instant.now();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            // fork: 启动子任务
            Subtask<String> userSub = scope.fork(() -> queryUser(userId));
            Subtask<Double> balanceSub = scope.fork(() -> queryBalance(userId));
            Subtask<Integer> ordersSub = scope.fork(() -> queryOrderCount(userId));

            // join: 等待所有任务完成（带超时）
            scope.joinUntil(Instant.now().plusSeconds(5));

            // throwIfFailed: 任一失败则抛异常，自动取消其他任务
            scope.throwIfFailed();

            long elapsed = Duration.between(start, Instant.now()).toMillis();
            System.out.println("  [StructuredConcurrency] " + elapsed + "ms");
            return userSub.get() + " | 余额: " + balanceSub.get() + " | 订单数: " + ordersSub.get();
        }
    }
    */

    // ========================================================================
    // ③ 错误处理对比
    // ========================================================================

    /**
     * Before: 错误处理复杂
     *   - 手动 try-catch
     *   - 手动取消其他任务
     *   - 容易遗漏
     */
    public static String errorHandlingBefore(long userId) throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> userF = pool.submit(() -> queryUser(userId));
            Future<Double> balanceF = pool.submit(() -> {
                if (userId == 999) throw new RuntimeException("用户不存在");
                return queryBalance(userId);
            });

            try {
                String user = userF.get();
                Double balance = balanceF.get();
                return user + " | " + balance;
            } catch (ExecutionException e) {
                // 手动取消其他任务
                userF.cancel(true);
                throw e.getCause();
            }
        }
    }

    /**
     * After: 错误处理简洁
     *   - ShutdownOnFailure 自动取消其他任务
     *   - 自动传播异常
     *   - 不会遗漏
     */
    /*
    public static String errorHandlingAfter(long userId) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Subtask<String> userSub = scope.fork(() -> queryUser(userId));
            Subtask<Double> balanceSub = scope.fork(() -> {
                if (userId == 999) throw new RuntimeException("用户不存在");
                return queryBalance(userId);
            });

            scope.join();
            scope.throwIfFailed();  // 自动传播异常，自动取消其他任务

            return userSub.get() + " | " + balanceSub.get();
        }
    }
    */

    // ========================================================================
    // 主入口：由简单到复杂
    // ========================================================================
    public static void main(String[] args) throws Exception {
        System.out.println("=== 2.6 Structured Concurrency 演示 ===\n");

        // ---- ① Before: CompletableFuture ----
        System.out.println("① Before: CompletableFuture 手动管理");
        String result1 = withCompletableFuture(1001);
        System.out.println("  结果: " + result1);

        // ---- ② After: StructuredTaskScope ----
        System.out.println("\n② After: StructuredTaskScope（Preview）");
        System.out.println("  需要 --enable-preview --add-modules jdk.incubator.concurrent");
        System.out.println("  代码已保留为注释，取消注释后可直接运行");
        // String result2 = withStructuredConcurrency(1001);
        // System.out.println("  结果: " + result2);

        // ---- ③ 错误处理对比 ----
        System.out.println("\n③ 错误处理对比");
        System.out.println("  Before: 手动 try-catch + 手动取消");
        System.out.println("  After:  ShutdownOnFailure 自动取消 + 自动传播");

        System.out.println("\n总结:");
        System.out.println("  Structured Concurrency 让并发任务管理变得结构化");
        System.out.println("  异常自动传播，取消自动级联，不会遗漏");
    }
}
