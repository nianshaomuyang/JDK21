package com.example.jdk21.vt.order.model;

/**
 * 用户模型
 */
public record User(
    long userId,
    String name,
    String level,
    String phone
) {}
