package com.example.jdk21.vt;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ============================================================================
 * 2.6 Scoped Values 演示 — 由简单到复杂
 * ============================================================================
 *
 * 递进路线：
 *   ① ThreadLocal 基础用法              → 传统方式
 *   ② ThreadLocal 的问题                → 为什么要替代
 *   ③ ScopedValue 基础用法              → 新方式
 *   ④ 虚拟线程下的对比                  → 核心价值
 *
 * 核心思想：
 *   ScopedValue 替代 ThreadLocal：
 *   - 不可变（设置后不能修改）
 *   - 自动清理（作用域结束自动失效）
 *   - 虚拟线程友好（不会因虚拟线程数量导致内存问题）
 *
 * 注意：这是 Preview 特性，需要：
 *   --enable-preview --add-modules jdk.incubator.concurrent
 */
public class ScopedValuesDemo {

    // ========================================================================
    // ① ThreadLocal 基础用法
    // ========================================================================

    private static final ThreadLocal<String> TL_USER = new ThreadLocal<>();
    private static final ThreadLocal<Integer> TL_REQUEST_ID = new ThreadLocal<>();
    private static final AtomicInteger requestCounter = new AtomicInteger(0);

    /**
     * Before: ThreadLocal 实现请求上下文
     *   - 每个线程独立的变量副本
     *   - 必须手动 remove()，否则泄漏
     */
    public static String threadLocalWay(String userName) throws Exception {
        TL_USER.set(userName);
        TL_REQUEST_ID.set(requestCounter.incrementAndGet());
        try {
            return processRequestTL();
        } finally {
            TL_USER.remove();      // 必须手动清理！
            TL_REQUEST_ID.remove(); // 必须手动清理！
        }
    }

    private static String processRequestTL() throws Exception {
        String user = TL_USER.get();
        int reqId = TL_REQUEST_ID.get();
        return callServiceTL(user, reqId);
    }

    private static String callServiceTL(String user, int reqId) throws Exception {
        Thread.sleep(50);
        return "user=" + user + ", reqId=" + reqId;
    }

    // ========================================================================
    // ② ThreadLocal 的问题
    // ========================================================================

    /**
     * ThreadLocal 在虚拟线程下的问题：
     *   - 每个虚拟线程都有独立的 ThreadLocal 副本
     *   - 百万虚拟线程 × ThreadLocal = 内存爆炸
     *   - 需要手动 remove()，忘记就泄漏
     */
    public static void threadLocalProblemDemo() throws Exception {
        System.out.println("ThreadLocal + 10000 虚拟线程:");
        AtomicInteger leakedCount = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<Future<Void>>();
            for (int i = 0; i < 10_000; i++) {
                final int id = i;
                futures.add(executor.submit(() -> {
                    TL_USER.set("user-" + id);
                    // 故意不 remove()，模拟泄漏
                    if (id % 1000 == 0) {
                        leakedCount.incrementAndGet();
                    }
                    Thread.sleep(10);
                    return null;
                }));
            }
            for (var f : futures) f.get();
        }

        System.out.println("  潜在泄漏数: " + leakedCount.get() + " (每个虚拟线程的 TL 未清理)");
    }

    // ========================================================================
    // ③ ScopedValue 基础用法（Preview）
    // ========================================================================

    /**
     * After: ScopedValue 实现请求上下文（Preview）
     *   - 不可变（设置后不能修改）
     *   - 自动清理（作用域结束自动失效）
     *   - 虚拟线程友好（不会因虚拟线程数量导致内存问题）
     */
    /*
    private static final ScopedValue<String> SV_USER = ScopedValue.newInstance();
    private static final ScopedValue<Integer> SV_REQUEST_ID = ScopedValue.newInstance();

    public static String scopedValueWay(String userName) throws Exception {
        int reqId = requestCounter.incrementAndGet();

        // 设置值并执行作用域内的代码
        return ScopedValue.where(SV_USER, userName)
                .where(SV_REQUEST_ID, reqId)
                .call(() -> processRequestSV());
    }

    private static String processRequestSV() throws Exception {
        String user = SV_USER.get();       // 自动继承，不可变
        int reqId = SV_REQUEST_ID.get();
        return callServiceSV(user, reqId);
    }

    private static String callServiceSV(String user, int reqId) throws Exception {
        Thread.sleep(50);
        return "user=" + user + ", reqId=" + reqId;
    }
    */

    // ========================================================================
    // ④ ScopedValue 优势总结
    // ========================================================================

    public static void scopedValueAdvantageDemo() {
        System.out.println("\nScopedValue 优势:");
        System.out.println("  1. 不可变 — 设置后不可修改，天然线程安全");
        System.out.println("  2. 自动清理 — 作用域结束自动失效，零泄漏");
        System.out.println("  3. 虚拟线程友好 — 不会因虚拟线程数量导致内存问题");
        System.out.println("  4. 继承语义 — 子任务自动继承父任务的值");
    }

    // ========================================================================
    // 主入口：由简单到复杂
    // ========================================================================
    public static void main(String[] args) throws Exception {
        System.out.println("=== 2.6 Scoped Values 演示 ===\n");

        // ---- ① ThreadLocal 基础用法 ----
        System.out.println("① ThreadLocal 基础用法");
        String result1 = threadLocalWay("张三");
        System.out.println("  结果: " + result1);
        System.out.println("  说明: 必须手动 remove()，否则泄漏");

        // ---- ② ThreadLocal 的问题 ----
        System.out.println("\n② ThreadLocal 在虚拟线程下的问题");
        threadLocalProblemDemo();
        System.out.println("  说明: 百万虚拟线程 × ThreadLocal = 内存爆炸");

        // ---- ③ ScopedValue 基础用法 ----
        System.out.println("\n③ ScopedValue 基础用法（Preview）");
        System.out.println("  需要 --enable-preview --add-modules jdk.incubator.concurrent");
        System.out.println("  代码已保留为注释，取消注释后可直接运行");
        // String result2 = scopedValueWay("张三");
        // System.out.println("  结果: " + result2);

        // ---- ④ 优势总结 ----
        scopedValueAdvantageDemo();

        System.out.println("\n总结:");
        System.out.println("  ThreadLocal: 可变，手动清理，虚拟线程下 OOM 风险");
        System.out.println("  ScopedValue: 不可变，自动清理，虚拟线程友好");
    }
}
