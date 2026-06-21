# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Purpose

This is a JDK 21 新特性分享项目，包含：
- 技术分享文档（Markdown 格式，含 Before/After 代码对比）
- 可运行的 Java 21 Demo 项目（Maven 构建）
- JMH 性能基准测试

目标读者：有 3+ 年经验的 Java 高级开发者。

## Build & Run

```bash
# 编译（需要 JDK 21+）
mvn clean compile

# 运行单个 Demo
mvn exec:java -Dexec.mainClass="com.example.jdk21.vt.VirtualThreadsDemo"

# 运行 JMH 基准测试
mvn clean package -pl benchmark -am
java -jar benchmark/target/benchmarks.jar
```

## Architecture

项目分为文档层和代码层：

- `README.md` — 主文档，JDK 21 新特性全景，含 Before/After 对比
- `docs/` — 深度专题文档（如 Virtual Threads 源码解析）
- `src/` — Demo 代码，按特性域划分包：
  - `vt/` — Virtual Threads 相关（Pinning、Structured Concurrency、Scoped Values）
  - `pattern/` — Pattern Matching & Record Patterns
  - `order/` — Virtual Threads 实战：高并发订单系统
  - `benchmark/` — JMH 性能测试

## Conventions

- Java source: 21+ 语法，充分使用 preview 特性需在 pom.xml 中配置 `--enable-preview`
- 文档语言：中文为主，代码注释中英皆可
- Before/After 对比：文档中用 Markdown 表格或并排代码块
- 性能数据：必须标注 JDK 版本、GC 配置、机器规格
