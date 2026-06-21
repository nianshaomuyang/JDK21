package com.example.jdk21.other;

import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * 3.1 Generational ZGC 演示 — 由简单到复杂
 * ============================================================================
 *
 * 递进路线：
 *   ① 短命对象场景                     → 理解 Young Generation
 *   ② 混合生命周期场景                 → 理解 Old Generation
 *   ③ 大堆内存场景                     → 理解 ZGC 的优势
 *
 * 核心价值：
 *   JDK 21 的分代 ZGC 让短命对象快速回收
 *   暂停时间 < 1ms，吞吐量提升 10-30%
 *
 * 运行时指定 GC 参数：
 *   java -XX:+UseZGC -Xlog:gc* -Xms4g -Xmx4g GcDemo
 */
public class GcDemo {

    // ========================================================================
    // ① 短命对象场景：理解 Young Generation
    // ========================================================================

    /**
     * 场景 1：大量短命对象（模拟 Web 请求）
     *   - 方法局部变量、临时对象
     *   - 方法返回后即不可达
     *   - 在 Young Generation 中快速回收
     *
     * 观察点：
     *   - Young GC 频繁但暂停极短
     *   - 大部分对象在 Young Generation 中就被回收
     */
    public static void shortLivedObjects(int iterations) {
        System.out.println("① 短命对象场景 (" + iterations + " 次迭代)");
        long start = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            // 每次迭代创建临时对象，方法返回后即不可达
            List<byte[]> temp = new ArrayList<>();
            for (int j = 0; j < 100; j++) {
                temp.add(new byte[1024]);  // 1KB 临时对象
            }
            // temp 出了作用域，成为垃圾，等待 Young GC 回收

            if (i % (iterations / 10) == 0) {
                System.out.printf("  进度: %d/%d%n", i, iterations);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  耗时: " + elapsed + "ms");
        System.out.println("  观察: Young GC 频繁但暂停极短\n");
    }

    // ========================================================================
    // ② 混合生命周期场景：理解 Old Generation
    // ========================================================================

    /**
     * 场景 2：长生命周期对象 + 短命对象混合
     *   - 长生命周期对象进入 Old Generation
     *   - 短命对象在 Young Generation 中回收
     *   - 分代 GC 只扫描 Young Generation，不扫描 Old
     *
     * 观察点：
     *   - Young GC 只扫描年轻代
     *   - Old Generation 的对象不被频繁扫描
     *   - 吞吐量提升
     */
    public static void mixedLifetimes() {
        System.out.println("② 混合生命周期场景");

        // 长生命周期对象（进入 Old Generation）
        List<byte[]> longLived = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            longLived.add(new byte[10240]);  // 10KB，存活较久
        }
        System.out.println("  长生命周期对象: " + longLived.size() + " 个 (10KB each)");

        // 短生命周期对象（在 Young Generation 中回收）
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100_000; i++) {
            byte[] temp = new byte[4096];  // 4KB 临时对象
            if (i % 10000 == 0) {
                System.out.printf("  短命对象创建: %d/%d%n", i, 100_000);
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  短命对象处理耗时: " + elapsed + "ms");
        System.out.println("  观察: Young GC 只扫描年轻代，不扫描长生命周期对象\n");

        // 保持引用，防止被回收
        System.out.println("  长生命周期对象仍存活: " + longLived.size());
    }

    // ========================================================================
    // ③ 大堆内存场景：理解 ZGC 的优势
    // ========================================================================

    /**
     * 场景 3：大堆内存使用
     *   - ZGC 适合大堆场景（几十 GB）
     *   - 暂停时间不随堆大小增长
     *   - G1 在大堆场景下暂停时间会增长
     *
     * 观察点：
     *   - ZGC 暂停时间 < 1ms，不随堆大小增长
     *   - 适合大堆、低延迟场景
     */
    public static void largeHeapDemo() {
        System.out.println("③ 大堆内存场景");
        Runtime rt = Runtime.getRuntime();
        long maxMem = rt.maxMemory() / (1024 * 1024);
        System.out.println("  最大堆: " + maxMem + " MB");

        // 分配大块内存
        List<byte[]> chunks = new ArrayList<>();
        long allocated = 0;
        try {
            while (allocated < maxMem * 0.7) {
                chunks.add(new byte[1024 * 1024]);  // 1MB
                allocated++;
            }
            System.out.println("  已分配: " + allocated + " MB");
            System.out.println("  观察: ZGC 暂停时间不随堆大小增长");
        } catch (OutOfMemoryError e) {
            System.out.println("  OOM at: " + allocated + " MB");
        }

        // 释放
        chunks.clear();
        System.gc();
        System.out.println("  已释放");
    }

    // ========================================================================
    // 主入口：由简单到复杂
    // ========================================================================
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║     Generational ZGC 演示 — JEP 439         ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        System.out.println("推荐运行参数:");
        System.out.println("  java -XX:+UseZGC -Xlog:gc* -Xms4g -Xmx4g GcDemo\n");

        // ---- ① 短命对象 ----
        shortLivedObjects(10000);

        // ---- ② 混合生命周期 ----
        mixedLifetimes();

        // ---- ③ 大堆内存 ----
        largeHeapDemo();

        // ---- 总结 ----
        System.out.println("\n=== ZGC 关键指标 ===");
        System.out.println("  GC 暂停时间: < 1ms（不随堆大小增长）");
        System.out.println("  吞吐量提升: 10-30%（相比不分代 ZGC）");
        System.out.println("  内存效率:    降低 10-20% 堆使用");
        System.out.println("  适合场景:    低延迟 + 大堆 + 高吞吐");
    }
}
