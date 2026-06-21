package com.cy3.taskdispatch.service;

import com.cy3.common.entity.CrawlTask;
import com.cy3.common.entity.CrawlerNode;
import com.cy3.taskdispatch.queue.NodeConcurrencyManager;
import com.cy3.taskdispatch.queue.TaskDispatchQueue;
import com.cy3.taskdispatch.repository.CrawlTaskRepository;
import com.cy3.taskdispatch.repository.CrawlerNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TaskDispatchService {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatchService.class);

    private final CrawlTaskRepository taskRepository;
    private final CrawlerNodeRepository nodeRepository;
    private final TrafficMonitorClient trafficMonitorClient;
    private final TaskDispatchQueue taskQueue;
    private final NodeConcurrencyManager nodeConcurrencyManager;

    public TaskDispatchService(CrawlTaskRepository taskRepository,
                               CrawlerNodeRepository nodeRepository,
                               TrafficMonitorClient trafficMonitorClient,
                               TaskDispatchQueue taskQueue,
                               NodeConcurrencyManager nodeConcurrencyManager) {
        this.taskRepository = taskRepository;
        this.nodeRepository = nodeRepository;
        this.trafficMonitorClient = trafficMonitorClient;
        this.taskQueue = taskQueue;
        this.nodeConcurrencyManager = nodeConcurrencyManager;
    }

    @Transactional
    public CrawlTask createTask(String targetUrl, String taskName, Integer priority, Long estimatedSizeKb) {
        CrawlTask task = new CrawlTask();
        task.setTaskCode("TASK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        task.setTargetUrl(targetUrl);
        task.setTaskName(taskName);
        task.setPriority(priority != null ? priority : 5);
        task.setStatus(CrawlTask.TaskStatus.PENDING.getCode());
        task.setEstimatedSizeKb(estimatedSizeKb);
        task.setRetryCount(0);
        task.setMaxRetry(3);

        CrawlTask saved = taskRepository.save(task);
        taskQueue.offer(saved);
        return saved;
    }

    public CrawlTask getTaskByCode(String taskCode) {
        return taskRepository.findByTaskCode(taskCode).orElse(null);
    }

    public Page<CrawlTask> getTaskList(Integer status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        if (status != null) {
            return taskRepository.findByStatus(status, pageable);
        }
        return taskRepository.findAll(pageable);
    }

    public Page<CrawlTask> getTasksByNode(String nodeCode, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return taskRepository.findByAssignedNodeCode(nodeCode, pageable);
    }

    @Transactional
    public CrawlTask dispatchTask(String taskCode, String nodeCode) {
        CrawlerNode node = nodeRepository.findByNodeCode(nodeCode)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeCode));

        if (node.getStatus() != 1) {
            throw new IllegalStateException("节点不可用，当前状态: " + node.getStatus());
        }

        if (!trafficMonitorClient.isNodeAvailable(nodeCode)) {
            throw new IllegalStateException("节点今日流量已超限，无法分配新任务");
        }

        if (!nodeConcurrencyManager.canAcceptTask(nodeCode)) {
            throw new IllegalStateException("节点并发任务数已达上限");
        }

        LocalDateTime now = LocalDateTime.now();
        int updatedRows = taskRepository.assignTaskAtomically(
                taskCode,
                CrawlTask.TaskStatus.PENDING.getCode(),
                CrawlTask.TaskStatus.ASSIGNED.getCode(),
                nodeCode,
                now,
                now
        );

        if (updatedRows == 0) {
            throw new IllegalStateException("任务状态已变更，分发失败（可能已被其他节点抢走）");
        }

        nodeConcurrencyManager.incrementTaskCount(nodeCode);
        taskQueue.remove(taskCode);
        taskQueue.incrementDispatched();

        log.info("任务 [{}] 已分配给节点 [{}]", taskCode, nodeCode);
        return taskRepository.findByTaskCode(taskCode).orElse(null);
    }

    @Transactional
    public CrawlTask autoDispatch(String taskCode) {
        List<CrawlerNode> availableNodes = nodeRepository.findByStatus(1);
        if (availableNodes.isEmpty()) {
            throw new IllegalStateException("没有可用的爬虫节点");
        }

        CrawlerNode bestNode = selectBestNode(availableNodes);
        if (bestNode == null) {
            throw new IllegalStateException("所有可用节点今日流量均已超限或并发已满");
        }

        return dispatchTask(taskCode, bestNode.getNodeCode());
    }

    private CrawlerNode selectBestNode(List<CrawlerNode> nodes) {
        CrawlerNode bestNode = null;
        long minTraffic = Long.MAX_VALUE;

        for (CrawlerNode node : nodes) {
            String nodeCode = node.getNodeCode();

            if (!trafficMonitorClient.isNodeAvailable(nodeCode)) {
                continue;
            }

            if (!nodeConcurrencyManager.canAcceptTask(nodeCode)) {
                continue;
            }

            long usedTraffic = trafficMonitorClient.getTodayUsedTraffic(nodeCode);
            if (usedTraffic < minTraffic) {
                minTraffic = usedTraffic;
                bestNode = node;
            }
        }

        return bestNode;
    }

    public CrawlTask pullTask(String nodeCode) {
        CrawlerNode node = nodeRepository.findByNodeCode(nodeCode)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeCode));

        if (node.getStatus() != 1) {
            throw new IllegalStateException("节点不可用");
        }

        if (!trafficMonitorClient.isNodeAvailable(nodeCode)) {
            throw new IllegalStateException("节点今日流量已超限");
        }

        if (!nodeConcurrencyManager.canAcceptTask(nodeCode)) {
            return null;
        }

        ReentrantLock nodeLock = nodeConcurrencyManager.getNodeLock(nodeCode);
        if (!nodeLock.tryLock()) {
            return null;
        }

        try {
            if (!nodeConcurrencyManager.canAcceptTask(nodeCode)) {
                return null;
            }

            TaskDispatchQueue.QueuedTask queuedTask = taskQueue.poll();
            if (queuedTask == null) {
                return null;
            }

            try {
                return dispatchTask(queuedTask.getTaskCode(), nodeCode);
            } catch (Exception e) {
                log.warn("节点 [{}] 拉取任务 [{}] 失败: {}", nodeCode, queuedTask.getTaskCode(), e.getMessage());
                taskQueue.incrementFailed();
                return null;
            }
        } finally {
            nodeLock.unlock();
        }
    }

    @Transactional
    public CrawlTask updateTaskStatus(String taskCode, Integer status, Long actualSizeKb) {
        CrawlTask task = taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskCode));

        Integer currentStatus = task.getStatus();

        if (status.equals(CrawlTask.TaskStatus.RUNNING.getCode())) {
            task.setStartTime(LocalDateTime.now());
        } else if (status.equals(CrawlTask.TaskStatus.SUCCESS.getCode())
                || status.equals(CrawlTask.TaskStatus.FAILED.getCode())) {
            task.setFinishTime(LocalDateTime.now());
            if (actualSizeKb != null && task.getAssignedNodeCode() != null) {
                task.setActualSizeKb(actualSizeKb);
                trafficMonitorClient.recordTraffic(task.getAssignedNodeCode(), actualSizeKb);
            }
            if (task.getAssignedNodeCode() != null) {
                nodeConcurrencyManager.decrementTaskCount(task.getAssignedNodeCode());
            }
        }

        task.setStatus(status);
        return taskRepository.save(task);
    }

    @Transactional
    public boolean retryTask(String taskCode) {
        CrawlTask task = taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskCode));

        if (task.getRetryCount() >= task.getMaxRetry()) {
            throw new IllegalStateException("已达到最大重试次数");
        }

        task.setRetryCount(task.getRetryCount() + 1);
        task.setStatus(CrawlTask.TaskStatus.PENDING.getCode());
        task.setAssignedNodeCode(null);
        task.setAssignTime(null);
        task.setStartTime(null);
        task.setFinishTime(null);

        CrawlTask saved = taskRepository.save(task);
        taskQueue.offer(saved);

        log.info("任务 [{}] 准备第 {} 次重试", taskCode, task.getRetryCount());
        return true;
    }

    @Transactional
    public void cancelTask(String taskCode) {
        CrawlTask task = taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskCode));

        if (task.getStatus().equals(CrawlTask.TaskStatus.SUCCESS.getCode())
                || task.getStatus().equals(CrawlTask.TaskStatus.FAILED.getCode())
                || task.getStatus().equals(CrawlTask.TaskStatus.CANCELLED.getCode())) {
            throw new IllegalStateException("任务已结束，无法取消");
        }

        task.setStatus(CrawlTask.TaskStatus.CANCELLED.getCode());
        task.setFinishTime(LocalDateTime.now());

        if (task.getAssignedNodeCode() != null) {
            nodeConcurrencyManager.decrementTaskCount(task.getAssignedNodeCode());
        }

        taskRepository.save(task);
        taskQueue.remove(taskCode);

        log.info("任务 [{}] 已取消", taskCode);
    }

    public long getTaskCount(Integer status) {
        if (status != null) {
            return taskRepository.countByStatus(status);
        }
        return taskRepository.count();
    }

    public long getNodeTaskCount(String nodeCode, Integer status) {
        if (status != null) {
            return taskRepository.countByAssignedNodeCodeAndStatus(nodeCode, status);
        }
        return taskRepository.countByAssignedNodeCodeAndStatus(nodeCode, null);
    }

    public int getQueueSize() {
        return taskQueue.size();
    }

    public int getNodeRunningCount(String nodeCode) {
        return nodeConcurrencyManager.getRunningTaskCount(nodeCode);
    }

    @Transactional
    public int batchDispatch(int batchSize) {
        int dispatched = 0;

        List<CrawlerNode> availableNodes = nodeRepository.findByStatus(1);
        if (availableNodes.isEmpty()) {
            return 0;
        }

        for (int i = 0; i < batchSize; i++) {
            CrawlerNode bestNode = selectBestNode(availableNodes);
            if (bestNode == null) {
                break;
            }

            TaskDispatchQueue.QueuedTask queuedTask = taskQueue.poll();
            if (queuedTask == null) {
                break;
            }

            try {
                dispatchTask(queuedTask.getTaskCode(), bestNode.getNodeCode());
                dispatched++;
            } catch (Exception e) {
                log.warn("批量分发任务 [{}] 失败: {}", queuedTask.getTaskCode(), e.getMessage());
                taskQueue.incrementFailed();
            }
        }

        log.info("批量分发完成，成功 {} 个", dispatched);
        return dispatched;
    }

    @Transactional
    public int loadPendingTasksToQueue(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<CrawlTask> pendingTasks = taskRepository.findPendingTasksOrderByPriority(pageable);

        int loaded = 0;
        for (CrawlTask task : pendingTasks) {
            if (taskQueue.offer(task)) {
                loaded++;
            }
        }

        log.info("从数据库加载 {} 个待分发任务到队列", loaded);
        return loaded;
    }
}
