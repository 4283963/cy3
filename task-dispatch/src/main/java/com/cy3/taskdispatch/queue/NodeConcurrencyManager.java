package com.cy3.taskdispatch.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class NodeConcurrencyManager {

    private static final Logger log = LoggerFactory.getLogger(NodeConcurrencyManager.class);

    @Value("${task-dispatch.node.max-concurrent:5}")
    private int maxConcurrentPerNode;

    private final ConcurrentHashMap<String, AtomicInteger> nodeTaskCounts = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, ReentrantLock> nodeLocks = new ConcurrentHashMap<>();

    public ReentrantLock getNodeLock(String nodeCode) {
        return nodeLocks.computeIfAbsent(nodeCode, k -> new ReentrantLock());
    }

    public int getRunningTaskCount(String nodeCode) {
        AtomicInteger count = nodeTaskCounts.get(nodeCode);
        return count != null ? count.get() : 0;
    }

    public boolean canAcceptTask(String nodeCode) {
        return getRunningTaskCount(nodeCode) < maxConcurrentPerNode;
    }

    public int incrementTaskCount(String nodeCode) {
        return nodeTaskCounts.computeIfAbsent(nodeCode, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public int decrementTaskCount(String nodeCode) {
        AtomicInteger count = nodeTaskCounts.get(nodeCode);
        if (count == null) {
            return 0;
        }
        int result = count.decrementAndGet();
        if (result < 0) {
            count.set(0);
            return 0;
        }
        return result;
    }

    public void resetNodeCount(String nodeCode) {
        AtomicInteger count = nodeTaskCounts.get(nodeCode);
        if (count != null) {
            count.set(0);
        }
    }

    public ConcurrentHashMap<String, AtomicInteger> getAllCounts() {
        return new ConcurrentHashMap<>(nodeTaskCounts);
    }

    public void setMaxConcurrentPerNode(int maxConcurrent) {
        this.maxConcurrentPerNode = maxConcurrent;
        log.info("节点最大并发数已更新为: {}", maxConcurrent);
    }

    public int getMaxConcurrentPerNode() {
        return maxConcurrentPerNode;
    }
}
