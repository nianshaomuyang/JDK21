package com.example.jdk21.other;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ============================================================================
 * 4.3 String Templates 演示 — 由简单到复杂
 * ============================================================================
 *
 * 递进路线：
 *   ① 基础用法（STR."..."）            → 最简单的模板
 *   ② 多行字符串                        → JSON/XML 构建
 *   ③ SQL 注入防护                      → 安全性优势
 *   ④ 与 formatted() 对比              → 什么时候用哪个
 *
 * 核心价值：
 *   - 代码更可读：内联表达式，无需拼接
 *   - 更安全：自动转义，防止注入
 *   - 更灵活：可自定义 TemplateProcessor
 *
 * ⚠️ 注意：这是 Preview 特性
 *   - 需要 --enable-preview
 *   - JDK 23 可能改 API，不建议生产使用
 *   - 目前用 String.formatted() 替代
 */
public class StringTemplateDemo {

    public static void main(String[] args) {
        System.out.println("=== 4.3 String Templates 演示 ===\n");

        String name = "张三";
        int age = 28;
        BigDecimal salary = new BigDecimal("15000.50");

        // ========================================================================
        // ① 基础用法：STR."..." 语法
        // ========================================================================
        System.out.println("① 基础用法");

        // Before: 字符串拼接
        String before = "姓名: " + name + ", 年龄: " + age + ", 薪资: " + salary;
        System.out.println("  Before (拼接): " + before);

        // After: String Template（需要 --enable-preview）
        // String after = STR."姓名: \{name}, 年龄: \{age}, 薪资: \{salary}";
        System.out.println("  After (STR):   姓名: " + name + ", 年龄: " + age + ", 薪资: " + salary);
        System.out.println("  说明: STR.\"...\" 语法需要 --enable-preview");

        // ========================================================================
        // ② 多行字符串：JSON/XML 构建
        // ========================================================================
        System.out.println("\n② 多行字符串");

        // text block + formatted() 替代方案
        String json = """
            {
                "name": "%s",
                "age": %d,
                "salary": %s
            }
            """.formatted(name, age, salary);
        System.out.println("  JSON (text block + formatted):");
        System.out.println(json);

        // String Template 方案（需要 --enable-preview）
        // String json2 = STR."""
        //     {
        //         "name": "\{name}",
        //         "age": \{age},
        //         "salary": \{salary}
        //     }
        //     """;
        System.out.println("  说明: String Template 可以直接内联表达式");

        // ========================================================================
        // ③ SQL 注入防护：安全性优势
        // ========================================================================
        System.out.println("③ SQL 注入防护");

        String userInput = "'; DROP TABLE users; --";
        System.out.println("  用户输入: " + userInput);

        // Before: 手动转义（容易遗漏）
        String unsafeSql = "SELECT * FROM users WHERE name = '" + userInput + "'";
        System.out.println("  不安全: " + unsafeSql);

        // After: 手动转义
        String safeSql = "SELECT * FROM users WHERE name = '" + userInput.replace("'", "''") + "'";
        System.out.println("  手动转义: " + safeSql);

        // String Template: 自动转义（需要 --enable-preview）
        // String autoSql = STR."SELECT * FROM users WHERE name = '\{userInput}'";
        System.out.println("  说明: STR 会自动处理转义，防止注入");

        // ========================================================================
        // ④ 与 formatted() 对比
        // ========================================================================
        System.out.println("\n④ 与 formatted() 对比");

        String fmtResult = "姓名: %s, 年龄: %d, 薪资: %s".formatted(name, age, salary);
        System.out.println("  formatted(): " + fmtResult);
        System.out.println("  STR:         姓名: " + name + ", 年龄: " + age + ", 薪资: " + salary);

        System.out.println("\n  对比:");
        System.out.println("  ┌─────────────┬────────────────────┬────────────────────┐");
        System.out.println("  │ 维度        │ formatted()        │ String Template    │");
        System.out.println("  ├─────────────┼────────────────────┼────────────────────┤");
        System.out.println("  │ 可读性      │ 一般（%s 占位符）  │ 好（直接内联）     │");
        System.out.println("  │ 安全性      │ 不自动转义          │ 自动转义           │");
        System.out.println("  │ 状态        │ Final               │ Preview            │");
        System.out.println("  │ 建议        │ 目前使用            │ 等 API 稳定        │");
        System.out.println("  └─────────────┴────────────────────┴────────────────────┘");

        System.out.println("\n总结:");
        System.out.println("  String Templates 让字符串拼接更可读、更安全");
        System.out.println("  ⚠️  Preview 特性，API 可能变化，不建议生产使用");
        System.out.println("  目前用 text block + formatted() 替代");
    }
}
