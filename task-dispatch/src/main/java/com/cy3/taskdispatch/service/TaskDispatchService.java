package com.cy3.taskdispatch.service;

import com.cy3.common.entity.CrawlTask;
import com.cy3.common.entity.CrawlerNode;
import com.cy3.taskdispatch.repository.CrawlTaskRepository;
import com.cy3.taskdispatch.repository.CrawlerNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDispatchService {

    private final CrawlTaskRepository taskRepository;
    private final CrawlerNodeRepository nodeRepository;
    private final TrafficMonitorClient trafficMonitorClient;

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
        return taskRepository.save(task);
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
        CrawlTask task = taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskCode));

        if (task.getStatus() != CrawlTask.TaskStatus.PENDING.getCode()) {
            throw new IllegalStateException("任务状态不允许分发，当前状态: " + task.getStatus());
        }

        CrawlerNode node = nodeRepository.findByNodeCode(nodeCode)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeCode));

        if (node.getStatus() != 1) {
            throw new IllegalStateException("节点不可用，当前状态: " + node.getStatus());
        }

        if (!trafficMonitorClient.isNodeAvailable(nodeCode)) {
            throw new IllegalStateException("节点今日流量已超限，无法分配新任务");
        }

        task.setAssignedNodeCode(nodeCode);
        task.setStatus(CrawlTask.TaskStatus.ASSIGNED.getCode());
        task.setAssignTime(LocalDateTime.now());

        log.info("任务 [{}] 已分配给节点 [{}]", taskCode, nodeCode);
        return taskRepository.save(task);
    }

    @Transactional
    public CrawlTask autoDispatch(String taskCode) {
        List<CrawlerNode> availableNodes = nodeRepository.findByStatus(1);
        if (availableNodes.isEmpty()) {
            throw new IllegalStateException("没有可用的爬虫节点");
        }

        CrawlerNode bestNode = selectBestNode(availableNodes);
        return dispatchTask(taskCode, bestNode.getNodeCode());
    }

    private CrawlerNode selectBestNode(List<CrawlerNode> nodes) {
        CrawlerNode bestNode = null;
        long minTraffic = Long.MAX_VALUE;

        for (CrawlerNode node : nodes) {
            if (!trafficMonitorClient.isNodeAvailable(node.getNodeCode())) {
                continue;
            }
            long usedTraffic = trafficMonitorClient.getTodayUsedTraffic(node.getNodeCode());
            if (usedTraffic < minTraffic) {
                minTraffic = usedTraffic;
                bestNode = node;
            }
        }

        if (bestNode == null) {
            throw new IllegalStateException("所有可用节点今日流量均已超限");
        }

        return bestNode;
    }

    @Transactional
    public CrawlTask updateTaskStatus(String taskCode, Integer status, Long actualSizeKb) {
        CrawlTask task = taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskCode));

        task.setStatus(status);

        if (status.equals(CrawlTask.TaskStatus.RUNNING.getCode())) {
            task.setStartTime(LocalDateTime.now());
        } else if (status.equals(CrawlTask.TaskStatus.SUCCESS.getCode())
                || status.equals(CrawlTask.TaskStatus.FAILED.getCode())) {
            task.setFinishTime(LocalDateTime.now());
            if (actualSizeKb != null && task.getAssignedNodeCode() != null) {
                task.setActualSizeKb(actualSizeKb);
                trafficMonitorClient.recordTraffic(task.getAssignedNodeCode(), actualSizeKb);
            }
        }

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

        taskRepository.save(task);
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
        taskRepository.save(task);
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

    @Transactional
    public void batchDispatch(int batchSize) {
        Pageable pageable = PageRequest.of(0, batchSize);
        List<CrawlTask> pendingTasks = taskRepository.findPendingTasksOrderByPriority(pageable);

        for (CrawlTask task : pendingTasks) {
            try {
                autoDispatch(task.getTaskCode());
            } catch (Exception e) {
                log.warn("自动分发任务 [{}] 失败: {}", task.getTaskCode(), e.getMessage());
            }
        }
    }
}
