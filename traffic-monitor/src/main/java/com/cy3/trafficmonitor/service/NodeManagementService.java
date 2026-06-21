package com.cy3.trafficmonitor.service;

import com.cy3.common.entity.CrawlerNode;
import com.cy3.trafficmonitor.repository.CrawlerNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NodeManagementService {

    private static final Logger log = LoggerFactory.getLogger(NodeManagementService.class);

    private final CrawlerNodeRepository nodeRepository;

    public NodeManagementService(CrawlerNodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    public CrawlerNode registerNode(String nodeName, String ipAddress, Long dailyTrafficLimitMb) {
        String nodeCode = "NODE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        CrawlerNode node = new CrawlerNode();
        node.setNodeCode(nodeCode);
        node.setNodeName(nodeName);
        node.setIpAddress(ipAddress);
        node.setStatus(1);
        node.setDailyTrafficLimitMb(dailyTrafficLimitMb != null ? dailyTrafficLimitMb : 10240L);

        log.info("注册新节点: {}", nodeCode);
        return nodeRepository.save(node);
    }

    public CrawlerNode getNode(String nodeCode) {
        return nodeRepository.findByNodeCode(nodeCode).orElse(null);
    }

    public List<CrawlerNode> getAllNodes() {
        return nodeRepository.findAll();
    }

    public List<CrawlerNode> getActiveNodes() {
        return nodeRepository.findByStatus(1);
    }

    @Transactional
    public CrawlerNode updateNode(String nodeCode, String nodeName, String ipAddress, Long dailyTrafficLimitMb) {
        CrawlerNode node = nodeRepository.findByNodeCode(nodeCode)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeCode));

        if (nodeName != null) {
            node.setNodeName(nodeName);
        }
        if (ipAddress != null) {
            node.setIpAddress(ipAddress);
        }
        if (dailyTrafficLimitMb != null) {
            node.setDailyTrafficLimitMb(dailyTrafficLimitMb);
        }

        return nodeRepository.save(node);
    }

    @Transactional
    public void deleteNode(String nodeCode) {
        CrawlerNode node = nodeRepository.findByNodeCode(nodeCode)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeCode));

        nodeRepository.delete(node);
        log.info("删除节点: {}", nodeCode);
    }

    public boolean nodeExists(String nodeCode) {
        return nodeRepository.existsByNodeCode(nodeCode);
    }
}
