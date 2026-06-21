package com.example.jdk21.vt;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ============================================================================
 * 2.4 Pinning 问题演示与排查 — 由简单到复杂
 * ============================================================================
 *
 * 递进路线：
 *   ① Pinning 演示（synchronized）      → 看到问题
 *   ② No-Pinning 演示（ReentrantLock）  → 看到解决方案
 *   ③ 排查方式                           → 知道怎么找问题
 *   ④ 替换指南                           → 知道怎么改代码
 *
 * 什么是 Pinning？
 *   虚拟线程在 synchronized 块内阻塞时，会被 pin 到载体线程上
 *   载体线程无法释放，无法执行其他虚拟线程
 *   导致并发能力下降，甚至死锁
 *
 * 根源：
 *   synchronized 使用对象监视器（Monitor）
 *   Monitor 依赖「哪个线程持有锁」这个信息
 *   如果虚拟线程 unmount，Monitor 的 owner 引用会失效
 *   为了维护正确性，JVM 强制虚拟线程 pin 在载体线程上
 *
 * 运行时加 JVM 参数查看 pinning 日志：
 *   java -Djdk.tracePinnedThreads=short com.example.jdk21.vt.PinningDemo
 */
public class PinningDemo {

    private static final Object SYNC_LOCK = new Object();
    private static final ReentrantLock REENTRANT_LOCK = new ReentrantLock();

    // ========================================================================
    // ① Pinning 演示：synchronized 内阻塞
    // ========================================================================

    /**
     * ❌ Pinning：synchronized 内阻塞
     *   - 虚拟线程被 pin 到载体线程
     *   - 载体线程无法释放
     *   - 其他虚拟线程无法使用这个载体线程
     *
     * 为什么会 pin？
     *   - synchronized 使用 Monitor
     *   - Monitor 需要知道「哪个线程持有锁」
     *   - 如果虚拟线程 unmount，Monitor 的 owner 引用失效
     *   - JVM 强制 pin 以维护 Monitor 正确性
     */
    public static long pinningDemo(int taskCount) throws Exception {
        Instant start = Instant.now();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    synchronized (SYNC_LOCK) {  // ← 这里会导致 pinning
                        // 在 synchronized 块内阻塞
                        // 虚拟线程被 pin，载体线程无法释放
                        Thread.sleep(Duration.ofMillis(10));
                    }
                    return null;
                }));
            }
            for (Future<Void> f : futures) {
                f.get();
            }
        }

        return Duration.between(start, Instant.now()).toMillis();
    }

    // ========================================================================
    // ② No-Pinning 演示：ReentrantLock 内阻塞
    // ========================================================================

    /**
     * ✅ No-Pinning：ReentrantLock 内阻塞
     *   - 虚拟线程可以正常 unmount
     *   - 载体线程释放，可以执行其他虚拟线程
     *   - 并发能力不受影响
     *
     * 为什么 ReentrantLock 不 pin？
     *   - ReentrantLock 的实现是虚拟线程感知的
     *   - 它不依赖「哪个线程持有锁」的 Monitor 机制
     *   - 虚拟线程可以安全地 unmount
     */
    public static long noPinningDemo(int taskCount) throws Exception {
        Instant start = Instant.now();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> {
                    REENTRANT_LOCK.lock();
                    try {
                        // 在 ReentrantLock 块内阻塞
                        // 虚拟线程可以正常 unmount，载体线程释放
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

        return Duration.between(start, Instant.now()).toMillis();
    }

    // ========================================================================
    // ③ 排查方式：怎么找到 Pinning 问题
    // ========================================================================

    /**
     * 排查 Pinning 的 3 种方式
     */
    public static void showDiagnostics() {
        System.out.println("\n③ 排查方式");

        // 方式 1：JVM 参数
        System.out.println("\n  方式 1: JVM 参数（推荐）");
        System.out.println("    java -Djdk.tracePinnedThreads=short MyApp");
        System.out.println("    输出示例:");
        System.out.println("    ** pinning: monitor-enter **");
        System.out.println("        at com.example.MyService.process(MyService.java:42)");

        // 方式 2：代码检查
        System.out.println("\n  方式 2: 代码检查");
        System.out.println("    检查所有 synchronized 块内是否有阻塞操作:");
        System.out.println("    - Thread.sleep()");
        System.out.println("    - I/O 操作（文件、网络）");
        System.out.println("    - LockSupport.park()");
        System.out.println("    - 其他线程的 join()/get()");

        // 方式 3：常见 Pinning 源
        System.out.println("\n  方式 3: 常见 Pinning 源");
        System.out.println("    - synchronized 块内阻塞（最常见）");
        System.out.println("    - JNI 调用期间");
        System.out.println("    - try-finally 中的 monitor（JDK 21）");
    }

    // ========================================================================
    // ④ 替换指南：怎么改代码
    // ========================================================================

    /**
     * 替换示例：synchronized → ReentrantLock
     */
    public static void replacementGuide() {
        System.out.println("\n④ 替换指南");

        // Before
        System.out.println("\n  Before (synchronized):");
        System.out.println("    synchronized (lock) {");
        System.out.println("        httpClient.send(request, body);  // pinning!");
        System.out.println("    }");

        // After
        System.out.println("\n  After (ReentrantLock):");
        System.out.println("    lock.lock();");
        System.out.println("    try {");
        System.out.println("        httpClient.send(request, body);  // no pinning");
        System.out.println("    } finally {");
        System.out.println("        lock.unlock();");
        System.out.println("    }");

        // 注意事项
        System.out.println("\n  注意事项:");
        System.out.println("    1. 不是所有 synchronized 都需要替换");
        System.out.println("    2. 锁内没有阻塞操作 → pinning 影响很小");
        System.out.println("    3. 只有锁内有 I/O/sleep/网络调用时才需要替换");
        System.out.println("    4. ReentrantLock 的性能与 synchronized 接近");
    }

    // ========================================================================
    // 主入口：由简单到复杂
    // ========================================================================
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║     Pinning 问题演示与排查 — JDK 21         ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        int taskCount = 50;

        // ---- ① Pinning 演示 ----
        System.out.println("\n① Pinning 演示（synchronized 内阻塞）");
        long pinningTime = pinningDemo(taskCount);
        System.out.println("  耗时: " + pinningTime + "ms");

        // ---- ② No-Pinning 演示 ----
        System.out.println("\n② No-Pinning 演示（ReentrantLock）");
        long noPinningTime = noPinningDemo(taskCount);
        System.out.println("  耗时: " + noPinningTime + "ms");

        // ---- 对比 ----
        System.out.println("\n对比:");
        System.out.println("  synchronized (pinning):  " + pinningTime + "ms");
        System.out.println("  ReentrantLock (no pin):  " + noPinningTime + "ms");
        long diff = pinningTime - noPinningTime;
        if (diff > 0) {
            System.out.println("  ReentrantLock 快 " + diff + "ms (" +
                String.format("%.1f", (diff * 100.0 / pinningTime)) + "%)");
        }

        // ---- ③ 排查方式 ----
        showDiagnostics();

        // ---- ④ 替换指南 ----
        replacementGuide();

        System.out.println("\n总结:");
        System.out.println("  1. synchronized 内阻塞 → 虚拟线程 pinning");
        System.out.println("  2. ReentrantLock 内阻塞 → 不 pinning");
        System.out.println("  3. 用 -Djdk.tracePinnedThreads=short 排查");
        System.out.println("  4. 只替换锁内有阻塞操作的 synchronized");
    }
}
