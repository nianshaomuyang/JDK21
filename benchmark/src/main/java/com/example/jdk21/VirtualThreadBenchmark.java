package com.example.jdk21;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JMH 性能基准测试
 *
 * 测试维度：
 * 1. 线程创建成本：平台线程 vs 虚拟线程
 * 2. 并发吞吐量：线程池 vs 虚拟线程执行器
 * 3. Pinning 影响：synchronized vs ReentrantLock
 * 4. 上下文切换开销
 *
 * 运行方式：
 *   mvn clean package -pl benchmark -am
 *   java -jar benchmark/target/benchmarks.jar
 *
 * 测试环境（请根据实际环境修改）：
 *   - CPU: Intel i7-12700H (14C20T)
 *   - RAM: 32GB DDR5
 *   - OS: Windows 11
 *   - JDK: 21.0.1 (OpenJDK)
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(value = 1, jvmArgs = {"--enable-preview", "-Xms4g", "-Xmx4g"})
public class VirtualThreadBenchmark {

    // ==================== 1. 线程创建成本 ====================

    /**
     * 基准：平台线程创建 + 启动 + join
     */
    @Benchmark
    @Threads(1)
    public void createPlatformThread(Blackhole bh) throws Exception {
        Thread t = new Thread(() -> bh.consume(1));
        t.start();
        t.join();
    }

    /**
     * 基准：虚拟线程创建 + 启动 + join
     */
    @Benchmark
    @Threads(1)
    public void createVirtualThread(Blackhole bh) throws Exception {
        Thread t = Thread.ofVirtual().start(() -> bh.consume(1));
        t.join();
    }

    // ==================== 2. 并发任务吞吐量 ====================

    /**
     * 基准：固定线程池 (100 threads) 执行 I/O 模拟任务
     */
    @Benchmark
    @Threads(1)
    public void fixedPoolThroughput(Blackhole bh) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(100);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                futures.add(pool.submit(() -> {
                    Blackhole.consumeCPU(100);  // 模拟 CPU 工作
                    bh.consume(1);
                }));
            }
            for (Future<?> f : futures) f.get();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * 基准：虚拟线程执行器执行同等任务
     */
    @Benchmark
    @Threads(1)
    public void virtualThreadThroughput(Blackhole bh) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                futures.add(executor.submit(() -> {
                    Blackhole.consumeCPU(100);  // 模拟 CPU 工作
                    bh.consume(1);
                }));
            }
            for (Future<?> f : futures) f.get();
        }
    }

    // ==================== 3. Pinning 影响 ====================

    private static final Object SYNC_LOCK = new Object();
    private static final ReentrantLock REENTRANT_LOCK = new ReentrantLock();

    /**
     * 基准：synchronized 阻塞（会导致 pinning）
     */
    @Benchmark
    @Threads(8)
    public void synchronizedPinning(Blackhole bh) throws Exception {
        synchronized (SYNC_LOCK) {
            Blackhole.consumeCPU(100);
            bh.consume(1);
        }
    }

    /**
     * 基准：ReentrantLock 阻塞（不 pinning）
     */
    @Benchmark
    @Threads(8)
    public void reentrantLockNoPin(Blackhole bh) throws Exception {
        REENTRANT_LOCK.lock();
        try {
            Blackhole.consumeCPU(100);
            bh.consume(1);
        } finally {
            REENTRANT_LOCK.unlock();
        }
    }

    // ==================== 4. 大规模并发 ====================

    /**
     * 基准：10000 个短任务 — 平台线程池
     */
    @Benchmark
    @Threads(1)
    public void massiveTasksPlatform(Blackhole bh) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(200);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 10_000; i++) {
                futures.add(pool.submit(() -> bh.consume(1)));
            }
            for (Future<?> f : futures) f.get();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * 基准：10000 个短任务 — 虚拟线程
     */
    @Benchmark
    @Threads(1)
    public void massiveTasksVirtual(Blackhole bh) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 10_000; i++) {
                futures.add(executor.submit(() -> bh.consume(1)));
            }
            for (Future<?> f : futures) f.get();
        }
    }

    // ==================== 启动入口 ====================

    public static void main(String[] args) throws Exception {
        var opt = new OptionsBuilder()
                .include(VirtualThreadBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
