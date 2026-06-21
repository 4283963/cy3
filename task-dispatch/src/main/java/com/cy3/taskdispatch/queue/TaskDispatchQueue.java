package com.cy3.taskdispatch.queue;

import com.cy3.common.entity.CrawlTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TaskDispatchQueue {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatchQueue.class);

    private static final int MAX_QUEUE_SIZE = 10000;

    private final PriorityBlockingQueue<QueuedTask> pendingQueue;

    private final AtomicInteger totalDispatched = new AtomicInteger(0);

    private final AtomicInteger totalFailed = new AtomicInteger(0);

    public TaskDispatchQueue() {
        this.pendingQueue = new PriorityBlockingQueue<>(
                MAX_QUEUE_SIZE,
                Comparator.comparingInt(QueuedTask::getPriority).reversed()
                        .thenComparing(QueuedTask::getCreateTime)
        );
    }

    public boolean offer(CrawlTask task) {
        if (task == null || task.getTaskCode() == null) {
            return false;
        }
        QueuedTask queuedTask = new QueuedTask(
                task.getTaskCode(),
                task.getPriority(),
                task.getCreateTime(),
                task.getEstimatedSizeKb()
        );
        boolean offered = pendingQueue.offer(queuedTask);
        if (offered) {
            log.debug("任务 [{}] 已加入分发队列，当前队列大小: {}", task.getTaskCode(), pendingQueue.size());
        } else {
            log.warn("分发队列已满，任务 [{}] 加入失败", task.getTaskCode());
        }
        return offered;
    }

    public QueuedTask poll() {
        QueuedTask task = pendingQueue.poll();
        if (task != null) {
            log.debug("从队列取出任务 [{}]，剩余队列大小: {}", task.getTaskCode(), pendingQueue.size());
        }
        return task;
    }

    public QueuedTask peek() {
        return pendingQueue.peek();
    }

    public int size() {
        return pendingQueue.size();
    }

    public boolean isEmpty() {
        return pendingQueue.isEmpty();
    }

    public int getTotalDispatched() {
        return totalDispatched.get();
    }

    public int incrementDispatched() {
        return totalDispatched.incrementAndGet();
    }

    public int getTotalFailed() {
        return totalFailed.get();
    }

    public int incrementFailed() {
        return totalFailed.incrementAndGet();
    }

    public boolean remove(String taskCode) {
        return pendingQueue.removeIf(t -> t.getTaskCode().equals(taskCode));
    }

    public void clear() {
        pendingQueue.clear();
        log.info("分发队列已清空");
    }

    public static class QueuedTask {
        private final String taskCode;
        private final int priority;
        private final LocalDateTime createTime;
        private final Long estimatedSizeKb;

        public QueuedTask(String taskCode, int priority, LocalDateTime createTime, Long estimatedSizeKb) {
            this.taskCode = taskCode;
            this.priority = priority;
            this.createTime = createTime;
            this.estimatedSizeKb = estimatedSizeKb;
        }

        public String getTaskCode() {
            return taskCode;
        }

        public int getPriority() {
            return priority;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public Long getEstimatedSizeKb() {
            return estimatedSizeKb;
        }
    }
}
