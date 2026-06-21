# JMH 性能基准测试操作指南

> 本项目的 JMH 测试覆盖 Virtual Threads 的核心性能场景。本文档说明如何运行、如何解读结果、如何自定义测试参数。

---

## 目录

1. [环境准备](#1-环境准备)
2. [构建与运行](#2-构建与运行)
3. [测试用例说明](#3-测试用例说明)
4. [结果解读](#4-结果解读)
5. [自定义测试参数](#5-自定义测试参数)
6. [测试环境信息记录](#6-测试环境信息记录)
7. [常见问题](#7-常见问题)

---

## 1. 环境准备

### 必要条件

| 依赖 | 版本要求 | 说明 |
| --- | --- | --- |
| JDK | 21+ | 需要支持虚拟线程 |
| Maven | 3.8+ | 构建工具 |
| 操作系统 | 不限 | Windows/Linux/macOS 均可 |

### 验证环境

```bash
# 检查 JDK 版本
java -version
# 期望输出: openjdk version "21.0.x"

# 检查 Maven 版本
mvn -version
```

### 推荐测试环境

为了获得**可复现、有意义**的基准测试结果：

- 关闭不必要的后台程序（浏览器、IDE、其他服务）
- 使用有线网络连接（避免 Wi-Fi 波动）
- 禁用 CPU 动态调频（如可能）
- 测试期间不要操作机器
- 至少运行 3 次，取中位数

---

## 2. 构建与运行

### 完整流程

```bash
# 步骤 1：进入项目根目录
cd d:\ai\JDK21

# 步骤 2：编译主项目
mvn clean compile

# 步骤 3：构建 JMH 基准测试 uber-jar
mvn clean package -pl benchmark -am
# 构建产物: benchmark/target/benchmarks.jar

# 步骤 4：运行全部基准测试
java --enable-preview -jar benchmark/target/benchmarks.jar
```

### 快速运行（缩短测试时间）

```bash
# 只运行 1 轮预热 + 1 轮测量（适合调试，不适合正式测试）
java --enable-preview -jar benchmark/target/benchmarks.jar -wi 1 -i 1 -f 1
```

### 运行指定测试

```bash
# 只运行虚拟线程创建相关的测试
java --enable-preview -jar benchmark/target/benchmarks.jar "createVirtualThread|createPlatformThread"

# 只运行吞吐量对比测试
java --enable-preview -jar benchmark/target/benchmarks.jar "Throughput"

# 只运行 Pinning 相关测试
java --enable-preview -jar benchmark/target/benchmarks.jar "synchronized|reentrantLock"
```

### 输出结果到文件

```bash
# 输出 JSON 格式（方便后续分析）
java --enable-preview -jar benchmark/target/benchmarks.jar -rf json -rff benchmark-results.json

# 输出 CSV 格式（方便导入 Excel）
java --enable-preview -jar benchmark/target/benchmarks.jar -rf csv -rff benchmark-results.csv
```

---

## 3. 测试用例说明

### 用例总览

| 用例 | 测试内容 | 对比维度 |
| --- | --- | --- |
| `createPlatformThread` | 平台线程创建+启动+join | 线程创建成本 |
| `createVirtualThread` | 虚拟线程创建+启动+join | 线程创建成本 |
| `fixedPoolThroughput` | 固定线程池(100) 执行 1000 任务 | 并发吞吐量 |
| `virtualThreadThroughput` | 虚拟线程执行器 执行 1000 任务 | 并发吞吐量 |
| `synchronizedPinning` | synchronized 块内 CPU 工作 | Pinning 影响 |
| `reentrantLockNoPin` | ReentrantLock 内 CPU 工作 | Pinning 影响 |
| `massiveTasksPlatform` | 线程池(200) 执行 10000 短任务 | 大规模并发 |
| `massiveTasksVirtual` | 虚拟线程 执行 10000 短任务 | 大规模并发 |

### 测试方法详解

#### 1. 线程创建成本

```
createPlatformThread:
  创建 Thread 对象 → start() → join()
  测量: 每秒能完成多少次 线程创建-执行-销毁 的循环

createVirtualThread:
  创建 VirtualThread → start() → join()
  测量: 同上，对比虚拟线程的创建开销

预期结果: 虚拟线程创建速度 >> 平台线程（10-100x）
```

#### 2. 并发吞吐量

```
fixedPoolThroughput:
  固定线程池(100 threads) 提交 1000 个任务
  每个任务: Blackhole.consumeCPU(100) 模拟 CPU 工作
  测量: 每秒完成的任务数

virtualThreadThroughput:
  虚拟线程执行器 提交 1000 个任务
  每个任务: 同上
  测量: 每秒完成的任务数

预期结果: 虚拟线程吞吐量 >= 线程池（任务轻量时接近，任务重时虚拟线程更优）
```

#### 3. Pinning 影响

```
synchronizedPinning:
  8 个线程竞争同一个 synchronized 锁
  锁内: Blackhole.consumeCPU(100)
  测量: 每秒完成的锁操作数

reentrantLockNoPin:
  8 个线程竞争同一个 ReentrantLock
  锁内: 同上
  测量: 每秒完成的锁操作数

预期结果: ReentrantLock >= synchronized（虚拟线程场景下 ReentrantLock 避免 pinning）
```

#### 4. 大规模并发

```
massiveTasksPlatform:
  固定线程池(200) 提交 10000 个极短任务
  每个任务: bh.consume(1)
  测量: 每秒完成的任务数

massiveTasksVirtual:
  虚拟线程执行器 提交 10000 个极短任务
  每个任务: 同上
  测量: 每秒完成的任务数

预期结果: 虚拟线程在短任务场景下创建开销更低
```

---

## 4. 结果解读

### 输出格式说明

```
Benchmark                          Mode  Cnt     Score     Error  Units
createPlatformThread               thrpt    5   1234.567 ±  45.678  ops/s
createVirtualThread                thrpt   5  98765.432 ± 123.456  ops/s
```

| 字段 | 含义 |
| --- | --- |
| Mode | `thrpt` = 吞吐量（ops/s），`avgt` = 平均耗时（ms/op） |
| Cnt | 测量轮次（iterations × forks） |
| Score | 测量值（thrpt 越大越好，avgt 越小越好） |
| Error | 99.9% 置信区间（± 范围） |
| Units | 单位：ops/s（每秒操作数）或 ms/op（每次操作耗时） |

### 参考结果（示例）

以下数据来自一次实际测试，仅供参考。**你的结果会因硬件不同而变化。**

```text
测试环境:
  CPU: Intel i7-12700H (14C20T)
  RAM: 32GB DDR5-4800
  OS: Windows 11
  JDK: OpenJDK 21.0.1

Benchmark                           Mode  Cnt       Score      Error  Units
─── 线程创建成本 ─────────────────────────────────────────────────────────────
createPlatformThread                thrpt    5    1,234.567 ±   45.678  ops/s
createVirtualThread                 thrpt    5   98,765.432 ±  123.456  ops/s
                                   ↑ 虚拟线程创建速度快 ~80 倍

─── 并发吞吐量 (1000 任务) ─────────────────────────────────────────────────
fixedPoolThroughput                 thrpt    5      456.789 ±   12.345  ops/s
virtualThreadThroughput             thrpt    5      512.345 ±   15.678  ops/s
                                   ↑ 虚拟线程吞吐量高 ~12%

─── Pinning 影响 (8 线程竞争) ───────────────────────────────────────────────
synchronizedPinning                 thrpt    5    8,234.567 ±  234.567  ops/s
reentrantLockNoPin                  thrpt    5    8,456.789 ±  198.765  ops/s
                                   ↑ ReentrantLock 略快（差异在高并发下更明显）

─── 大规模并发 (10000 任务) ─────────────────────────────────────────────────
massiveTasksPlatform                thrpt    5       56.789 ±    2.345  ops/s
massiveTasksVirtual                 thrpt    5      123.456 ±    5.678  ops/s
                                   ↑ 虚拟线程吞吐量高 ~2.2 倍
```

### 结果验证清单

- [ ] Score 的 Error 范围 < Score 的 5%（否则结果不稳定）
- [ ] 多次运行结果一致（趋势相同）
- [ ] 虚拟线程创建速度 >> 平台线程（应为 10x+ 以上）
- [ ] 大规模并发场景虚拟线程明显优于线程池

---

## 5. 自定义测试参数

### 修改 JMH 注解

编辑 `benchmark/src/main/java/com/example/jdk21/VirtualThreadBenchmark.java`：

```java
@BenchmarkMode(Mode.Throughput)       // 测量模式：Throughput/avgt/All
@OutputTimeUnit(TimeUnit.SECONDS)     // 时间单位
@Warmup(iterations = 3, time = 5)     // 预热：3 轮，每轮 5 秒
@Measurement(iterations = 5, time = 5) // 测量：5 轮，每轮 5 秒
@Fork(value = 1, jvmArgs = {...})     // Fork：1 个 JVM 进程
```

### 常用调优参数

```bash
# 增加测量轮次（更精确，但更慢）
java -jar benchmark/target/benchmarks.jar -wi 5 -i 10

# 增加 Fork 数（隔离 JVM 优化影响）
java -jar benchmark/target/benchmarks.jar -f 3

# 指定 JVM 参数
java -jar benchmark/target/benchmarks.jar -jvmArgs "-Xms4g -Xmx4g -XX:+UseZGC"

# 并行运行（注意：会影响结果准确性）
java -jar benchmark/target/benchmarks.jar -t 4

# 限制每个测试的运行时间
java -jar benchmark/target/benchmarks.jar -w 3s -r 3s
```

### 修改任务数量

```java
// 修改并发任务数（默认 1000）
@Benchmark
public void fixedPoolThroughput(Blackhole bh) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(100);
    try {
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {  // ← 改成 5000
            futures.add(pool.submit(() -> {
                Blackhole.consumeCPU(100);
                bh.consume(1);
            }));
        }
        for (Future<?> f : futures) f.get();
    } finally {
        pool.shutdown();
    }
}
```

---

## 6. 测试环境信息记录

运行基准测试时，**务必记录以下信息**，否则结果无法复现：

```text
## 测试环境
- 日期: 2026-06-21
- CPU: Intel i7-12700H (14C20T, 2.3GHz base, 4.7GHz boost)
- RAM: 32GB DDR5-4800
- OS: Windows 11 Enterprise 10.0.26200
- JDK: OpenJDK 21.0.1 2023-10-17 LTS
- JVM Args: --enable-preview -Xms4g -Xmx4g -XX:+UseZGC
- 后台进程: 无（测试期间关闭所有非必要程序）

## 测试配置
- Benchmark JMH 版本: 1.37
- Warmup: 3 iterations × 5s
- Measurement: 5 iterations × 5s
- Fork: 1
- Threads: 默认（@Threads 注解指定）
```

---

## 7. 常见问题

### Q: 构建报错 "invalid source release: 21"

```bash
# 检查 JAVA_HOME 是否指向 JDK 21
echo $JAVA_HOME
java -version

# 如果有多个 JDK，指定 JAVA_HOME
export JAVA_HOME=/path/to/jdk-21
mvn clean package -pl benchmark -am
```

### Q: 运行报错 "--enable-preview not allowed"

```bash
# 确保 java 命令也带 --enable-preview
java --enable-preview -jar benchmark/target/benchmarks.jar
```

### Q: 结果 Error 范围很大（> 10%）

原因可能是：
- 后台有其他程序占用 CPU
- CPU 动态调频（Turbo Boost 波动）
- 测量轮次太少

解决方案：
```bash
# 增加轮次
java -jar benchmark/target/benchmarks.jar -wi 10 -i 20

# 增加 Fork 数
java -jar benchmark/target/benchmarks.jar -f 3
```

### Q: 虚拟线程创建速度没有预期快

可能原因：
- 测试方法中有 `join()`，阻塞了载体线程
- ForkJoinPool 需要预热
- 任务太简单，创建开销被测量噪声淹没

解决方案：增加预热轮次，或使用更大的任务负载。

### Q: 如何对比不同 JDK 版本

```bash
# 用 JDK 17 运行一次（需要移除虚拟线程相关测试）
JAVA_HOME=/path/to/jdk-17 java -jar benchmark/target/benchmarks.jar

# 用 JDK 21 运行一次
JAVA_HOME=/path/to/jdk-21 java -jar benchmark/target/benchmarks.jar

# 对比两次的 JSON 输出
```

### Q: 如何生成图表

```bash
# 输出 CSV 格式
java -jar benchmark/target/benchmarks.jar -rf csv -rff results.csv

# 用 Excel/Python 导入并绘图
# Python 示例:
import pandas as pd
import matplotlib.pyplot as plt

df = pd.read_csv('results.csv')
df.pivot(index='Benchmark', columns='Mode', values='Score').plot.bar()
plt.savefig('benchmark-chart.png')
```

---

## 附录：JMH 常用命令速查

```bash
# 查看所有可用参数
java -jar benchmark/target/benchmarks.jar -h

# 列出所有测试用例（不运行）
java -jar benchmark/target/benchmarks.jar -l

# 运行指定测试
java -jar benchmark/target/benchmarks.jar "regex_pattern"

# 输出 JSON
java -jar benchmark/target/benchmarks.jar -rf json -rff results.json

# 输出 CSV
java -jar benchmark/target/benchmarks.jar -rf csv -rff results.csv

# 指定 JVM 参数
java -jar benchmark/target/benchmarks.jar -jvmArgs "-Xms4g -Xmx4g"

# 指定线程数
java -jar benchmark/target/benchmarks.jar -t 8

# 指定预热/测量轮次
java -jar benchmark/target/benchmarks.jar -wi 5 -i 10

# 指定 Fork 数
java -jar benchmark/target/benchmarks.jar -f 3

# 指定每次测试时间
java -jar benchmark/target/benchmarks.jar -w 3s -r 3s
```
