package com.example.jdk21.pattern;

/**
 * ============================================================================
 * 1.4 Record Patterns：解构赋值 — 由简单到复杂
 * ============================================================================
 *
 * 递进路线：
 *   ① 基础解构（单层 Record）          → 最简单的解构
 *   ② 嵌套解构（多层 Record）          → Line 包含两个 Point
 *   ③ switch 中解构多种类型            → 一个 switch 处理多种形状
 *   ④ 守卫条件 + 解构                  → 解构后进一步判断
 *   ⑤ 实战：面积计算                   → 解构 + 计算
 *   ⑥ 实战：JSON 节点解析              → 解构 + 递归
 *
 * 核心语法：
 *   Point(var x, var y)                    → 解构 Point，绑定 x 和 y
 *   Line(Point(var x1, var y1), ...)       → 嵌套解构
 *   case Point(var x, var y) -> ...        → switch 中解构
 *
 * 与 getter 的对比：
 *   Before: line.start().x()               → 层层 getter
 *   After:  Line(Point(var x1, ...))       → 直接解构绑定
 */
public class RecordPatternDemo {

    // ========================================================================
    // 数据模型
    // ========================================================================
    record Point(int x, int y) {}
    record Line(Point start, Point end) {}
    record Circle(Point center, double radius) {}
    record Rect(Point topLeft, Point bottomRight) {}

    // ========================================================================
    // ① 基础解构：单层 Record
    // ========================================================================
    // 最简单的解构：case Point(var x, var y) → 把 Point 的字段绑定到 x 和 y
    static String formatPoint(Object obj) {
        return switch (obj) {
            case Point(var x, var y) -> "(%d, %d)".formatted(x, y);
            default -> "not a point";
        };
    }

    // ========================================================================
    // ② 嵌套解构：多层 Record
    // ========================================================================
    // Line 包含两个 Point，可以一次性解构所有层级
    // Line(Point(var x1, var y1), Point(var x2, var y2))
    //   ↓ 解构 Line 的 start 和 end
    //   ↓ 再解构 start 的 x 和 y，end 的 x 和 y
    static String formatLine(Object obj) {
        return switch (obj) {
            case Line(Point(var x1, var y1), Point(var x2, var y2)) ->
                "Line(%d,%d → %d,%d)".formatted(x1, y1, x2, y2);
            default -> "not a line";
        };
    }

    // ========================================================================
    // ③ switch 中解构多种类型
    // ========================================================================
    // 一个 switch 处理多种 Record 类型，每种都有不同的解构深度
    static String formatShape(Object shape) {
        return switch (shape) {
            // 单层解构
            case Point(var x, var y) ->
                "(%d, %d)".formatted(x, y);
            // 双层嵌套解构
            case Line(Point(var x1, var y1), Point(var x2, var y2)) ->
                "Line(%d,%d→%d,%d)".formatted(x1, y1, x2, y2);
            // 单层解构 + 基本类型
            case Circle(Point(var cx, var cy), var r) ->
                "Circle(center=(%d,%d), r=%.1f)".formatted(cx, cy, r);
            // 双层嵌套解构
            case Rect(Point(var x1, var y1), Point(var x2, var y2)) ->
                "Rect[(%d,%d)→(%d,%d)]".formatted(x1, y1, x2, y2);
            default -> "unknown";
        };
    }

    // ========================================================================
    // ④ 守卫条件 + 解构
    // ========================================================================
    // 解构后可以用 when 做进一步判断
    // 注意：这里用了 yield 返回值（因为 lambda 需要返回）
    static String pointRelation(Object shape, Point p) {
        return switch (shape) {
            case Circle(Point(var cx, var cy), var r) -> {
                // 解构后直接使用 cx, cy, r
                double dist = Math.sqrt(Math.pow(p.x() - cx, 2) + Math.pow(p.y() - cy, 2));
                if (dist < r) yield "点在圆内";
                else if (dist == r) yield "点在圆上";
                else yield "点在圆外";
            }
            case Rect(Point(var x1, var y1), Point(var x2, var y2)) -> {
                if (p.x() >= x1 && p.x() <= x2 && p.y() >= y1 && p.y() <= y2) {
                    yield "点在矩形内";
                } else {
                    yield "点在矩形外";
                }
            }
            default -> "不支持的图形";
        };
    }

    // ========================================================================
    // ⑤ 实战：面积计算
    // ========================================================================
    // 解构 + 计算，替代 getter 链
    static double area(Object shape) {
        return switch (shape) {
            case Point _ -> 0;
            case Line _  -> 0;
            case Circle(_, var r) -> Math.PI * r * r;
            case Rect(Point(var x1, var y1), Point(var x2, var y2)) ->
                Math.abs((x2 - x1) * (y2 - y1));
            default -> 0;
        };
    }

    // ========================================================================
    // ⑥ 实战：工作流审批流程（解构 + 递归）
    // ========================================================================
    // 这是 Record Patterns 的高级用法：
    //   - sealed interface 定义工作流节点类型
    //   - record 定义每种节点的数据结构
    //   - switch + 解构 + 递归处理审批流程
    //
    // 场景：请假审批流程
    //   开始 → 提交申请 → 主管审批 → (金额>5000 ? 总监审批 : 跳过) → 结束
    //
    // 工作流节点类型：
    //   Start      → 开始节点
    //   Submit     → 提交申请
    //   Approve    → 审批节点（需要指定审批人）
    //   Decision   → 条件分支（根据条件走不同分支）
    //   End        → 结束节点

    sealed interface WorkflowNode permits Start, Submit, Approve, Decision, End {}
    record Start(String id, WorkflowNode next) implements WorkflowNode {}
    record Submit(String id, String applicant, double amount, WorkflowNode next) implements WorkflowNode {}
    record Approve(String id, String approver, WorkflowNode next) implements WorkflowNode {}
    record Decision(String id, String condition, WorkflowNode trueBranch, WorkflowNode falseBranch) implements WorkflowNode {}
    record End(String id, String status) implements WorkflowNode {}

    /**
     * 打印工作流结构（解构 + 递归）
     */
    static String printWorkflow(WorkflowNode node, int indent) {
        String pad = "  ".repeat(indent);
        return switch (node) {
            case Start(var id, var next) ->
                pad + "[开始] " + id + "\n" + printWorkflow(next, indent + 1);

            case Submit(var id, var applicant, var amount, var next) ->
                pad + "[提交] " + id + " | 申请人: " + applicant + " | 金额: " + amount + "\n"
                    + printWorkflow(next, indent + 1);

            case Approve(var id, var approver, var next) ->
                pad + "[审批] " + id + " | 审批人: " + approver + "\n"
                    + printWorkflow(next, indent + 1);

            case Decision(var id, var condition, var trueBranch, var falseBranch) ->
                pad + "[判断] " + id + " | 条件: " + condition + "\n"
                    + pad + "  ├─ 是:\n" + printWorkflow(trueBranch, indent + 2)
                    + pad + "  └─ 否:\n" + printWorkflow(falseBranch, indent + 2);

            case End(var id, var status) ->
                pad + "[结束] " + id + " | 状态: " + status;
        };
    }

    /**
     * 模拟执行工作流（解构 + 递归 + 条件判断）
     */
    static String executeWorkflow(WorkflowNode node, double amount) {
        return switch (node) {
            case Start(_, var next) ->
                "→ 开始\n" + executeWorkflow(next, amount);

            case Submit(var id, var applicant, var amt, var next) ->
                "→ " + applicant + " 提交申请，金额: " + amt + "\n"
                    + executeWorkflow(next, amt);

            case Approve(var id, var approver, var next) ->
                "→ " + approver + " 审批通过\n"
                    + executeWorkflow(next, amount);

            case Decision(_, _, var trueBranch, var falseBranch) ->
                amount > 5000
                    ? "→ 金额 > 5000，需要总监审批\n" + executeWorkflow(trueBranch, amount)
                    : "→ 金额 ≤ 5000，主管审批即可\n" + executeWorkflow(falseBranch, amount);

            case End(_, var status) ->
                "→ 流程结束: " + status;
        };
    }

    /**
     * 统计工作流节点数（解构 + 递归）
     */
    static int countNodes(WorkflowNode node) {
        return switch (node) {
            case Start(_, var next) -> 1 + countNodes(next);
            case Submit(_, _, _, var next) -> 1 + countNodes(next);
            case Approve(_, _, var next) -> 1 + countNodes(next);
            case Decision(_, _, var trueBranch, var falseBranch) -> 1 + countNodes(trueBranch) + countNodes(falseBranch);
            case End(_, _) -> 1;
        };
    }

    // ========================================================================
    // Before 对比：手动 getter 写法
    // ========================================================================
    // 展示 Record Patterns 替代了多少冗长的 getter 调用
    static String formatShapeBefore(Object shape) {
        if (shape instanceof Point p) {
            return "(%d, %d)".formatted(p.x(), p.y());
        } else if (shape instanceof Line l) {
            Point s = l.start();   // 手动 getter
            Point e = l.end();     // 手动 getter
            return "Line(%d,%d → %d,%d)".formatted(s.x(), s.y(), e.x(), e.y());
        } else if (shape instanceof Circle c) {
            Point center = c.center();  // 手动 getter
            return "Circle(center=(%d,%d), r=%.1f)".formatted(center.x(), center.y(), c.radius());
        } else {
            return "unknown";
        }
    }

    // ========================================================================
    // 主入口：由简单到复杂
    // ========================================================================
    public static void main(String[] args) {
        System.out.println("=== 1.4 Record Patterns：解构赋值 ===\n");

        // ---- ① 基础解构 ----
        System.out.println("① 基础解构（单层 Record）");
        System.out.println("  " + formatPoint(new Point(3, 5)));
        System.out.println("  语法: case Point(var x, var y) → 绑定 x 和 y");

        // ---- ② 嵌套解构 ----
        System.out.println("\n② 嵌套解构（多层 Record）");
        System.out.println("  " + formatLine(new Line(new Point(0, 0), new Point(10, 10))));
        System.out.println("  语法: Line(Point(var x1, var y1), Point(var x2, var y2))");

        // ---- ③ switch 中解构多种类型 ----
        System.out.println("\n③ switch 中解构多种类型");
        Object[] shapes = {
            new Point(3, 5),
            new Line(new Point(0, 0), new Point(10, 10)),
            new Circle(new Point(5, 5), 3.0),
            new Rect(new Point(1, 1), new Point(6, 4))
        };
        for (var s : shapes) {
            System.out.println("  " + formatShape(s));
        }
        System.out.println("  说明: 每种类型有不同的解构深度");

        // ---- ④ 守卫条件 + 解构 ----
        System.out.println("\n④ 守卫条件 + 解构");
        Circle circle = new Circle(new Point(5, 5), 3.0);
        Rect rect = new Rect(new Point(0, 0), new Point(10, 10));
        System.out.println("  " + pointRelation(circle, new Point(5, 5)));   // 圆心
        System.out.println("  " + pointRelation(circle, new Point(10, 5)));  // 圆外
        System.out.println("  " + pointRelation(rect, new Point(3, 7)));     // 矩形内
        System.out.println("  " + pointRelation(rect, new Point(15, 5)));    // 矩形外
        System.out.println("  说明: 解构后可以用 yield 返回计算结果");

        // ---- ⑤ 面积计算 ----
        System.out.println("\n⑤ 面积计算");
        for (var s : shapes) {
            System.out.printf("  %-40s 面积: %.2f%n", formatShape(s), area(s));
        }
        System.out.println("  说明: 解构 + 计算，替代 getter 链");

        // ---- ⑥ 工作流审批流程 ----
        System.out.println("\n⑥ 工作流审批流程（解构 + 递归）");

        // 构建工作流：请假审批
        // 开始 → 提交申请 → 主管审批 → (金额>5000 ? 总监审批 : 跳过) → 结束
        WorkflowNode workflow = new Start("start",
            new Submit("submit", "张三", 8000,
                new Approve("mgr", "李经理",
                    new Decision("check-amount", "金额 > 5000",
                        new Approve("dir", "王总监",
                            new End("end-approve", "审批通过")),
                        new End("end-auto", "自动通过")))));

        System.out.println("  工作流结构:");
        System.out.println(printWorkflow(workflow, 0));

        System.out.println("  模拟执行（金额 8000）:");
        System.out.println(executeWorkflow(workflow, 8000));

        System.out.println("  节点总数: " + countNodes(workflow));
        System.out.println("  说明: sealed + record + switch + 递归 = 工作流 DSL");

        // ---- Before / After 对比 ----
        System.out.println("\nBefore / After 对比");
        System.out.println("  Before (getter 链): " + formatShapeBefore(new Line(new Point(0, 0), new Point(10, 10))));
        System.out.println("  After  (解构):      " + formatShape(new Line(new Point(0, 0), new Point(10, 10))));
        System.out.println("  说明: 解构让嵌套数据访问变得简洁");

        System.out.println("\n总结:");
        System.out.println("  Record Patterns 让嵌套数据的访问变得声明式");
        System.out.println("  switch + 解构 = 替代 getter 链的优雅方案");
    }
}
