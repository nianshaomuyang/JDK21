package com.example.jdk21.other;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * ============================================================================
 * 4.2 Foreign Function & Memory API 演示 — 由简单到复杂
 * ============================================================================
 *
 * 递进路线：
 *   ① 调用 C strlen 函数                → 最简单的 native 调用
 *   ② 调用 C abs 函数                   → 传参 + 返回值
 *   ③ 堆外内存操作                      → Arena 生命周期管理
 *   ④ 与 JNI 对比                       → 理解优势
 *
 * 核心价值：
 *   取代 JNI/JNA 的现代方案
 *   - 纯 Java 代码，不需要 native 方法声明
 *   - 类型安全，编译时检查
 *   - Arena 自动管理内存，不会泄漏
 *
 * 注意：需要 --enable-preview --add-modules jdk.incubator.foreign
 * 注意：需要系统有 C 标准库
 */
public class ForeignFunctionDemo {

    // ========================================================================
    // ① 调用 C strlen 函数：最简单的 native 调用
    // ========================================================================

    /**
     * 调用 C 标准库的 strlen 函数
     *   - 获取 native linker
     *   - 查找 strlen 函数
     *   - 分配 native 内存
     *   - 调用并获取结果
     */
    public static long callStrlen(String str) throws Throwable {
        // 1. 获取 native linker
        Linker linker = Linker.nativeLinker();

        // 2. 查找 strlen 函数
        SymbolLookup lookup = linker.defaultLookup();
        MethodHandle strlen = linker.downcallHandle(
            lookup.find("strlen").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        );

        // 3. 分配 native 内存并调用
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cStr = arena.allocateUtf8String(str);
            return (long) strlen.invoke(cStr);
        }
        // Arena 关闭，自动释放 native 内存
    }

    // ========================================================================
    // ② 调用 C abs 函数：传参 + 返回值
    // ========================================================================

    /**
     * 调用 C 标准库的 abs 函数
     *   - 传入 int 参数
     *   - 返回 int 结果
     */
    public static int callAbs(int value) throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup lookup = linker.defaultLookup();
        MethodHandle abs = linker.downcallHandle(
            lookup.find("abs").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );
        return (int) abs.invoke(value);
    }

    // ========================================================================
    // ③ 堆外内存操作：Arena 生命周期管理
    // ========================================================================

    /**
     * 堆外内存操作
     *   - Arena 管理内存生命周期
     *   - 分配、读写、释放
     *   - Arena 关闭后自动释放
     */
    public static void offHeapMemoryDemo() {
        System.out.println("③ 堆外内存操作");

        // Arena 管理内存生命周期
        try (Arena arena = Arena.ofConfined()) {
            // 分配 100 个 int 的 native 数组
            MemorySegment segment = arena.allocate(ValueLayout.JAVA_INT, 100);

            // 写入
            for (int i = 0; i < 100; i++) {
                segment.setAtIndex(ValueLayout.JAVA_INT, i, i * i);
            }

            // 读取
            System.out.println("  segment[0] = " + segment.getAtIndex(ValueLayout.JAVA_INT, 0));
            System.out.println("  segment[10] = " + segment.getAtIndex(ValueLayout.JAVA_INT, 10));
            System.out.println("  segment[99] = " + segment.getAtIndex(ValueLayout.JAVA_INT, 99));

            // 内存大小
            System.out.println("  byteSize = " + segment.byteSize());
        }
        // Arena 关闭，自动释放 native 内存

        System.out.println("  Arena 关闭后内存已自动释放");
    }

    // ========================================================================
    // ④ 与 JNI 对比
    // ========================================================================

    /**
     * Foreign Function API vs JNI 对比
     */
    public static void comparisonDemo() {
        System.out.println("\n④ 与 JNI 对比");
        System.out.println("  ┌─────────────────────┬──────────────────────┬──────────────────────┐");
        System.out.println("  │ 维度                │ JNI                  │ Foreign Function API │");
        System.out.println("  ├─────────────────────┼──────────────────────┼──────────────────────┤");
        System.out.println("  │ 代码量              │ 大量样板代码          │ 纯 Java 代码         │");
        System.out.println("  │ 类型安全            │ 运行时检查            │ 编译时检查           │");
        System.out.println("  │ 内存管理            │ 手动 malloc/free      │ Arena 自动管理       │");
        System.out.println("  │ 性能                │ JNI 调用开销大        │ 接近原生性能         │");
        System.out.println("  │ 调试                │ 需要 native debugger  │ 标准 Java 调试       │");
        System.out.println("  └─────────────────────┴──────────────────────┴──────────────────────┘");
    }

    // ========================================================================
    // 主入口：由简单到复杂
    // ========================================================================
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  Foreign Function & Memory API — JEP 442    ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        try {
            // ---- ① 调用 C strlen ----
            System.out.println("\n① 调用 C strlen");
            String[] testStrings = {"Hello", "JDK 21", "Foreign Function API", ""};
            for (String s : testStrings) {
                long len = callStrlen(s);
                System.out.printf("  strlen(\"%s\") = %d%n", s, len);
            }

            // ---- ② 调用 C abs ----
            System.out.println("\n② 调用 C abs");
            System.out.println("  abs(-42) = " + callAbs(-42));
            System.out.println("  abs(0) = " + callAbs(0));
            System.out.println("  abs(100) = " + callAbs(100));

            // ---- ③ 堆外内存 ----
            offHeapMemoryDemo();

            // ---- ④ 对比 ----
            comparisonDemo();

        } catch (Throwable e) {
            System.out.println("\n调用 native 函数失败: " + e.getMessage());
            System.out.println("可能原因: 系统不支持或需要 --add-modules jdk.incubator.foreign");
        }

        System.out.println("\n总结:");
        System.out.println("  Foreign Function API 取代 JNI，纯 Java 调用 C 函数");
        System.out.println("  Arena 自动管理内存，不会泄漏");
        System.out.println("  大多数 Web 开发者用不到，除非需要调用 C 库");
    }
}
