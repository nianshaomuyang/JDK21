package com.example.jdk21.collections;

import java.util.*;

/**
 * ============================================================================
 * 4.1 Sequenced Collections 演示 — 由简单到复杂
 * ============================================================================
 *
 * 递进路线：
 *   ① List 首尾访问                    → 最基础的改进
 *   ② Set 首尾访问                     → LinkedHashSet
 *   ③ Map 首尾访问                     → LinkedHashMap
 *   ④ 反转视图                         → reversed()
 *   ⑤ 实战场景                         → LRU 缓存、栈/队列
 *
 * 核心价值：
 *   统一了 List/Set/Map 的首尾访问语义
 *   消除了 get(list.size()-1) 这种丑陋写法
 *
 * 新增接口：
 *   SequencedCollection<E>  ← List, Deque, SortedSet
 *   SequencedSet<E>         ← LinkedHashSet, TreeSet
 *   SequencedMap<K,V>       ← LinkedHashMap, TreeMap
 */
public class SequencedDemo {

    public static void main(String[] args) {
        System.out.println("=== 4.1 Sequenced Collections 演示 ===\n");

        // ========================================================================
        // ① List 首尾访问：最基础的改进
        // ========================================================================
        System.out.println("① List 首尾访问");

        List<String> list = new ArrayList<>(List.of("A", "B", "C", "D", "E"));

        // Before: 丑陋的写法
        System.out.println("  Before:");
        System.out.println("    首元素: list.get(0) → " + list.get(0));
        System.out.println("    尾元素: list.get(list.size()-1) → " + list.get(list.size() - 1));

        // After: 语义清晰的写法
        System.out.println("  After:");
        System.out.println("    首元素: list.getFirst() → " + list.getFirst());
        System.out.println("    尾元素: list.getLast() → " + list.getLast());

        // 新增/删除首尾
        list.addFirst("Z");
        list.addLast("Y");
        System.out.println("    addFirst('Z'), addLast('Y'): " + list);

        String removed = list.removeFirst();
        System.out.println("    removeFirst(): " + removed + " → " + list);

        // ========================================================================
        // ② Set 首尾访问
        // ========================================================================
        System.out.println("\n② Set 首尾访问");

        SequencedSet<String> set = new LinkedHashSet<>(List.of("X", "Y", "Z"));
        System.out.println("    首元素: " + set.getFirst());
        System.out.println("    尾元素: " + set.getLast());
        set.addFirst("W");
        set.addLast("A");
        System.out.println("    addFirst('W'), addLast('A'): " + set);

        // ========================================================================
        // ③ Map 首尾访问
        // ========================================================================
        System.out.println("\n③ Map 首尾访问");

        SequencedMap<String, Integer> map = new LinkedHashMap<>();
        map.put("first", 1);
        map.put("second", 2);
        map.put("third", 3);
        map.put("last", 99);

        // Before: 需要 iterator
        System.out.println("  Before:");
        var firstEntry = map.entrySet().iterator().next();
        System.out.println("    首 Entry: iterator().next() → " + firstEntry);
        System.out.println("    尾 Entry: 需要遍历到最后... 很麻烦");

        // After: 直接方法调用
        System.out.println("  After:");
        System.out.println("    首 Entry: map.firstEntry() → " + map.firstEntry());
        System.out.println("    尾 Entry: map.lastEntry() → " + map.lastEntry());

        // 新增首尾
        map.putFirst("zero", 0);
        map.putLast("end", 100);
        System.out.println("    putFirst/putLast: " + map);

        // poll 首尾
        var polled = map.pollFirstEntry();
        System.out.println("    pollFirstEntry(): " + polled + " → " + map);

        // ========================================================================
        // ④ 反转视图
        // ========================================================================
        System.out.println("\n④ 反转视图");

        SequencedCollection<Integer> nums = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        System.out.println("    原始: " + nums);
        System.out.println("    reversed(): " + nums.reversed());
        System.out.println("    原始不变: " + nums);
        System.out.println("    说明: reversed() 返回视图，不是拷贝");

        // ========================================================================
        // ⑤ 实战场景
        // ========================================================================
        System.out.println("\n⑤ 实战场景");

        // LRU 缓存
        System.out.println("  LRU 缓存（基于 LinkedHashMap）:");
        SequencedMap<String, String> lruCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > 3;  // 最多 3 个元素
            }
        };
        lruCache.put("a", "1");
        lruCache.put("b", "2");
        lruCache.put("c", "3");
        lruCache.get("a");     // 访问 a，移到最后
        lruCache.put("d", "4"); // 超过容量，淘汰最久未使用的
        System.out.println("    缓存: " + lruCache);
        System.out.println("    最久未用: " + lruCache.firstEntry());
        System.out.println("    最近使用: " + lruCache.lastEntry());

        // 栈/队列语义
        System.out.println("\n  栈/队列语义:");
        SequencedCollection<Integer> deque = new ArrayDeque<>(List.of(1, 2, 3));
        System.out.println("    初始: " + deque);
        deque.addFirst(0);   // push
        deque.addLast(4);    // offer
        System.out.println("    addFirst(0), addLast(4): " + deque);
        System.out.println("    removeFirst(): " + deque.removeFirst());  // pop
        System.out.println("    removeLast(): " + deque.removeLast());

        System.out.println("\n总结:");
        System.out.println("  Sequenced Collections 统一了集合的首尾访问语义");
        System.out.println("  reversed() 返回零拷贝反序视图");
        System.out.println("  消除了 get(list.size()-1) 这种丑陋写法");
    }
}
