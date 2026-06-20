package com.cy3.trafficmonitor.controller;

import com.cy3.common.dto.Result;
import com.cy3.common.entity.CrawlerNode;
import com.cy3.trafficmonitor.service.NodeManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
public class NodeController {

    private final NodeManagementService nodeManagementService;

    @PostMapping
    public Result<CrawlerNode> registerNode(@RequestBody Map<String, Object> request) {
        String nodeName = (String) request.get("nodeName");
        String ipAddress = (String) request.get("ipAddress");
        Long dailyTrafficLimitMb = request.get("dailyTrafficLimitMb") != null
                ? Long.valueOf(request.get("dailyTrafficLimitMb").toString()) : null;

        if (nodeName == null || nodeName.isBlank()) {
            return Result.fail("节点名称不能为空");
        }

        CrawlerNode node = nodeManagementService.registerNode(nodeName, ipAddress, dailyTrafficLimitMb);
        return Result.success(node);
    }

    @GetMapping("/{nodeCode}")
    public Result<CrawlerNode> getNode(@PathVariable String nodeCode) {
        CrawlerNode node = nodeManagementService.getNode(nodeCode);
        if (node == null) {
            return Result.fail(404, "节点不存在");
        }
        return Result.success(node);
    }

    @GetMapping
    public Result<List<CrawlerNode>> getAllNodes() {
        List<CrawlerNode> nodes = nodeManagementService.getAllNodes();
        return Result.success(nodes);
    }

    @GetMapping("/active")
    public Result<List<CrawlerNode>> getActiveNodes() {
        List<CrawlerNode> nodes = nodeManagementService.getActiveNodes();
        return Result.success(nodes);
    }

    @PutMapping("/{nodeCode}")
    public Result<CrawlerNode> updateNode(
            @PathVariable String nodeCode,
            @RequestBody Map<String, Object> request) {
        String nodeName = (String) request.get("nodeName");
        String ipAddress = (String) request.get("ipAddress");
        Long dailyTrafficLimitMb = request.get("dailyTrafficLimitMb") != null
                ? Long.valueOf(request.get("dailyTrafficLimitMb").toString()) : null;

        try {
            CrawlerNode node = nodeManagementService.updateNode(nodeCode, nodeName, ipAddress, dailyTrafficLimitMb);
            return Result.success(node);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{nodeCode}")
    public Result<String> deleteNode(@PathVariable String nodeCode) {
        try {
            nodeManagementService.deleteNode(nodeCode);
            return Result.success("节点已删除");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }
}
