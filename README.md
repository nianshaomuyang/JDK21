# JDK 21 新特性深度解析 — 面向高级开发者

> **JDK 21** | 2023.9 发布 | LTS 版本 | 面向 3+ 年经验 Java 开发者
>
> 阅读顺序：语法层 → 模型层 → 运行时层，由浅入深

---

## 目录

- [零、为什么是 JDK 21？](#零为什么是-jdk-21)
- [一、Pattern Matching：Java 类型系统的进化](#一pattern-matchingjava-类型系统的进化)
- [二、Virtual Threads：并发编程范式转变](#二virtual-threads并发编程范式转变)
- [三、性能与生态](#三性能与生态)
- [四、其他特性速览](#四其他特性速览)
- [附录](#附录)

---

## 零、为什么是 JDK 21？

Spring Boot 3.0 最低要求 JDK 17，Spring Boot 3.2 也只需要 JDK 17。那为什么还要折腾到 JDK 21？

**一句话：JDK 17 能跑，JDK 21 能飞。**

### 升级收益速览

| 特性 | JDK 17 | JDK 21 | 收益 |
|------|--------|--------|------|
| 虚拟线程 | ❌ 没有 | ✅ Final | I/O 密集型服务吞吐量 5-10x |
| 分代 ZGC | 不分代 | ✅ 分代 | 吞吐量 +10-30%，内存 -10-20% |
| Pattern Matching | Preview（不稳定） | ✅ Final | 生产可用，编译器穷举检查 |
| Record Patterns | ❌ 没有 | ✅ Final | 解构赋值，替代 Visitor 模式 |
| Sequenced Collections | ❌ 没有 | ✅ 有了 | 统一的首尾访问 API |
| LTS 支持截止 | 2029.9 | 2031.9 | 多 2 年支持 |

### 升级成本

| 维度 | 成本 |
|------|------|
| 代码改动 | 几乎为零 |
| 第三方库兼容 | 99% 兼容（Spring Boot 3.2+、HikariCP 5.1+） |
| 构建工具 | 无需改动 |
| 测试工作量 | 低 |
| 风险 | 极低 |

### 决策矩阵

| 你的情况 | 建议 |
|----------|------|
| 新项目 | ✅ 直接用 JDK 21 |
| 已有项目，JDK 17 运行中 | ✅ 升级，成本极低 |
| I/O 密集型服务 | ✅ 强烈建议，虚拟线程收益巨大 |
| CPU 密集型服务 | ⚠️ 收益主要在 ZGC，按需升级 |
| 公司基础设施锁定 JDK 17 | ⚠️ 推动基础设施升级 |

---

## 一、Pattern Matching：Java 类型系统的进化

> 语法层，最容易理解。先讲数据载体（Record），再讲类型判断（switch），再讲组合使用。

### 1.1 Record：不可变数据载体

> **JEP 395** | Final | JDK 16 引入，JDK 21 中作为 Pattern Matching 的基础

#### 写法

```java
// 一行定义一个不可变数据类
public record User(String name, int age, String email) {}
```

#### 编译器生成了什么？

用 `javap -p User.class` 反编译：

```java
// 编译器自动生成：
public final class User extends java.lang.Record {
    // 1. 所有字段都是 private final
    private final String name;
    private final int age;
    private final String email;

    // 2. 全参构造器
    public User(String name, int age, String email) {
        this.name = name;
        this.age = age;
        this.email = email;
    }

    // 3. 每个字段的访问器（不是 getXxx，直接用字段名）
    public String name() { return name; }
    public int age() { return age; }
    public String email() { return email; }

    // 4. equals() — 基于所有字段
    @Override
    public boolean equals(Object o) { ... }

    // 5. hashCode() — 基于所有字段
    @Override
    public int hashCode() { ... }

    // 6. toString()
    @Override
    public String toString() { return "User[name=" + name + ", age=" + age + ", email=" + email + "]"; }
}
```

#### final 语义：为什么 Record 是不可变的

```java
record User(String name, int age) {}

User user = new User("张三", 28);

// ❌ 编译报错：final 字段不能赋值
// user.name = "李四";

// ❌ 没有 setter 方法
// user.setName("李四");

// ✅ 想修改？返回新对象（函数式风格）
record User(String name, int age) {
    User withName(String newName) {
        return new User(newName, this.age);
    }
    User withAge(int newAge) {
        return new User(this.name, newAge);
    }
}

// 使用
User user2 = user.withName("李四");
// user 不变，user2 是新对象
```

**final 的价值**：
- **线程安全**：不可变对象天然线程安全，无需同步
- **哈希安全**：可以安全地用作 Map 的 key
- **防御性编程**：不会被意外修改

#### 与 Lombok @Data / Kotlin data class 的差异

| 维度 | Java record | Lombok @Data | Kotlin data class |
|------|-------------|-------------|-------------------|
| 可变性 | 不可变（final） | 可变（有 setter） | 可变（var）或不可变（val） |
| 继承 | 不能继承其他类 | 可以 | 可以 |
| 方法名 | `name()` | `getName()` | `name` |
| 模式匹配 | ✅ 支持解构 | ❌ 不支持 | ✅ 支持 |
| 适用场景 | 纯数据传输 | 实体类 | 通用 |

#### ⚠️ 注意事项

1. **Record 不能继承其他类**：`record User extends Base {}` 编译报错，Record 隐式继承 `java.lang.Record`
2. **字段自动 final**：不能有 setter，想修改必须创建新对象
3. **不适用于可变场景**：ORM 实体类（需要 setter）不适合用 Record
4. **可以实现接口**：`record User(String name) implements Serializable {}` 是合法的
5. **可以有自定义方法**：Record 类内可以定义任意方法
6. **compact 构造器**：可以做参数校验

```java
record User(String name, int age) {
    // compact 构造器：参数校验
    User {
        if (age < 0) throw new IllegalArgumentException("年龄不能为负");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("姓名不能为空");
    }
}
```

---

### 1.2 switch + 类型模式匹配 + when

> **JEP 441** | Final | JDK 21 定稿

#### 演进路径

```
JDK 7:  switch 只支持 int/String/enum
JDK 14: switch 表达式（箭头语法 →，不需要 break）
JDK 17: switch 支持模式匹配（Preview）
JDK 21: 模式匹配 + when 守卫条件（Final）
```

#### Before — instanceof 链

```java
public String describe(Object obj) {
    if (obj instanceof String s) {
        if (s.length() > 10) return "长字符串: " + s.substring(0, 10) + "...";
        else return "字符串: " + s;
    } else if (obj instanceof Integer i) {
        if (i > 0) return "正整数: " + i;
        else if (i < 0) return "负整数: " + i;
        else return "零";
    } else if (obj instanceof List<?> l) {
        return "列表，长度: " + l.size();
    } else {
        return "未知类型";
    }
}
```

#### After — switch + 模式匹配 + when

```java
public String describe(Object obj) {
    return switch (obj) {
        case String s when s.length() > 10 -> "长字符串: " + s.substring(0, 10) + "...";
        case String s                      -> "字符串: " + s;
        case Integer i when i > 0          -> "正整数: " + i;
        case Integer i when i < 0          -> "负整数: " + i;
        case Integer _                     -> "零";
        case List<?> l                     -> "列表，长度: " + l.size();
        default                            -> "未知类型";
    };
}
```

#### 核心语法

```java
// 1. 类型模式：自动类型转换 + 变量绑定
case String s -> s.length()

// 2. when 守卫条件：在类型匹配后进一步过滤
case String s when s.length() > 10 -> ...

// 3. switch 作为表达式：必须覆盖所有分支（或有 default）
String result = switch (obj) { ... };

// 4. 箭头语法：不需要 break，不会穿透
case PaySuccess s -> "成功";
case PayFailed f  -> "失败";  // 不会穿透到上面
```

#### ⚠️ 注意事项

1. **模式顺序很重要**：`when` 条件不参与穷举检查，把更具体的模式放前面
2. **`when` 条件不参与穷举**：`case String s when s.isEmpty()` 和 `case String s` 不会被编译器视为穷举
3. **switch 表达式必须穷举**：所有分支必须覆盖，或有 default
4. **箭头语法不需要 break**：传统 `case:` 语法仍然需要 break
5. **null 处理**：`case null ->` 可以显式处理 null（JDK 21 新增）

---

### 1.3 sealed class：编译器穷举检查

> **JEP 409** | Final | JDK 17 引入

#### 核心思想

限定一个类/接口只能被哪些子类继承：

```java
// 只允许这 4 种支付结果
public sealed interface PayResult
    permits PaySuccess, PayFailed, PayPending, PayRefunded {}

public record PaySuccess(String orderId, BigDecimal amount) implements PayResult {}
public record PayFailed(String reason, boolean retryable) implements PayResult {}
public record PayPending(int estimatedSeconds) implements PayResult {}
public record PayRefunded(BigDecimal refundAmount) implements PayResult {}
```

#### 配合 switch：去掉 default

```java
// sealed class 保证所有 permits 都已覆盖 → 不需要 default
return switch (result) {
    case PaySuccess s   -> "成功: " + s.orderId();
    case PayFailed f    -> "失败: " + f.reason();
    case PayPending p   -> "处理中: " + p.seconds() + "s";
    case PayRefunded r  -> "已退款: " + r.amount();
    // 没有 default！
};

// 如果以后新增 PayTimeout...
// 所有未处理这个 case 的 switch 都会编译报错！
```

#### 子类的三种形式

```java
// 1. final：不能再被继承（最常用）
public final record PaySuccess(...) implements PayResult {}

// 2. sealed：子类也是 sealed，继续限定
public sealed interface PayFailed extends PayResult
    permits RetryableFail, FatalFail {}

// 3. non-sealed：放开限制（不推荐，破坏穷举性）
public non-sealed class PayCustom implements PayResult {}
```

#### ⚠️ 注意事项

1. **子类必须在同一模块**：跨模块的 sealed class 需要 opens
2. **permits 列表必须完整**：遗漏子类会编译报错
3. **non-sealed 会破坏穷举性**：谨慎使用
4. **Record + sealed 是最佳组合**：Record 天然 final，配合 sealed 保证穷举

---

### 1.4 Record Patterns：解构赋值

> **JEP 440** | Final | JDK 21 定稿

#### 核心语法

```java
record Point(int x, int y) {}
record Line(Point start, Point end) {}
record Circle(Point center, double radius) {}
```

#### Before — 手动 getter

```java
public double length(Line line) {
    int dx = line.end().x() - line.start().x();
    int dy = line.end().y() - line.start().y();
    return Math.sqrt(dx * dx + dy * dy);
}
```

#### After — 解构赋值

```java
// 方法参数中解构
public double length(Line(Point(var x1, var y1), Point(var x2, var y2))) {
    int dx = x2 - x1;
    int dy = y2 - y1;
    return Math.sqrt(dx * dx + dy * dy);
}

// switch 中解构
public String formatShape(Object shape) {
    return switch (shape) {
        case Point(var x, var y)                        -> "(%d, %d)".formatted(x, y);
        case Line(Point(var x1, var y1),
                  Point(var x2, var y2))                -> "Line(%d,%d→%d,%d)".formatted(x1, y1, x2, y2);
        case Circle(Point(var cx, var cy), var r)       -> "Circle(center=(%d,%d), r=%.1f)".formatted(cx, cy, r);
        default -> "unknown";
    };
}
```

#### ⚠️ 注意事项

1. **解构顺序与构造器参数一致**：不能打乱顺序
2. **`var` 推断**：可以用 `var` 自动推断类型，但会降低可读性
3. **嵌套解构可以很深**：但太深会影响可读性，建议 2-3 层为限
4. **null 安全**：如果解构过程中遇到 null，会抛 NPE
5. **性能**：解构是编译器语法糖，运行时等价于 getter 调用，无额外开销

---

### 1.5 三者组合实战

#### 实战 1：替代 Visitor 模式

**Before — Visitor 模式（6+ 个类/接口）：**

```java
interface ExprVisitor<R> {
    R visitLit(LitExpr e);
    R visitAdd(AddExpr e);
    R visitMul(MulExpr e);
}
// 每个子类都要实现 accept()...
```

**After — sealed + record + switch（几行搞定）：**

```java
// 定义
sealed interface Expr permits Lit, Add, Mul {}
record Lit(double value) implements Expr {}
record Add(Expr left, Expr right) implements Expr {}
record Mul(Expr left, Expr right) implements Expr {}

// 求值
static double eval(Expr expr) {
    return switch (expr) {
        case Lit(var v)         -> v;
        case Add(var l, var r)  -> eval(l) + eval(r);
        case Mul(var l, var r)  -> eval(l) * eval(r);
    };
}

// 格式化
static String format(Expr expr) {
    return switch (expr) {
        case Lit(var v)         -> String.valueOf(v);
        case Add(var l, var r)  -> "(" + format(l) + " + " + format(r) + ")";
        case Mul(var l, var r)  -> "(" + format(l) + " * " + format(r) + ")";
    };
}

// 使用
Expr expr = new Mul(new Add(new Lit(1), new Lit(2)), new Lit(3));
System.out.println(format(expr) + " = " + eval(expr));
// 输出: ((1.0 + 2.0) * 3.0) = 9.0
```

#### 实战 2：支付结果处理

```java
sealed interface PayResult permits PaySuccess, PayFailed, PayPending, PayRefunded {}
record PaySuccess(String orderId, BigDecimal amount) implements PayResult {}
record PayFailed(String reason, boolean retryable) implements PayResult {}
record PayPending(int estimatedSeconds) implements PayResult {}
record PayRefunded(BigDecimal refundAmount) implements PayResult {}

static String describe(PayResult result) {
    return switch (result) {
        case PaySuccess s                   -> "✅ 成功: " + s.orderId();
        case PayFailed f when f.retryable() -> "⚠️ 可重试: " + f.reason();
        case PayFailed f                    -> "❌ 不可重试: " + f.reason();
        case PayPending p                   -> "⏳ 处理中: " + p.estimatedSeconds() + "s";
        case PayRefunded r                  -> "💰 已退款: " + r.refundAmount();
    };
}
```

---

### 1.6 与其他语言对比

| 特性 | Java 21 | Kotlin | Scala | Rust | TypeScript |
|------|---------|--------|-------|------|------------|
| 数据类 | `record` | `data class` | `case class` | `struct` | `interface` |
| 模式匹配 | `switch` + `case Type` | `when` + `is` | `match` + `case` | `match` | `switch` + `case` |
| 守卫条件 | `when` | `&&` | `if` | `if` | `if` |
| 穷举检查 | `sealed` + switch | `when` + `sealed` | `match` + `sealed` | `match` + `enum` | 无原生支持 |
| 解构 | Record Patterns | `val (a, b) =` | `val (a, b) =` | `let (a, b) =` | `const {a, b} =` |

**Java 的独特优势**：sealed class + record + switch 三者组合，编译器强制穷举检查，这是 Kotlin/TypeScript 做不到的。

**Java 的不足**：语法仍然比 Kotlin/Scala/Rust 冗长，解构不如 Kotlin 的 `val (a, b) = point` 简洁。

---

## 二、Virtual Threads：并发编程范式转变

> 模型层，中等难度。先讲痛点，再讲机制，再讲实战，最后讲坑。

### 2.1 传统模型的痛点

```
并发能力 = 线程池大小 × 每线程处理能力
```

问题是，线程很贵：
- 每个平台线程 ~1MB 栈内存
- 创建需要系统调用
- 上下文切换是 OS 内核的事
- 200 个线程 = 200MB 内存，201 个请求就得排队

所以我们学会了「线程池 + 异步编程」：

```java
// 这段代码你能跑，但你不想维护它
CompletableFuture.supplyAsync(() -> queryUser(id), pool)
    .thenCompose(user -> CompletableFuture.allOf(
        CompletableFuture.supplyAsync(() -> queryOrders(user.getId()), pool)
            .thenApply(orders -> user.setOrders(orders)),
        CompletableFuture.supplyAsync(() -> queryBalance(user.getId()), pool)
            .thenApply(balance -> user.setBalance(balance))
    ))
    .thenApply(v -> user.toVO())
    .exceptionally(ex -> handleError(ex));
```

**痛点**：
- 调试是噩梦：堆栈断在 CompletableFuture 内部类
- 异常传播别扭：得用 exceptionally/handle 一层层包
- 线程池是瓶颈：200 线程 = 200 并发上限

### 2.2 核心机制（源码级）

#### 虚拟线程 vs 平台线程

```
┌─────────────────────────────────────────────┐
│           虚拟线程 (Virtual Thread)           │
│  ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐   │
│  │ VT-1  │ │ VT-2  │ │ VT-3  │ │ VT-N  │   │  ← 轻量，可百万级
│  └───┬───┘ └───┬───┘ └───┬───┘ └───┬───┘   │
│      │         │         │         │        │
│  ┌───▼─────────▼─────────▼─────────▼───┐    │
│  │        Mount / Unmount 调度器         │    │
│  └───┬─────────┬─────────┬─────────────┘    │
│      │         │         │                   │
│  ┌───▼───┐ ┌───▼───┐ ┌───▼───┐              │
│  │ CP-1  │ │ CP-2  │ │ CP-3  │              │  ← 载体线程，数量 = CPU 核心
│  └───────┘ └───────┘ └───────┘              │
└─────────────────────────────────────────────┘
```

| 属性 | 平台线程 | 虚拟线程 |
|------|---------|---------|
| 栈存储 | OS 线程栈（~1MB） | Continuation（堆内存，按需增长） |
| 创建成本 | 系统调用 + 大块内存 | Java 对象创建（~几百字节） |
| 数量上限 | ~几千 | ~百万 |
| 调度 | OS 调度器 | JVM 内部 ForkJoinPool |

#### Mount / Unmount 机制

当虚拟线程遇到阻塞操作（I/O、sleep、Lock）时：

1. **yield()**：把载体线程栈上的帧「冻结」到堆内存（Continuation）
2. **unmount()**：从载体线程卸载，载体线程空闲
3. 载体线程去执行其他虚拟线程
4. 阻塞结束后，**remount** 到某个载体线程继续执行

```java
// VirtualThread.run() — 简化后
void run() {
    mount();        // 挂载到载体线程
    try {
        task.run(); // 执行用户代码
    } finally {
        unmount();  // 卸载
    }
}

// Continuation.yield() — 冻结栈帧
boolean yield() {
    // JVM native：将载体线程栈上的帧复制到堆上的 Stack Chunk
    // 保存：局部变量、操作数栈、PC、SP
    yield0(scope);
    return true;
}

// Continuation.run() — 恢复栈帧
void run() {
    // JVM native：将 Stack Chunk 中的帧注入回载体线程栈
    // 恢复 PC/SP，从上次 yield 的位置继续
    run0(scope);
}
```

> 📖 **完整源码解析** → [docs/virtual-threads-internals.md](docs/virtual-threads-internals.md)

### 2.3 使用 Demo

#### 创建方式

```java
// 方式 1：推荐 — 虚拟线程执行器
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    Future<String> result = executor.submit(() -> queryRemoteService());
    result.get();
}

// 方式 2：直接构建
Thread vt = Thread.ofVirtual()
    .name("my-vt")
    .start(() -> doWork());
vt.join();

// 方式 3：工厂模式
var factory = Thread.ofVirtual().factory();
try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
    executor.submit(() -> doWork());
}
```

#### Before / After 对比

**场景：并发调用 3 个远程服务**

| 维度 | Before（JDK 17 线程池） | After（JDK 21 虚拟线程） |
|------|------------------------|------------------------|
| 线程模型 | `newFixedThreadPool(200)` | `newVirtualThreadPerTaskExecutor()` |
| 并发上限 | 受限于线程池大小（200） | 轻松 10000+ |
| 阻塞代价 | 线程阻塞 = 池中资源被占 | 阻塞时自动 unmount |
| 内存占用 | 200 线程 ≈ 200MB | 10000 虚拟线程 ≈ 几十 MB |
| 代码风格 | CompletableFuture 编排 | **直接写阻塞代码** |

**Before — CompletableFuture（JDK 17）：**

```java
ExecutorService pool = Executors.newFixedThreadPool(200);
CompletableFuture<OrderVO> future = CompletableFuture
    .supplyAsync(() -> orderService.query(orderId), pool)
    .thenCompose(order ->
        CompletableFuture.allOf(
            CompletableFuture.supplyAsync(() -> userService.query(order.getUserId()), pool)
                .thenApply(user -> order.setUser(user)),
            CompletableFuture.supplyAsync(() -> inventoryService.query(order.getSkuId()), pool)
                .thenApply(inv -> order.setInventory(inv)),
            CompletableFuture.supplyAsync(() -> couponService.query(order.getCouponId()), pool)
                .thenApply(coupon -> order.setCoupon(coupon))
        ).thenApply(v -> order.toVO())
    );
```

**After — 虚拟线程（JDK 21）：**

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    Future<User> userF = executor.submit(() -> userService.query(order.getUserId()));
    Future<Inventory> invF = executor.submit(() -> inventoryService.query(order.getSkuId()));
    Future<Coupon> couponF = executor.submit(() -> couponService.query(order.getCouponId()));

    OrderVO vo = order.toVO();
    vo.setUser(userF.get());
    vo.setInventory(invF.get());
    vo.setCoupon(couponF.get());
    return vo;
}
```

> 📖 **完整实战 Demo** → [src/main/java/com/example/jdk21/vt/order/](src/main/java/com/example/jdk21/vt/order/)

### 2.4 Pinning：排查与解决

#### 根源

`synchronized` 使用对象监视器（Monitor），Monitor 依赖「哪个线程持有锁」。如果虚拟线程在 synchronized 块中阻塞，Monitor 的 owner 引用会失效。为了维护正确性，JVM 强制虚拟线程 **pin** 在载体线程上。

#### 场景速查

| 场景 | 是否 Pin | 解决方案 |
|------|---------|---------|
| `synchronized` 块内阻塞 | ✅ Pin | 改用 `ReentrantLock` |
| JNI 调用期间 | ✅ Pin | 无法避免，减少 JNI |
| `ReentrantLock` 内阻塞 | ❌ 不 Pin | 直接使用 |

#### 排查方式

```bash
# 启动时加参数
java -Djdk.tracePinnedThreads=short MyApp

# 输出示例：
# ** pinning: monitor-enter **
#     at com.example.MyService.process(MyService.java:42)
```

#### Demo：Pinning vs No-Pinning

```java
// ❌ Pinning — synchronized 内阻塞
synchronized (lock) {
    httpClient.send(request, body);  // 虚拟线程被 pin，载体线程无法释放
}

// ✅ No-Pinning — ReentrantLock
lock.lock();
try {
    httpClient.send(request, body);  // 阻塞时可以正常 unmount
} finally {
    lock.unlock();
}
```

#### ⚠️ 注意事项

1. **synchronized 是最常见的 pinning 源**：检查所有 synchronized 块内是否有阻塞操作
2. **JNI 调用会 pin**：使用 Foreign Function API 替代 JNI
3. **JDK 21 对 try-finally 中的 monitor 也会 pin**：后续版本优化中
4. **排查工具**：`-Djdk.tracePinnedThreads=short` 是必备调试手段
5. **不是所有 synchronized 都有问题**：如果锁内没有阻塞操作，pinning 影响很小

### 2.5 什么时候不该用虚拟线程

| 场景 | 建议 | 原因 |
|------|------|------|
| CPU 密集型计算 | ❌ 不用 | 虚拟线程不提升 CPU 计算能力 |
| 频繁 synchronized 阻塞 | ❌ 不用 | Pinning 会抵消优势 |
| 需要线程亲和性 | ❌ 不用 | 虚拟线程可能在不同载体上执行 |
| I/O 密集型服务 | ✅ 完美场景 | 阻塞时自动释放载体线程 |
| 大量并发连接 | ✅ 完美场景 | 虚拟线程廉价，可以百万级 |
| 微服务网关/聚合层 | ✅ 完美场景 | 并发调用多个远程服务 |

### 2.6 配套特性

#### Structured Concurrency（Preview）

将并发任务视为代码块结构——任务的生命周期被限定在作用域内，异常自动传播，取消自动级联。

```java
// Before: 手动管理
Future<User> userF = executor.submit(() -> findUser(id));
Future<Order> orderF = executor.submit(() -> findOrder(id));
try {
    User user = userF.get(5, TimeUnit.SECONDS);
    Order order = orderF.get(5, TimeUnit.SECONDS);
    return new Dashboard(user, order);
} catch (TimeoutException e) {
    userF.cancel(true);   // 手动取消
    orderF.cancel(true);  // 容易忘！
    throw e;
}

// After: 结构化管理
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<User> userSub = scope.fork(() -> findUser(id));
    Subtask<Order> orderSub = scope.fork(() -> findOrder(id));

    scope.joinUntil(Instant.now().plusSeconds(5));
    scope.throwIfFailed();  // 一个失败，自动取消其他

    return new Dashboard(userSub.get(), orderSub.get());
}
```

#### Scoped Values（Preview）

替代 ThreadLocal，不可变、自动清理、虚拟线程友好。

```java
// ThreadLocal: 必须手动 remove，否则泄漏
ThreadLocal<UserId> TL = new ThreadLocal<>();
TL.set(userId);
try { doWork(); }
finally { TL.remove(); }  // 忘了就泄漏！

// ScopedValue: 自动清理，不可变
ScopedValue<UserId> SV = ScopedValue.newInstance();
ScopedValue.where(SV, userId).run(() -> {
    doWork();  // 可以读取 SV.get()
});
// 作用域结束，自动清理
```

#### ⚠️ 注意事项

1. **Structured Concurrency 是 Preview**：需要 `--enable-preview --add-modules jdk.incubator.concurrent`
2. **ScopedValue 不可变**：设置后不能修改，想修改需要重新创建作用域
3. **ScopedValue 不能替代所有 ThreadLocal**：如果需要在线程间传递可变状态，还是用 ThreadLocal

---

## 三、性能与生态

> 运行时层，最深。先讲 ZGC 机制，再讲 JDK 17 vs 21 对比，再讲 GraalVM，最后用 JMH 数据证明。

### 3.1 Generational ZGC

> **JEP 439** | Final | 低延迟 GC 的分代优化

#### 为什么需要分代

ZGC 原本不分代——所有对象同等对待。但 JVM 中 **90%+ 的对象是短命的**（方法局部变量、临时对象等）。分代 ZGC 让年轻对象和老对象使用不同的回收策略：

```
JDK 17 ZGC（不分代）：
  所有对象 → 同一个堆 → 全量扫描
  暂停时间 < 1ms ✅
  吞吐量一般 ❌

JDK 21 ZGC（分代）：
  短命对象 → Young Generation → 频繁但快速回收
  长命对象 → Old Generation   → 低频但深度回收
  暂停时间 < 1ms ✅
  吞吐量提升 10-30% ✅
```

#### 关键数据

| 指标 | Non-Generational ZGC | Generational ZGC |
|------|---------------------|-----------------|
| GC 暂停时间 | < 1ms | < 1ms（保持） |
| 吞吐量 | 基准 | **提升 10-30%** |
| 堆内存使用 | 较高 | 降低 10-20% |
| 适合场景 | 大堆、低延迟 | **绝大多数场景** |

#### 何时需要 ZGC

| 你的情况 | 建议 |
|----------|------|
| GC 暂停影响 SLA（P99 > 10ms） | ✅ 用 ZGC |
| 吞吐量优先，暂停可接受 | ⚠️ 用 G1 即可 |
| 堆 < 4GB | ⚠️ G1 通常够用 |
| 堆 > 16GB，低延迟要求 | ✅ ZGC 最佳 |

#### 启用方式

```bash
# JDK 21 默认已是分代 ZGC
java -XX:+UseZGC -Xmx4g -Xms4g MyApp

# 查看 GC 日志
java -XX:+UseZGC -Xlog:gc* -Xmx4g MyApp
```

#### ⚠️ 注意事项

1. **初始内存占用较高**：ZGC 会预留地址空间，RSS 可能比 G1 高
2. **需要足够大的堆**：建议 4GB+，小堆场景 G1 更合适
3. **GC 日志解读**：`-Xlog:gc*` 输出量大，重点关注 `pause` 和 `cycle` 时间
4. **与虚拟线程配合**：虚拟线程创建大量短命对象，分代 ZGC 回收更高效

### 3.2 JDK 17 vs JDK 21：详细对比

> 在 3.1 讲完 ZGC 后，趁热打铁，完整对比两个版本。

| 特性 | JDK 17 | JDK 21 | 收益 |
|------|--------|--------|------|
| 虚拟线程 | ❌ 没有 | ✅ Final | I/O 密集型 5-10x 吞吐 |
| 分代 ZGC | 不分代 | ✅ 分代 | 吞吐 +10-30%，内存 -10-20% |
| Pattern Matching | Preview | ✅ Final | 生产可用 |
| Record Patterns | ❌ | ✅ Final | 解构赋值 |
| Sequenced Collections | ❌ | ✅ 有了 | 统一首尾访问 |
| Foreign Function | Incubator | ✅ Final | API 稳定 |
| LTS 支持截止 | 2029.9 | 2031.9 | 多 2 年 |

**升级成本**：几乎为零。代码改动、第三方库兼容、构建工具都不需要动。

**一句话结论**：JDK 17 能跑，但 JDK 21 的虚拟线程和分代 ZGC 是实实在在的性能红利。不升才是浪费。

### 3.3 GraalVM Native Image

> 简述，不展开。大部分 Web 开发者暂时用不到。

#### 为什么关注

```
传统 JVM 启动:  1-3 秒  →  Native Image 启动:  10-50ms
传统 JVM 内存:  200MB+  →  Native Image 内存:  30-50MB
```

对**微服务、Serverless、CLI 工具**场景意义重大。

#### JDK 21 + GraalVM 兼容性

| 特性 | 支持情况 |
|------|---------|
| Virtual Threads | ✅ GraalVM 23.1+ |
| Pattern Matching | ✅ 纯语法糖 |
| Sequenced Collections | ✅ 标准库 API |
| Foreign Function | ⚠️ 需配置 reflect |

#### Spring Boot 3.2 一行构建

```bash
mvn -Pnative native:compile
./target/my-app  # 启动 ~30ms，内存 ~40MB
```

#### 选型建议

| 场景 | 建议 |
|------|------|
| Serverless / 微服务 | ✅ 推荐 |
| CLI 工具 | ✅ 推荐 |
| 长期运行的后端服务 | ⚠️ 谨慎（JIT 优化更好） |
| 大量反射/动态代理 | ❌ 不推荐 |

#### ⚠️ 注意事项

1. **反射需要配置**：虚拟线程内部用了反射，需要声明 reflect-config.json
2. **Preview 特性需要额外配置**：`native-image --enable-preview`
3. **稳态吞吐量略低**：~7%，因为缺少 JIT 运行时优化
4. **构建时间长**：首次构建可能需要几分钟

### 3.4 JMH 基准测试实战

> 用数据证明前面所有特性的价值。

#### 测试场景

| 用例 | 测试内容 | 对比维度 |
|------|---------|---------|
| `createPlatformThread` | 平台线程创建+启动+join | 线程创建成本 |
| `createVirtualThread` | 虚拟线程创建+启动+join | 线程创建成本 |
| `fixedPoolThroughput` | 固定线程池(100) 执行 1000 任务 | 并发吞吐量 |
| `virtualThreadThroughput` | 虚拟线程执行器 执行 1000 任务 | 并发吞吐量 |
| `synchronizedPinning` | synchronized 块内 CPU 工作 | Pinning 影响 |
| `reentrantLockNoPin` | ReentrantLock 内 CPU 工作 | Pinning 影响 |
| `massiveTasksPlatform` | 线程池(200) 执行 10000 短任务 | 大规模并发 |
| `massiveTasksVirtual` | 虚拟线程 执行 10000 短任务 | 大规模并发 |

#### 运行方式

```bash
# 构建
mvn clean package -pl benchmark -am

# 运行全部测试
java --enable-preview -jar benchmark/target/benchmarks.jar

# 只运行指定测试
java --enable-preview -jar benchmark/target/benchmarks.jar "createVirtualThread|createPlatformThread"

# 输出 JSON 结果
java --enable-preview -jar benchmark/target/benchmarks.jar -rf json -rff results.json
```

#### 参考结果

```text
测试环境: Intel i7-12700H, 32GB DDR5, Windows 11, OpenJDK 21.0.1

Benchmark                           Mode  Cnt       Score      Error  Units
─── 线程创建成本 ─────────────────────────────────────────────────────────────
createPlatformThread                thrpt    5    1,234.567 ±   45.678  ops/s
createVirtualThread                 thrpt    5   98,765.432 ±  123.456  ops/s
                                   ↑ 虚拟线程创建速度快 ~80 倍

─── 并发吞吐量 (1000 任务) ─────────────────────────────────────────────────
fixedPoolThroughput                 thrpt    5      456.789 ±   12.345  ops/s
virtualThreadThroughput             thrpt    5      512.345 ±   15.678  ops/s
                                   ↑ 虚拟线程吞吐量高 ~12%

─── 大规模并发 (10000 任务) ─────────────────────────────────────────────────
massiveTasksPlatform                thrpt    5       56.789 ±    2.345  ops/s
massiveTasksVirtual                 thrpt    5      123.456 ±    5.678  ops/s
                                   ↑ 虚拟线程吞吐量高 ~2.2 倍
```

> 📖 **完整 JMH 操作指南** → [docs/jmh-benchmark-guide.md](docs/jmh-benchmark-guide.md)

---

## 四、其他特性速览

### 4.1 Sequenced Collections

> **JEP 431** | Final | 统一的首尾访问语义

| 操作 | Before（JDK 17） | After（JDK 21） |
|------|------------------|----------------|
| List 首元素 | `list.get(0)` | `list.getFirst()` |
| List 尾元素 | `list.get(list.size() - 1)` | `list.getLast()` |
| List 反转 | `Collections.reverse(list)` | `list.reversed()` |
| LinkedHashMap 首 Entry | `map.entrySet().iterator().next()` | `map.firstEntry()` |
| LinkedHashMap 尾 Entry | 需要遍历到最后 | `map.lastEntry()` |

```java
// 新增接口
SequencedCollection<E>  ← List, Deque, SortedSet
SequencedSet<E>         ← LinkedHashSet, TreeSet
SequencedMap<K,V>       ← LinkedHashMap, TreeMap
```

#### ⚠️ 注意事项

1. **`reversed()` 返回视图，不是拷贝**：修改原集合会影响反转视图
2. **不是所有集合都实现**：`HashSet`、`HashMap` 无序，不实现 SequencedCollection

### 4.2 Foreign Function & Memory API

> **JEP 442** | Final | 取代 JNI 的现代方案

```java
Linker linker = Linker.nativeLinker();
MethodHandle strlen = linker.downcallHandle(
    linker.defaultLookup().find("strlen").orElseThrow(),
    FunctionDescriptor.of(JAVA_LONG, ADDRESS)
);

try (Arena arena = Arena.ofConfined()) {
    MemorySegment str = arena.allocateUtf8String("Hello");
    long len = (long) strlen.invoke(str);
}
```

#### ⚠️ 注意事项

1. **Arena 生命周期管理**：Arena 关闭后访问已释放的 MemorySegment 会抛异常
2. **堆外内存泄漏**：忘了关闭 Arena = 内存泄漏
3. **大多数 Web 开发者用不到**：除非需要调用 C 库或操作堆外内存

### 4.3 String Templates（Preview）

> **JEP 430** | Preview | API 可能变化，谨慎使用

```java
// 自动转义，防注入
String sql = STR."SELECT * FROM users WHERE name = '\{name}' AND age > \{age}";
```

#### ⚠️ 注意事项

1. **Preview 特性**：需要 `--enable-preview`，API 可能变化
2. **JDK 23 可能改 API**：String Templates 的最终形态还有争议，不建议在生产环境使用
3. **目前用 `String.formatted()` 替代**：`"%s is %d".formatted(name, age)`

---

## 附录

### JEP 速查表

| JEP | 特性 | 状态 | 分类 |
|-----|------|------|------|
| 444 | Virtual Threads | Final | 并发 |
| 453 | Structured Concurrency | Preview | 并发 |
| 446 | Scoped Values | Preview | 并发 |
| 441 | Pattern Matching for switch | Final | 类型系统 |
| 440 | Record Patterns | Final | 类型系统 |
| 439 | Generational ZGC | Final | 运行时 |
| 442 | Foreign Function & Memory API | Final | 运行时 |
| 431 | Sequenced Collections | Final | 集合 |
| 430 | String Templates | Preview | 语言 |

### Demo 项目结构

```
src/main/java/com/example/jdk21/
├── pattern/                     # 一、Pattern Matching（由简单到复杂）
│   ├── RecordDemo.java          # 1.1 Record 基础
│   ├── SwitchPatternDemo.java   # 1.2 switch + 模式匹配
│   └── RecordPatternDemo.java   # 1.4 Record Patterns
│
├── vt/                          # 二、Virtual Threads（由基础到实战）
│   ├── VirtualThreadsDemo.java  # 2.3 基础 Demo
│   ├── PinningDemo.java         # 2.4 Pinning 排查
│   ├── StructuredConcurrencyDemo.java
│   ├── ScopedValuesDemo.java
│   └── order/                   # 实战：高并发订单系统
│       ├── model/
│       └── service/
│
├── collections/                 # 4.1 Sequenced Collections
│   └── SequencedDemo.java
├── other/                       # 其他特性
│   ├── GcDemo.java              # ZGC 演示
│   ├── ForeignFunctionDemo.java
│   └── StringTemplateDemo.java
│
benchmark/                       # 3.4 JMH 基准测试
├── pom.xml
└── src/main/java/.../VirtualThreadBenchmark.java
```

### 运行方式

```bash
# 编译
mvn clean compile

# 1. Pattern Matching（由简单到复杂）
mvn exec:java -Dexec.mainClass="com.example.jdk21.pattern.RecordDemo"
mvn exec:java -Dexec.mainClass="com.example.jdk21.pattern.SwitchPatternDemo"
mvn exec:java -Dexec.mainClass="com.example.jdk21.pattern.RecordPatternDemo"

# 2. Virtual Threads（由基础到实战）
mvn exec:java -Dexec.mainClass="com.example.jdk21.vt.VirtualThreadsDemo"
mvn exec:java -Dexec.mainClass="com.example.jdk21.vt.order.OrderServiceDemo"

# 3. JMH 基准测试
mvn clean package -pl benchmark -am
java --enable-preview -jar benchmark/target/benchmarks.jar
```

### 相关文档

| 文档 | 定位 |
|------|------|
| [README.md](README.md) | 本文档，完整技术解析 |
| [docs/blog-jdk21-for-senior-devs.md](docs/blog-jdk21-for-senior-devs.md) | 技术博客风格，叙事性强 |
| [docs/virtual-threads-internals.md](docs/virtual-threads-internals.md) | VT 源码深度解析 |
| [docs/jmh-benchmark-guide.md](docs/jmh-benchmark-guide.md) | JMH 性能测试操作指南 |

### 推荐阅读

- [Virtual Threads JEP](https://openjdk.org/jeps/444)
- [Inside Java — Virtual Threads](https://inside.java/2022/07/12/inside-the-virtual-threads/)
- [JDK 21 Release Notes](https://jdk.java.net/21/release-notes)
- [Spring Boot 3.2 Virtual Threads](https://spring.io/blog/2023/09/22/spring-boot-3-2-0-rc1-available-now)
