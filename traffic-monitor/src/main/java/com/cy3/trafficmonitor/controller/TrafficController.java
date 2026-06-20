package com.cy3.trafficmonitor.controller;

import com.cy3.common.dto.Result;
import com.cy3.common.entity.TrafficRecord;
import com.cy3.trafficmonitor.service.TrafficMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/traffic")
@RequiredArgsConstructor
public class TrafficController {

    private final TrafficMonitorService trafficMonitorService;

    @PostMapping("/record")
    public Result<TrafficRecord> recordTraffic(
            @RequestParam String nodeCode,
            @RequestParam long sizeKb) {
        try {
            TrafficRecord record = trafficMonitorService.recordTraffic(nodeCode, sizeKb);
            return Result.success(record);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/today")
    public Result<Long> getTodayUsedTraffic(@RequestParam String nodeCode) {
        long usedTraffic = trafficMonitorService.getTodayUsedTraffic(nodeCode);
        return Result.success(usedTraffic);
    }

    @GetMapping("/today/requests")
    public Result<Long> getTodayRequestCount(@RequestParam String nodeCode) {
        long count = trafficMonitorService.getTodayRequestCount(nodeCode);
        return Result.success(count);
    }

    @GetMapping("/today/record")
    public Result<TrafficRecord> getTodayRecord(@RequestParam String nodeCode) {
        TrafficRecord record = trafficMonitorService.getTodayRecord(nodeCode);
        return Result.success(record);
    }

    @GetMapping("/available")
    public Result<Boolean> isNodeAvailable(@RequestParam String nodeCode) {
        boolean available = trafficMonitorService.isNodeAvailable(nodeCode);
        return Result.success(available);
    }

    @GetMapping("/usage-ratio")
    public Result<Double> getUsageRatio(@RequestParam String nodeCode) {
        double ratio = trafficMonitorService.getUsageRatio(nodeCode);
        return Result.success(ratio);
    }

    @GetMapping("/limit")
    public Result<Long> getDailyLimit(@RequestParam String nodeCode) {
        long limit = trafficMonitorService.getDailyLimit(nodeCode);
        return Result.success(limit);
    }

    @PutMapping("/limit")
    public Result<?> updateDailyLimit(
            @RequestParam String nodeCode,
            @RequestParam long dailyLimitMb) {
        try {
            trafficMonitorService.updateDailyLimit(nodeCode, dailyLimitMb);
            return Result.success("日流量限额已更新");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/today/all")
    public Result<List<TrafficRecord>> getTodayAllRecords() {
        List<TrafficRecord> records = trafficMonitorService.getTodayAllRecords();
        return Result.success(records);
    }

    @GetMapping("/today/over-limit")
    public Result<List<TrafficRecord>> getTodayOverLimitRecords() {
        List<TrafficRecord> records = trafficMonitorService.getTodayOverLimitRecords();
        return Result.success(records);
    }

    @PostMapping("/shutoff")
    public Result<String> shutOffNode(
            @RequestParam String nodeCode,
            @RequestParam(defaultValue = "手动拉闸") String reason) {
        try {
            trafficMonitorService.shutOffNode(nodeCode, reason);
            return Result.success("节点已拉闸停用");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/restore")
    public Result<String> restoreNode(@RequestParam String nodeCode) {
        try {
            trafficMonitorService.restoreNode(nodeCode);
            return Result.success("节点已恢复启用");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/reset")
    public Result<TrafficRecord> resetDailyRecord(
            @RequestParam String nodeCode,
            @RequestParam long usedTrafficMb,
            @RequestParam long requestCount) {
        try {
            TrafficRecord record = trafficMonitorService.resetDailyRecord(nodeCode, usedTrafficMb, requestCount);
            return Result.success(record);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }
}
