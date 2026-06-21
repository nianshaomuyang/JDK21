package com.example.jdk21.pattern;

import java.math.BigDecimal;

/**
 * ============================================================================
 * 1.1 Record：不可变数据载体 — 由简单到复杂
 * ============================================================================
 *
 * 递进路线：
 *   ① 最简 Record（一行定义）        → 理解 Record 是什么
 *   ② 不可变性（final 语义）         → 理解 Record 的核心设计
 *   ③ with 方法（函数式修改）        → 理解如何"修改"不可变对象
 *   ④ 自定义方法                     → Record 不只是数据载体
 *   ⑤ compact 构造器（参数校验）     → 生产环境必备
 *   ⑥ equals / hashCode 自动生成    → 理解编译器帮我们做了什么
 *   ⑦ Record vs Lombok @Data        → 什么时候用哪个
 *
 * 核心价值：
 *   - 一行定义不可变数据类
 *   - 编译器自动生成 equals/hashCode/toString
 *   - final 语义保证线程安全
 *   - 天然支持 Pattern Matching 解构（见 1.2 和 1.4）
 *
 * 编译后结构（javap -p User.class）：
 *   public final class User extends java.lang.Record {
 *       private final String name;    // 所有字段都是 private final
 *       private final int age;
 *       private final String email;
 *       public User(String name, int age, String email) { ... }  // 全参构造器
 *       public String name() { return name; }   // 访问器（不是 getXxx）
 *       public int age() { return age; }
 *       public String email() { return email; }
 *       public boolean equals(Object o) { ... }  // 自动生成
 *       public int hashCode() { ... }            // 自动生成
 *       public String toString() { ... }         // 自动生成
 *   }
 */
public class RecordDemo {

    // ========================================================================
    // ① 最简 Record：一行定义不可变数据类
    // ========================================================================
    // 这是最简单的 Record，编译器自动生成：
    //   - private final 字段
    //   - 全参构造器
    //   - 访问器方法（name() 而不是 getName()）
    //   - equals() / hashCode() / toString()
    record User(String name, int age, String email) {}

    // ========================================================================
    // ④ 自定义方法：Record 不只是数据载体
    // ========================================================================
    // Record 类内可以定义任意方法，增强数据类的行为
    record Point(int x, int y) {
        // 计算两点距离
        double distanceTo(Point other) {
            int dx = this.x - other.x;
            int dy = this.y - other.y;
            return Math.sqrt(dx * dx + dy * dy);
        }

        // ========================================================================
        // ③ with 方法：函数式修改不可变对象
        // ========================================================================
        // Record 字段是 final 的，不能 setter
        // 正确做法：返回新对象，原对象不变（函数式风格）
        // 这与 String 的操作方式一致：String.replace() 返回新字符串
        Point withX(int newX) {
            return new Point(newX, this.y);  // 返回新 Point，原对象不变
        }

        Point withY(int newY) {
            return new Point(this.x, newY);
        }
    }

    // ========================================================================
    // ⑤ compact 构造器：参数校验
    // ========================================================================
    // 生产环境必备：在构造器中校验参数，防止创建非法对象
    // compact 构造器不写参数列表，直接用字段名，编译器自动赋值
    record Order(String orderId, BigDecimal amount, String status) {
        // compact 构造器：参数校验
        Order {
            // 参数校验：在赋值之前检查
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException("订单号不能为空");
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("金额必须大于 0");
            }
            // 校验通过后，编译器自动执行 this.orderId = orderId 等赋值
        }
    }

    // ========================================================================
    // 实现接口的 Record
    // ========================================================================
    // Record 可以实现接口，但不能继承其他类（隐式继承 java.lang.Record）
    record UserDTO(String name, int age) implements java.io.Serializable {}

    // ========================================================================
    // 主入口：由简单到复杂的演示
    // ========================================================================
    public static void main(String[] args) {
        System.out.println("=== 1.1 Record 基础演示 ===\n");

        // ---- ① 最简 Record ----
        System.out.println("① 最简 Record（一行定义）");
        User user = new User("张三", 28, "zhangsan@example.com");
        System.out.println("  创建: " + user);                    // 自动 toString()
        System.out.println("  name(): " + user.name());           // 访问器（不是 getName）
        System.out.println("  age(): " + user.age());
        System.out.println("  email(): " + user.email());

        // ---- ② 不可变性（final 语义）----
        System.out.println("\n② 不可变性（final 语义）");
        System.out.println("  user.name() 返回: " + user.name());
        // user.name = "李四";  // ❌ 编译报错：final 字段不能赋值
        // user.setName("李四"); // ❌ 没有 setter 方法
        System.out.println("  说明: Record 字段是 private final，没有 setter");
        System.out.println("  价值: 不可变对象天然线程安全，可安全用作 Map 的 key");

        // ---- ③ with 方法（函数式修改）----
        System.out.println("\n③ with 方法（函数式修改）");
        Point p1 = new Point(3, 5);
        Point p2 = p1.withX(10);  // 返回新对象
        Point p3 = p1.withY(20);  // 返回新对象
        System.out.println("  p1 = " + p1);
        System.out.println("  p1.withX(10) = " + p2 + "  ← 新对象");
        System.out.println("  p1.withY(20) = " + p3 + "  ← 新对象");
        System.out.println("  p1 不变: " + p1 + "  ← 原对象保持不变");

        // ---- ④ 自定义方法 ----
        System.out.println("\n④ 自定义方法");
        Point a = new Point(0, 0);
        Point b = new Point(3, 4);
        System.out.println("  " + a + " 到 " + b + " 的距离: " + a.distanceTo(b));

        // ---- ⑤ compact 构造器（参数校验）----
        System.out.println("\n⑤ compact 构造器（参数校验）");

        // 正常情况
        try {
            Order order = new Order("ORD-001", new BigDecimal("299.00"), "待支付");
            System.out.println("  正常订单: " + order);
        } catch (IllegalArgumentException e) {
            System.out.println("  异常: " + e.getMessage());
        }

        // 异常情况：空订单号
        try {
            new Order("", new BigDecimal("100"), "待支付");
        } catch (IllegalArgumentException e) {
            System.out.println("  空订单号: " + e.getMessage());
        }

        // 异常情况：负数金额
        try {
            new Order("ORD-002", new BigDecimal("-10"), "待支付");
        } catch (IllegalArgumentException e) {
            System.out.println("  负数金额: " + e.getMessage());
        }

        // ---- ⑥ equals / hashCode 自动生成 ----
        System.out.println("\n⑥ equals / hashCode（自动生成）");
        User user1 = new User("张三", 28, "zhangsan@example.com");
        User user2 = new User("张三", 28, "zhangsan@example.com");
        System.out.println("  user1.equals(user2): " + user1.equals(user2));
        System.out.println("  user1.hashCode() == user2.hashCode(): " + (user1.hashCode() == user2.hashCode()));
        System.out.println("  说明: 基于所有字段自动生成，无需手写");

        // ---- ⑦ Record vs Lombok @Data ----
        System.out.println("\n⑦ Record vs Lombok @Data");
        System.out.println("  ┌─────────────┬────────────────────┬────────────────────┐");
        System.out.println("  │ 维度        │ Java Record        │ Lombok @Data       │");
        System.out.println("  ├─────────────┼────────────────────┼────────────────────┤");
        System.out.println("  │ 可变性      │ 不可变（final）     │ 可变（有 setter）  │");
        System.out.println("  │ 方法名      │ name()             │ getName()          │");
        System.out.println("  │ 模式匹配    │ ✅ 支持解构         │ ❌ 不支持          │");
        System.out.println("  │ 适用场景    │ DTO / 值对象        │ ORM 实体类         │");
        System.out.println("  └─────────────┴────────────────────┴────────────────────┘");

        System.out.println("\n总结:");
        System.out.println("  Record 是 Java 的不可变数据载体，一行代码替代几十行样板代码");
        System.out.println("  final 语义保证线程安全，天然支持 Pattern Matching 解构");
    }
}
