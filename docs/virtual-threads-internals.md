# Virtual Threads 源码深度解析

> 基于 OpenJDK 21 源码，从 JVM 层面理解虚拟线程的实现机制。

---

## 目录

1. [整体架构](#1-整体架构)
2. [虚拟线程的创建](#2-虚拟线程的创建)
3. [Mount / Unmount 机制](#3-mount--unmount-机制)
4. [Continuation：挂载的核心](#4-continuation挂载的核心)
5. [载体线程与 ForkJoinPool](#5-载体线程与-forkjoinpool)
6. [Pinning 的根源与排查](#6-pinning-的根源与排查)
7. [阻塞操作的处理](#7-阻塞操作的处理)
8. [源码调用链全景图](#8-源码调用链全景图)

---

## 1. 整体架构

虚拟线程的核心类关系：

```
java.lang.Thread
    └── java.lang.VirtualThread  (JVM 内部类)

java.lang.Continuation          (协程核心，存储栈帧)
    └── java.lang.Continuation.VThreadContinuation

java.lang.VirtualThread.ContinuationScope
    └── 定义 mount/unmount 的边界

java.util.concurrent.ForkJoinPool
    └── VirtualThread 的默认载体线程池
```

**关键源文件位置**（OpenJDK）：

```
src/java.base/share/classes/java/lang/VirtualThread.java
src/java.base/share/classes/java/lang/Continuation.java
src/java.base/share/classes/java/lang/Thread.java
src/hotspot/share/runtime/continuation*.cpp  (JVM native 实现)
```

---

## 2. 虚拟线程的创建

### 入口：`Thread.ofVirtual().start(task)`

```java
// Thread.Builder.OfVirtual.start()
public Thread start(Runnable task) {
    Thread vt = new VirtualThread(this, task);
    vt.start();
    return vt;
}
```

### VirtualThread 构造函数核心逻辑

```java
// VirtualThread.java (简化)
VirtualThread(Thread parent, String name, int characteristics, Runnable task) {
    super(parent, null, name);
    this.task = task;

    // 栈帧存储在 Continuation 中，而非 OS 线程栈
    this.continuation = new VThreadContinuation(task);

    // 每个虚拟线程有独立的 permits（用于 park/unpark）
    this.parkPermits = new AtomicInteger(0);
}
```

**与平台线程的关键区别**：

| 属性 | 平台线程 (Platform Thread) | 虚拟线程 (Virtual Thread) |
|------|--------------------------|-------------------------|
| 栈存储 | OS 线程栈（~1MB） | Continuation 对象（堆内存，按需增长） |
| 创建成本 | 系统调用 + 大块内存分配 | Java 对象创建（~几百字节） |
| 数量上限 | ~几千 | ~百万 |
| 调度 | OS 调度器 | JVM 内部 ForkJoinPool |

---

## 3. Mount / Unmount 机制

这是虚拟线程最核心的机制——**虚拟线程需要「挂载」到载体线程上才能执行代码**。

### mount 流程

```java
// VirtualThread.run() — 虚拟线程的入口
@Override
public void run() {
    // mount：将虚拟线程挂载到当前载体线程
    mount();
    try {
        // 执行用户任务
        task.run();
    } finally {
        // unmount：从载体线程卸载
        unmount();
    }
}
```

```java
// VirtualThread.mount() (简化)
private void mount() {
    // 1. 记录当前载体线程
    carrierThread = Thread.currentThread();

    // 2. 将 Continuation 的栈帧恢复到载体线程的栈上
    continuation.run();  // ← 这是关键！JVM 会把 Continuation 保存的栈帧"注入"到载体线程

    // 3. 设置 ThreadLocal 等上下文
    carrierThread.setVirtualThread(this);
}
```

### unmount 流程

```java
// VirtualThread.unmount() (简化)
private void unmount() {
    // 1. 保存当前栈帧到 Continuation
    continuation.yield();  // ← 关键！将载体线程栈上的帧"快照"保存到堆

    // 2. 清除载体线程关联
    carrierThread.setVirtualThread(null);
    carrierThread = null;
}
```

---

## 4. Continuation：挂载的核心

`Continuation` 是虚拟线程的**灵魂**——它保存了虚拟线程的完整栈帧状态，使得虚拟线程可以在不同载体线程之间迁移。

### Continuation 的栈帧存储

```
┌─────────────────────────────────────────────────┐
│  Continuation 对象 (堆内存)                       │
│                                                  │
│  ┌──────────────────────────────────────┐       │
│  │  Stack Chunk (堆分配的"虚拟栈")       │       │
│  │                                      │       │
│  │  ┌──────┐ ┌──────┐ ┌──────┐         │       │
│  │  │Frame1│ │Frame2│ │Frame3│  ...     │       │
│  │  │locals│ │locals│ │locals│         │       │
│  │  │stack │ │stack │ │stack │         │       │
│  │  └──────┘ └──────┘ └──────┘         │       │
│  └──────────────────────────────────────┘       │
│                                                  │
│  entry: PC, SP 等恢复点                           │
│  done: 是否已执行完毕                             │
└─────────────────────────────────────────────────┘
```

### yield() 的核心逻辑

当虚拟线程遇到阻塞操作（如 I/O、`Thread.sleep()`、`LockSupport.park()`）时：

```java
// Continuation.yield() — 从载体线程卸载
public boolean yield() {
    // 1. JVM native 调用：将当前载体线程栈上的帧复制到 Continuation 的 Stack Chunk
    //    这一步会保存：
    //    - 所有局部变量
    //    - 操作数栈
    //    - 程序计数器 (PC)
    //    - 栈指针 (SP)
    yield0(scope);

    // 2. 恢复执行后返回 true（已 yield 过）
    // 3. 如果从未 yield，返回 false
    return true;
}
```

### run() 的恢复逻辑

```java
// Continuation.run() — 挂载到载体线程
public void run() {
    // JVM native 调用：将 Stack Chunk 中的帧"注入"到载体线程的栈上
    // 恢复 PC/SP，从上次 yield 的位置继续执行
    run0(scope);
}
```

### JVM 层面的 native 实现（hotspot）

```cpp
// src/hotspot/share/runtime/continuationFreezeThaw.cpp (简化)

// 冻结（freeze）= 从载体线程栈 → Continuation Stack Chunk
void Freeze::freeze(JavaThread* thread) {
    // 1. 在堆上分配 Stack Chunk
    StackChunkFrameStream chunk(...);

    // 2. 逐帧复制：将平台线程栈上的帧"冻结"到堆
    while (!is_done()) {
        copy_frame(chunk);     // 复制帧数据
        fix_frame_links(chunk); // 修复帧间引用
        next_frame();
    }

    // 3. 设置 Continuation 的入口点
    cont->set_pc(last_frame.pc());
    cont->set_sp(last_frame.sp());
}

// 解冻（thaw）= 从 Continuation Stack Chunk → 载体线程栈
void Thaw::thaw(JavaThread* thread) {
    // 1. 从 Continuation 读取入口点
    address pc = cont->pc();

    // 2. 逐帧"解冻"：将堆上的帧复制回载体线程栈
    while (!is_done()) {
        copy_frame_from_chunk(chunk);
        next_frame();
    }

    // 3. 跳转到 PC 继续执行
    thread->set_pc(pc);
}
```

---

## 5. 载体线程与 ForkJoinPool

虚拟线程默认使用 `ForkJoinPool` 作为载体线程池：

```java
// VirtualThread.createDefaultScheduler()
private static ExecutorService createDefaultScheduler() {
    // 并行度 = CPU 核心数
    int parallelism = Runtime.getRuntime().availableProcessors();

    return new ForkJoinPool(
        parallelism,
        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
        null,
        true,  // asyncMode = true（FIFO 调度，适合 I/O 密集型）
        0,     // corePoolSize
        parallelism,  // maximumPoolSize
        1,     // minimumRunnable（至少 1 个线程可运行）
        null,  // saturate
        60,    // keepAliveTime
        TimeUnit.SECONDS
    );
}
```

### 调度流程

```
虚拟线程 VT-1 需要执行
    │
    ▼
ForkJoinPool.submit(VT-1)
    │
    ▼
载体线程 Carrier-1 取出 VT-1
    │
    ▼
mount() → 执行 VT-1 的代码
    │
    ▼
VT-1 遇到阻塞（如 I/O）
    │
    ▼
yield() → unmount() → VT-1 保存到队列
    │
    ▼
Carrier-1 空闲，可以执行其他虚拟线程
    │
    ▼
VT-1 阻塞结束 → 重新加入队列 → 等待载体线程
```

---

## 6. Pinning 的根源与排查

### 为什么 synchronized 会导致 pinning

`synchronized` 使用 **对象监视器（Object Monitor）**，而 Monitor 的实现依赖于**当前线程的身份**。当虚拟线程在 `synchronized` 块中阻塞时：

1. Monitor 需要知道「哪个线程持有锁」
2. 如果虚拟线程 unmount，Monitor 的 owner 引用会失效
3. 为了维护 Monitor 的正确性，JVM 强制虚拟线程 **pin** 在载体线程上

```cpp
// hotspot: 检查是否可以 yield
bool Continuation::can_yield(JavaThread* thread) {
    // 如果当前在 synchronized 块中 → 不能 yield
    if (thread->has_monitor_chunks()) {
        return false;  // pin!
    }
    return true;
}
```

### 排查 Pinning

```java
// 启用 pinning 日志
// java -Djdk.tracePinnedThreads=short/virtual/none ...

// short 模式输出示例：
// Thread[#22,ForkJoinPool-1-worker-3,5,CarrierThreads]
//     java.base/java.lang.VirtualThread.park(VirtualThread.java:581)
//     java.base/java.lang.LockSupport.park(LockSupport.java:341)
//     java.base/java.util.concurrent.locks.ReentrantLock$Sync.lock(ReentrantLock.java:158)
//     java.base/java.util.concurrent.locks.ReentrantLock.lock(ReentrantLock.java:322)
//     com.example.MyService.process(MyService.java:42)  ← 你的代码
//     ...
//     ** pinning: monitor-enter **
```

### 代码层面排查

```java
// 用 jcmd 查看虚拟线程状态
// jcmd <pid> Thread.dump_to_file -format=json <file>

// 编程式检查
Thread.getAllStackTraces().forEach((thread, stack) -> {
    if (thread.isVirtual()) {
        System.out.println("VT: " + thread.getName());
        for (StackTraceElement frame : stack) {
            if (frame.getClassName().contains("synchronized")) {
                System.out.println("  ⚠️ 可能的 pinning: " + frame);
            }
        }
    }
});
```

---

## 7. 阻塞操作的处理

不同阻塞操作对虚拟线程的影响：

### 7.1 I/O 阻塞

```java
// Socket I/O — 虚拟线程感知
// java.net.Socket.getInputStream().read()
// → 底层调用 VirtualThread.park()
// → 完全 unmount，不 pin

socket.read(buffer);  // ✅ 不 pin
```

**JVM 内部实现**：NIO 的 `SelectorImpl` 被修改为虚拟线程感知：

```java
// sun.nio.ch.SelectorImpl (简化)
@Override
public int select(long timeout) {
    if (Thread.currentThread().isVirtual()) {
        // 虚拟线程：注册到 NIO 事件循环，然后 park
        // park 会触发 yield → unmount
        VirtualThread vt = (VirtualThread) Thread.currentThread();
        vt.park();  // 释放载体线程
        // 阻塞结束后 unpark → remount
        return selectedKeys.size();
    } else {
        // 平台线程：传统 select
        return doSelect(timeout);
    }
}
```

### 7.2 Thread.sleep()

```java
// VirtualThread.sleep()
public static void sleep(Duration duration) throws InterruptedException {
    VirtualThread vt = current();
    if (vt != null) {
        // 虚拟线程：调度定时唤醒，然后 park（触发 yield）
        vt.sleepNanos(duration.toNanos());
        // → park() → yield() → unmount
    } else {
        // 平台线程：传统 sleep
        Thread.sleep(duration);
    }
}
```

### 7.3 Object.wait() / LockSupport.park()

```java
// LockSupport.park() — 虚拟线程的核心挂起点
public static void park(Object blocker) {
    Thread t = Thread.currentThread();
    if (t instanceof VirtualThread vt) {
        // 虚拟线程：检查是否在 synchronized 块中
        if (vt.isPinned()) {
            // pinning：不 yield，载体线程阻塞
            parkPinned(vt);
        } else {
            // 非 pinning：正常 park → yield → unmount
            vt.park();
        }
    } else {
        // 平台线程：传统 park
        parkPlatform(blocker);
    }
}
```

---

## 8. 源码调用链全景图

### 虚拟线程执行流程

```
Thread.ofVirtual().start(task)
│
├─ new VirtualThread(task)
│   └─ new VThreadContinuation(task)   // Continuation 存储栈帧
│
├─ vt.start()
│   └─ submitRunContinuation()
│       └─ ForkJoinPool.execute(this)
│
├─ [载体线程执行]
│   ├─ VirtualThread.run()
│   │   ├─ mount()                      // 挂载到载体线程
│   │   │   └─ continuation.run()       // 恢复栈帧
│   │   │       └─ thaw() [JVM native]  // 堆栈 → 载体线程栈
│   │   │
│   │   ├─ task.run()                   // 执行用户代码
│   │   │
│   │   │   // ... 用户代码执行中 ...
│   │   │
│   │   │   // 遇到阻塞操作（如 I/O）
│   │   │   ├─ LockSupport.park()
│   │   │   │   └─ vt.park()
│   │   │   │       ├─ 检查 pinning
│   │   │   │       ├─ yield()          // 卸载
│   │   │   │       │   └─ freeze() [JVM native]  // 载体线程栈 → 堆
│   │   │   │       └─ 载体线程释放，可执行其他 VT
│   │   │   │
│   │   │   // 阻塞结束
│   │   │   ├─ LockSupport.unpark(vt)
│   │   │   │   └─ vt.unpark()
│   │   │   │       └─ submitRunContinuation()  // 重新调度
│   │   │   │
│   │   │   // 继续执行...
│   │   │   └─ mount() → continuation.run() → thaw() → 继续
│   │   │
│   │   └─ unmount()                    // 卸载
│   │       └─ continuation.yield()
│   │           └─ freeze() [JVM native] // 载体线程栈 → 堆
│   │
│   └─ 载体线程回到 ForkJoinPool，等待下一个任务
│
└─ 虚拟线程执行完毕
```

### 关键源码方法速查

| 方法 | 位置 | 作用 |
|------|------|------|
| `VirtualThread.run()` | VirtualThread.java | 虚拟线程入口 |
| `VirtualThread.mount()` | VirtualThread.java | 挂载到载体线程 |
| `VirtualThread.unmount()` | VirtualThread.java | 从载体线程卸载 |
| `VirtualThread.park()` | VirtualThread.java | 挂起虚拟线程 |
| `VirtualThread.submitRunContinuation()` | VirtualThread.java | 提交到调度器 |
| `Continuation.run()` | Continuation.java | 恢复栈帧 |
| `Continuation.yield()` | Continuation.java | 保存栈帧 |
| `Continuation.run0()` | Continuation.java (native) | JVM 层栈帧恢复 |
| `Continuation.yield0()` | Continuation.java (native) | JVM 层栈帧保存 |
| `Freeze::freeze()` | continuationFreezeThaw.cpp | 栈帧冻结到堆 |
| `Thaw::thaw()` | continuationFreezeThaw.cpp | 堆栈帧解冻到载体 |
| `ForkJoinPool.execute()` | ForkJoinPool.java | 调度执行 |

---

## 总结

虚拟线程的本质是 **JVM 层面的协程**：

1. **Continuation** 保存/恢复栈帧，实现用户态的上下文切换
2. **Mount/Unmount** 机制让虚拟线程在少量载体线程上复用执行
3. **ForkJoinPool** 作为调度器，以 FIFO asyncMode 调度虚拟线程
4. **Pinning** 是 `synchronized` 的 Monitor 实现导致的限制，用 `ReentrantLock` 替代即可

理解这些源码机制，有助于：
- 正确评估虚拟线程的适用场景
- 排查 pinning 导致的性能问题
- 理解 ThreadLocal / Scoped Values 的实现差异
- 设计高性能的虚拟线程应用架构
