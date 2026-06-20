package com.cy3.taskdispatch.controller;

import com.cy3.common.dto.Result;
import com.cy3.common.entity.CrawlTask;
import com.cy3.taskdispatch.service.TaskDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskDispatchService taskDispatchService;

    @PostMapping
    public Result<CrawlTask> createTask(@RequestBody Map<String, Object> request) {
        String targetUrl = (String) request.get("targetUrl");
        String taskName = (String) request.get("taskName");
        Integer priority = request.get("priority") != null ? (Integer) request.get("priority") : null;
        Long estimatedSizeKb = request.get("estimatedSizeKb") != null
                ? Long.valueOf(request.get("estimatedSizeKb").toString()) : null;

        if (targetUrl == null || targetUrl.isBlank()) {
            return Result.fail("目标URL不能为空");
        }

        CrawlTask task = taskDispatchService.createTask(targetUrl, taskName, priority, estimatedSizeKb);
        return Result.success(task);
    }

    @GetMapping("/{taskCode}")
    public Result<CrawlTask> getTask(@PathVariable String taskCode) {
        CrawlTask task = taskDispatchService.getTaskByCode(taskCode);
        if (task == null) {
            return Result.fail(404, "任务不存在");
        }
        return Result.success(task);
    }

    @GetMapping
    public Result<Page<CrawlTask>> getTaskList(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CrawlTask> tasks = taskDispatchService.getTaskList(status, page, size);
        return Result.success(tasks);
    }

    @GetMapping("/node/{nodeCode}")
    public Result<Page<CrawlTask>> getTasksByNode(
            @PathVariable String nodeCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CrawlTask> tasks = taskDispatchService.getTasksByNode(nodeCode, page, size);
        return Result.success(tasks);
    }

    @PostMapping("/{taskCode}/dispatch")
    public Result<CrawlTask> dispatchTask(
            @PathVariable String taskCode,
            @RequestParam(required = false) String nodeCode) {
        try {
            CrawlTask task;
            if (nodeCode != null && !nodeCode.isBlank()) {
                task = taskDispatchService.dispatchTask(taskCode, nodeCode);
            } else {
                task = taskDispatchService.autoDispatch(taskCode);
            }
            return Result.success(task);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/batch-dispatch")
    public Result<String> batchDispatch(@RequestParam(defaultValue = "10") int batchSize) {
        taskDispatchService.batchDispatch(batchSize);
        return Result.success("批量分发完成");
    }

    @PutMapping("/{taskCode}/status")
    public Result<CrawlTask> updateTaskStatus(
            @PathVariable String taskCode,
            @RequestBody Map<String, Object> request) {
        Integer status = (Integer) request.get("status");
        Long actualSizeKb = request.get("actualSizeKb") != null
                ? Long.valueOf(request.get("actualSizeKb").toString()) : null;

        if (status == null) {
            return Result.fail("状态不能为空");
        }

        try {
            CrawlTask task = taskDispatchService.updateTaskStatus(taskCode, status, actualSizeKb);
            return Result.success(task);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/{taskCode}/retry")
    public Result<Boolean> retryTask(@PathVariable String taskCode) {
        try {
            boolean result = taskDispatchService.retryTask(taskCode);
            return Result.success(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/{taskCode}/cancel")
    public Result<String> cancelTask(@PathVariable String taskCode) {
        try {
            taskDispatchService.cancelTask(taskCode);
            return Result.success("任务已取消");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/count")
    public Result<Long> getTaskCount(@RequestParam(required = false) Integer status) {
        long count = taskDispatchService.getTaskCount(status);
        return Result.success(count);
    }
}
