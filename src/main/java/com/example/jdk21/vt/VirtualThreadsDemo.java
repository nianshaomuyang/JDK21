package com.example.jdk21.vt;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ============================================================================
 * 2.3 Virtual Threads 基础演示 — 由简单到复杂
 * ============================================================================
 *
 * 递进路线：
 *   ① 创建方式（3 种）                 → 最基础的使用
 *   ② 大规模并发验证（10000 任务）      → 虚拟线程的核心价值
 *   ③ Pinning 演示（synchronized）      → 虚拟线程的坑
 *   ④ No-Pinning 演示（ReentrantLock）  → 解决方案
 *
 * 核心价值：
 *   - 虚拟线程创建成本极低（~几百字节 vs 平台线程 ~1MB）
 *   - 可以轻松创建 10000+ 并发线程
 *   - 阻塞时自动 unmount，不占载体线程
 *   - 代码如同单线程，无需 CompletableFuture 编排
 *
 * 运行方式：
 *   mvn exec:java -Dexec.mainClass="com.example.jdk21.vt.VirtualThreadsDemo"
 */
public class VirtualThreadsDemo {

    // ========================================================================
    // ① 创建方式：3 种创建虚拟线程的方式
    // ========================================================================

    /**
     * 方式 1：虚拟线程执行器（推荐）
     *   - 最简单，最常用
     *   - 每个任务自动创建一个虚拟线程
     *   - try-with-resources 自动关闭
     */
    public static long createWithExecutor(int taskCount) throws Exception {
        Instant start = Instant.now();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    Thread.sleep(Duration.ofMillis(100));
                    return Thread.currentThread().toString();
                }));
            }
            for (Future<String> f : futures) {
                f.get();
            }
        }
        return Duration.between(start, Instant.now()).toMillis();
    }

    /**
     * 方式 2：直接构建虚拟线程
     *   - 更灵活，可以指定名称、特性等
     *   - 适合需要精细控制的场景
     */
    public static void createDirectly() {
        Thread vt = Thread.ofVirtual()
                .name("my-virtual-thread")  // 指定名称
                .start(() -> {
                    System.out.println("  Running in: " + Thread.currentThread());
                    System.out.println("  Is virtual: " + Thread.currentThread().isVirtual());
                });
        try {
            vt.join();  // 等待完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 方式 3：虚拟线程工厂
     *   - 适合需要批量创建的场景
     *   - 工厂可以复用配置
     */
    public static void createWithFactory() {
        var factory = Thread.ofVirtual().factory();
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            for (int i = 0; i < 5; i++) {
                executor.submit(() -> {
                    System.out.println("  Task in: " + Thread.currentThread().getName());
                });
            }
        }
    }

    // ========================================================================
    // ② 大规模并发验证：虚拟线程的核心价值
    // ========================================================================

    /**
     * 验证虚拟线程可以轻松支撑 10000+ 并发
     *   - 平台线程：~几千个就会 OOM
     *   - 虚拟线程：10000 个轻松创建，内存占用极低
     */
    public static void massiveConcurrencyDemo() throws Exception {
        int count = 10_000;
        System.out.println("\n② 大规模并发验证: " + count + " 并发任务");

        Instant start = Instant.now();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final int taskId = i;
                futures.add(executor.submit(() -> {
                    Thread.sleep(Duration.ofMillis(50 + (taskId % 100)));
                    return taskId;
                }));
            }

            int completed = 0;
            for (Future<Integer> f : futures) {
                f.get();
                completed++;
            }

            long elapsed = Duration.between(start, Instant.now()).toMillis();
            System.out.println("  完成: " + completed + " 任务");
            System.out.println("  耗时: " + elapsed + "ms");
            System.out.println("  吞吐: " + String.format("%.0f", completed * 1000.0 / elapsed) + " tasks/s");
        }
    }

    // ========================================================================
    // ③ Pinning 演示：虚拟线程的坑
    // ========================================================================

    private static final Object SYNC_LOCK = new Object();
    private static final ReentrantLock REENTRANT_LOCK = new ReentrantLock();

    /**
     * ❌ Pinning：synchronized 内阻塞
     *   - 虚拟线程被 pin 到载体线程
     *   - 载体线程无法释放，无法执行其他虚拟线程
     *   - 导致并发能力下降
     */
    public static long pinningDemo(int taskCount) throws Exception {
        Instant start = Instant.now();
        AtomicInteger pinnedCount = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    synchronized (SYNC_LOCK) {  // ← 这里会导致 pinning
                        pinnedCount.incrementAndGet();
                        Thread.sleep(Duration.ofMillis(10));
                    }
                    return null;
                }));
            }
            for (Future<Void> f : futures) {
                f.get();
            }
        }

        long elapsed = Duration.between(start, Instant.now()).toMillis();
        System.out.println("  Pinning 任务数: " + pinnedCount.get());
        System.out.println("  耗时: " + elapsed + "ms");
        return elapsed;
    }

    // ========================================================================
    // ④ No-Pinning 演示：用 ReentrantLock 替代
    // ========================================================================

    /**
     * ✅ No-Pinning：ReentrantLock 内阻塞
     *   - 虚拟线程可以正常 unmount
     *   - 载体线程释放，可以执行其他虚拟线程
     *   - 并发能力不受影响
     */
    public static long noPinningDemo(int taskCount) throws Exception {
        Instant start = Instant.now();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    REENTRANT_LOCK.lock();
                    try {
                        Thread.sleep(Duration.ofMillis(10));
                    } finally {
                        REENTRANT_LOCK.unlock();
                    }
                    return null;
                }));
            }
            for (Future<Void> f : futures) {
                f.get();
            }
        }

        long elapsed = Duration.between(start, Instant.now()).toMillis();
        System.out.println("  耗时: " + elapsed + "ms");
        return elapsed;
    }

    // ========================================================================
    // 主入口：由简单到复杂
    // ========================================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║     Virtual Threads 基础演示 — JDK 21       ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        // ---- ① 创建方式 ----
        System.out.println("① 创建方式");

        // 方式 1：虚拟线程执行器
        System.out.println("\n  方式 1: 虚拟线程执行器（推荐）");
        long executorTime = createWithExecutor(100);
        System.out.println("  100 任务耗时: " + executorTime + "ms");

        // 方式 2：直接构建
        System.out.println("\n  方式 2: 直接构建虚拟线程");
        createDirectly();

        // 方式 3：工厂模式
        System.out.println("\n  方式 3: 虚拟线程工厂");
        createWithFactory();

        // ---- ② 大规模并发 ----
        massiveConcurrencyDemo();

        // ---- ③ Pinning 演示 ----
        System.out.println("\n③ Pinning 演示（synchronized 内阻塞）");
        int taskCount = 50;
        long pinningTime = pinningDemo(taskCount);

        // ---- ④ No-Pinning 演示 ----
        System.out.println("\n④ No-Pinning 演示（ReentrantLock）");
        long noPinningTime = noPinningDemo(taskCount);

        // ---- 对比 ----
        System.out.println("\n对比:");
        System.out.println("  synchronized (pinning):  " + pinningTime + "ms");
        System.out.println("  ReentrantLock (no pin):  " + noPinningTime + "ms");
        long diff = pinningTime - noPinningTime;
        if (diff > 0) {
            System.out.println("  ReentrantLock 快 " + diff + "ms (" +
                String.format("%.1f", (diff * 100.0 / pinningTime)) + "%)");
        }

        System.out.println("\n总结:");
        System.out.println("  1. 虚拟线程创建成本极低，可以轻松创建 10000+");
        System.out.println("  2. 阻塞时自动 unmount，不占载体线程");
        System.out.println("  3. synchronized 内阻塞会导致 pinning");
        System.out.println("  4. 用 ReentrantLock 替代 synchronized 可避免 pinning");
    }
}
