package com.example.jdk21.pattern;

import java.math.BigDecimal;

/**
 * ============================================================================
 * 1.2 switch + 类型模式匹配 + when — 由简单到复杂
 * ============================================================================
 *
 * 递进路线：
 *   ① switch 表达式基础（箭头语法）     → 最基础的 switch 改进
 *   ② 类型模式匹配（替代 instanceof）   → switch 能判断类型了
 *   ③ when 守卫条件                     → 在类型匹配后进一步过滤
 *   ④ sealed class 穷举检查             → 编译器保证覆盖所有情况
 *   ⑤ when + sealed 组合               → 完整的类型安全模式匹配
 *   ⑥ 嵌套模式 + 泛型                  → 复杂数据结构的处理
 *   ⑦ 实战：替代 Visitor 模式          → AST 表达式求值
 *
 * 演进路径：
 *   JDK 7:  只支持 int/String/enum
 *   JDK 14: 箭头语法（→），不需要 break
 *   JDK 17: 模式匹配（Preview）
 *   JDK 21: 模式匹配 + when 守卫条件（Final）
 */
public class SwitchPatternDemo {

    // ========================================================================
    // ① switch 表达式基础：箭头语法，不需要 break
    // ========================================================================
    // 这是最基础的 switch 改进：
    //   - 箭头语法（→）替代传统的 case/break
    //   - 不会穿透（fall-through）
    //   - switch 可以作为表达式返回值
    static String dayType(String day) {
        return switch (day) {
            case "Monday", "Tuesday", "Wednesday", "Thursday", "Friday" -> "工作日";
            case "Saturday", "Sunday" -> "周末";
            default -> "未知";
        };
    }

    // ========================================================================
    // ② 类型模式匹配：替代 instanceof 链
    // ========================================================================
    // JDK 21 的核心改进：switch 可以判断类型，并自动绑定变量
    //   - case String s → 如果 obj 是 String，绑定到变量 s
    //   - 自动类型转换，不需要手动强转
    //   - 替代冗长的 instanceof 链
    static String describeType(Object obj) {
        return switch (obj) {
            case String s  -> "字符串: " + s + " (长度 " + s.length() + ")";
            case Integer i -> "整数: " + i;
            case Double d  -> "浮点数: " + d;
            case Boolean b -> "布尔: " + b;
            case null      -> "null";        // JDK 21 支持显式处理 null
            default        -> "其他: " + obj.getClass().getSimpleName();
        };
    }

    // ========================================================================
    // ③ when 守卫条件：在类型匹配后进一步过滤
    // ========================================================================
    // when 是 JDK 21 新增的守卫条件：
    //   - case Integer i when i > 0 → 先匹配 Integer，再检查 i > 0
    //   - 注意：when 条件不参与穷举检查，把更具体的放前面
    static String describeNumber(Number num) {
        return switch (num) {
            case Integer i when i > 0  -> "正整数: " + i;   // 更具体，放前面
            case Integer i when i < 0  -> "负整数: " + i;
            case Integer _             -> "零";              // 兜底
            case Double d when d > 0   -> "正浮点: " + d;
            case Double d when d < 0   -> "负浮点: " + d;
            case Double _              -> "零点零";
            default                    -> "其他数字: " + num;
        };
    }

    // ========================================================================
    // ④ sealed class：编译器穷举检查
    // ========================================================================
    // sealed interface 限定子类型：
    //   - permits 列出所有允许的子类
    //   - 配合 switch：覆盖所有 permits → 不需要 default
    //   - 新增子类型时，所有未处理的 switch 编译报错
    sealed interface Shape permits Circle, Rectangle, Triangle {}
    record Circle(double radius) implements Shape {}
    record Rectangle(double width, double height) implements Shape {}
    record Triangle(double a, double b, double c) implements Shape {}

    /**
     * switch + sealed：编译器保证穷举
     * 如果新增子类型（比如 Square），这里会编译报错
     * 这是 sealed class 的核心价值：类型安全的穷举检查
     */
    static double area(Shape shape) {
        return switch (shape) {
            case Circle c    -> Math.PI * c.radius() * c.radius();
            case Rectangle r -> r.width() * r.height();
            case Triangle t  -> {
                double s = (t.a() + t.b() + t.c()) / 2;
                yield Math.sqrt(s * (s - t.a()) * (s - t.b()) * (s - t.c()));
            }
            // 没有 default！sealed class 保证穷举
        };
    }

    // ========================================================================
    // ⑤ when + sealed 组合：完整的类型安全模式匹配
    // ========================================================================
    // 这是 JDK 21 模式匹配的完整形态：
    //   - sealed class 保证穷举
    //   - when 守卫条件处理分支逻辑
    //   - 不需要 default，编译器帮你检查
    sealed interface PayResult permits PaySuccess, PayFailed, PayPending, PayRefunded {}
    record PaySuccess(String orderId, BigDecimal amount) implements PayResult {}
    record PayFailed(String reason, boolean retryable) implements PayResult {}
    record PayPending(int estimatedSeconds) implements PayResult {}
    record PayRefunded(BigDecimal refundAmount) implements PayResult {}

    static String describePay(PayResult result) {
        return switch (result) {
            case PaySuccess s                   -> "✅ 成功: " + s.orderId() + " " + s.amount();
            case PayFailed f when f.retryable() -> "⚠️ 可重试: " + f.reason();  // when 守卫
            case PayFailed f                    -> "❌ 不可重试: " + f.reason(); // 兜底
            case PayPending p                   -> "⏳ 处理中: " + p.estimatedSeconds() + "s";
            case PayRefunded r                  -> "💰 已退款: " + r.refundAmount();
        };
    }

    // ========================================================================
    // ⑥ 嵌套模式 + 泛型：复杂数据结构的处理
    // ========================================================================
    // 泛型 sealed interface + 类型模式匹配
    // 适用于 API 响应、消息体等复杂结构
    sealed interface ApiResponse<T> permits Success, Error, Loading {}
    record Success<T>(T data, int code) implements ApiResponse<T> {}
    record Error<T>(String message, int code) implements ApiResponse<T> {}
    record Loading<T>(int progress) implements ApiResponse<T> {}

    static <T> String handleResponse(ApiResponse<T> response) {
        return switch (response) {
            case Success(var data, var code) when code == 200 -> "成功: " + data;
            case Success(var data, var code)                  -> "成功但异常码: " + code;
            case Error(var msg, var code) when code >= 500    -> "服务器错误 [" + code + "]: " + msg;
            case Error(var msg, var code)                     -> "客户端错误 [" + code + "]: " + msg;
            case Loading(var progress)                        -> "加载中: " + progress + "%";
        };
    }

    // ========================================================================
    // ⑦ 实战：替代 Visitor 模式（AST 表达式求值）
    // ========================================================================
    // Before: Visitor 模式需要 6+ 个类/接口（ExprVisitor, LitExpr, AddExpr, MulExpr...）
    // After:  sealed + record + switch，几行搞定
    // 这是 JDK 21 类型系统进化的终极体现
    sealed interface Expr permits Lit, Add, Mul {}
    record Lit(double value) implements Expr {}
    record Add(Expr left, Expr right) implements Expr {}
    record Mul(Expr left, Expr right) implements Expr {}

    // 求值：switch + 解构 + 递归
    static double eval(Expr expr) {
        return switch (expr) {
            case Lit(var v)        -> v;
            case Add(var l, var r) -> eval(l) + eval(r);
            case Mul(var l, var r) -> eval(l) * eval(r);
        };
    }

    // 格式化：同样的模式
    static String format(Expr expr) {
        return switch (expr) {
            case Lit(var v)        -> String.valueOf(v);
            case Add(var l, var r) -> "(" + format(l) + " + " + format(r) + ")";
            case Mul(var l, var r) -> "(" + format(l) + " * " + format(r) + ")";
        };
    }

    // ========================================================================
    // 主入口：由简单到复杂
    // ========================================================================
    public static void main(String[] args) {
        System.out.println("=== 1.2 switch + 类型模式匹配 + when ===\n");

        // ---- ① switch 表达式基础 ----
        System.out.println("① switch 表达式基础（箭头语法）");
        String[] days = {"Monday", "Saturday", "Holiday"};
        for (var d : days) {
            System.out.println("  " + d + " → " + dayType(d));
        }
        System.out.println("  说明: 箭头语法不需要 break，不会穿透");

        // ---- ② 类型模式匹配 ----
        System.out.println("\n② 类型模式匹配（替代 instanceof）");
        Object[] objs = {"hello", 42, 3.14, true, null};
        for (var o : objs) {
            System.out.println("  " + describeType(o));
        }
        System.out.println("  说明: case String s → 自动类型转换 + 变量绑定");

        // ---- ③ when 守卫条件 ----
        System.out.println("\n③ when 守卫条件");
        Number[] nums = {10, -5, 0, 3.14, -2.5};
        for (var n : nums) {
            System.out.println("  " + describeNumber(n));
        }
        System.out.println("  说明: when 条件不参与穷举检查，更具体的放前面");

        // ---- ④ sealed class 穷举检查 ----
        System.out.println("\n④ sealed class 穷举检查");
        Shape[] shapes = {
            new Circle(5),
            new Rectangle(4, 6),
            new Triangle(3, 4, 5)
        };
        for (var s : shapes) {
            System.out.printf("  %-20s 面积: %.2f%n", s, area(s));
        }
        System.out.println("  说明: 没有 default，sealed class 保证穷举");

        // ---- ⑤ when + sealed 组合 ----
        System.out.println("\n⑤ when + sealed 组合（支付结果）");
        PayResult[] pays = {
            new PaySuccess("ORD-001", new BigDecimal("299.00")),
            new PayFailed("余额不足", true),
            new PayFailed("风控拦截", false),
            new PayPending(30),
            new PayRefunded(new BigDecimal("299.00"))
        };
        for (var p : pays) {
            System.out.println("  " + describePay(p));
        }
        System.out.println("  说明: sealed + when = 完整的类型安全模式匹配");

        // ---- ⑥ 嵌套模式 + 泛型 ----
        System.out.println("\n⑥ 嵌套模式 + 泛型（API 响应）");
        ApiResponse<?>[] responses = {
            new Success<>("张三", 200),
            new Success<>(null, 204),
            new Error<>("未授权", 401),
            new Error<>("内部错误", 500),
            new Loading<>(60)
        };
        for (var r : responses) {
            System.out.println("  " + handleResponse(r));
        }
        System.out.println("  说明: 泛型 sealed interface + 类型模式匹配");

        // ---- ⑦ 替代 Visitor ----
        System.out.println("\n⑦ 替代 Visitor 模式（AST 求值）");
        Expr expr1 = new Mul(new Add(new Lit(1), new Lit(2)), new Lit(3));
        Expr expr2 = new Add(new Lit(1), new Mul(new Lit(2), new Lit(3)));
        System.out.println("  " + format(expr1) + " = " + eval(expr1));
        System.out.println("  " + format(expr2) + " = " + eval(expr2));
        System.out.println("  说明: Before 需要 6+ 个类，After 只需 sealed + record + switch");

        System.out.println("\n总结:");
        System.out.println("  JDK 21 的模式匹配是类型系统的进化");
        System.out.println("  sealed class + switch + when = 编译器保证的类型安全");
    }
}
