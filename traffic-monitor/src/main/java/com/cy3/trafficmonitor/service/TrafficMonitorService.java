package com.cy3.trafficmonitor.service;

import com.cy3.common.entity.CrawlerNode;
import com.cy3.common.entity.CrawlerNode.NodeStatus;
import com.cy3.common.entity.TrafficRecord;
import com.cy3.trafficmonitor.repository.CrawlerNodeRepository;
import com.cy3.trafficmonitor.repository.TrafficRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TrafficMonitorService {

    private static final Logger log = LoggerFactory.getLogger(TrafficMonitorService.class);

    private final TrafficRecordRepository trafficRecordRepository;
    private final CrawlerNodeRepository crawlerNodeRepository;

    private final ConcurrentHashMap<String, ReentrantLock> nodeLocks = new ConcurrentHashMap<>();

    public TrafficMonitorService(TrafficRecordRepository trafficRecordRepository,
                                 CrawlerNodeRepository crawlerNodeRepository) {
        this.trafficRecordRepository = trafficRecordRepository;
        this.crawlerNodeRepository = crawlerNodeRepository;
    }

    @Value("${traffic.warning-threshold:0.9}")
    private double warningThreshold;

    private static final int MAX_RETRY = 3;

    public long getTodayUsedTraffic(String nodeCode) {
        LocalDate today = LocalDate.now();
        return trafficRecordRepository.findByNodeCodeAndRecordDate(nodeCode, today)
                .map(TrafficRecord::getUsedTrafficMb)
                .orElse(0L);
    }

    public long getTodayRequestCount(String nodeCode) {
        LocalDate today = LocalDate.now();
        return trafficRecordRepository.findByNodeCodeAndRecordDate(nodeCode, today)
                .map(TrafficRecord::getRequestCount)
                .orElse(0L);
    }

    public TrafficRecord getTodayRecord(String nodeCode) {
        LocalDate today = LocalDate.now();
        return trafficRecordRepository.findByNodeCodeAndRecordDate(nodeCode, today).orElse(null);
    }

    public boolean isNodeAvailable(String nodeCode) {
        CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode).orElse(null);
        if (node == null || !node.getStatus().equals(NodeStatus.RUNNING.getCode())) {
            return false;
        }

        TrafficRecord record = getTodayRecord(nodeCode);
        if (record == null) {
            return true;
        }

        return !record.getOverLimit();
    }

    @Transactional
    public TrafficRecord recordTraffic(String nodeCode, long sizeKb) {
        if (sizeKb <= 0) {
            throw new IllegalArgumentException("流量大小必须大于0");
        }

        long sizeMb = (long) Math.ceil(sizeKb / 1024.0);
        LocalDate today = LocalDate.now();

        ensureRecordExists(nodeCode, today);

        int updatedRows = trafficRecordRepository.incrementTrafficAtomically(
                nodeCode, today, sizeMb, LocalDateTime.now());

        if (updatedRows == 0) {
            throw new IllegalStateException("流量记录更新失败");
        }

        TrafficRecord record = trafficRecordRepository.findByNodeCodeAndRecordDate(nodeCode, today)
                .orElseThrow(() -> new IllegalStateException("流量记录不存在"));

        checkAndMarkOverLimit(nodeCode, record);

        return record;
    }

    private void ensureRecordExists(String nodeCode, LocalDate date) {
        if (trafficRecordRepository.existsByNodeCodeAndRecordDate(nodeCode, date)) {
            return;
        }

        ReentrantLock lock = nodeLocks.computeIfAbsent(nodeCode, k -> new ReentrantLock());
        lock.lock();
        try {
            if (trafficRecordRepository.existsByNodeCodeAndRecordDate(nodeCode, date)) {
                return;
            }
            try {
                TrafficRecord record = new TrafficRecord();
                record.setNodeCode(nodeCode);
                record.setRecordDate(date);
                record.setUsedTrafficMb(0L);
                record.setRequestCount(0L);
                record.setOverLimit(false);
                trafficRecordRepository.save(record);
                log.debug("为节点 [{}] 创建新的日流量记录，日期: {}", nodeCode, date);
            } catch (DataIntegrityViolationException e) {
                log.debug("节点 [{}] 的日流量记录已由其他线程创建", nodeCode);
            }
        } finally {
            lock.unlock();
        }
    }

    private void checkAndMarkOverLimit(String nodeCode, TrafficRecord record) {
        CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode).orElse(null);
        if (node == null) {
            return;
        }

        long limit = node.getDailyTrafficLimitMb();
        if (limit <= 0) {
            return;
        }

        boolean wasOverLimit = Boolean.TRUE.equals(record.getOverLimit());
        boolean isOverLimit = record.getUsedTrafficMb() >= limit;

        if (!wasOverLimit && isOverLimit) {
            record.setOverLimit(true);
            trafficRecordRepository.save(record);
            log.warn("节点 [{}] 今日流量已超限！已用: {}MB, 限额: {}MB",
                    nodeCode, record.getUsedTrafficMb(), limit);
            restNodeOverLimit(nodeCode);
        }
    }

    @Transactional
    public TrafficRecord resetDailyRecord(String nodeCode, long usedTrafficMb, long requestCount) {
        LocalDate today = LocalDate.now();
        TrafficRecord record = trafficRecordRepository.findByNodeCodeAndRecordDate(nodeCode, today)
                .orElseGet(() -> {
                    TrafficRecord r = new TrafficRecord();
                    r.setNodeCode(nodeCode);
                    r.setRecordDate(today);
                    r.setUsedTrafficMb(0L);
                    r.setRequestCount(0L);
                    r.setOverLimit(false);
                    return r;
                });

        record.setUsedTrafficMb(usedTrafficMb);
        record.setRequestCount(requestCount);

        CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode).orElse(null);
        if (node != null) {
            record.setOverLimit(usedTrafficMb >= node.getDailyTrafficLimitMb());
        }

        return trafficRecordRepository.save(record);
    }

    public List<TrafficRecord> getTodayAllRecords() {
        return trafficRecordRepository.findByRecordDate(LocalDate.now());
    }

    public List<TrafficRecord> getTodayOverLimitRecords() {
        return trafficRecordRepository.findByRecordDateAndOverLimit(LocalDate.now(), true);
    }

    public double getUsageRatio(String nodeCode) {
        CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode).orElse(null);
        if (node == null) {
            return 0.0;
        }

        long used = getTodayUsedTraffic(nodeCode);
        long limit = node.getDailyTrafficLimitMb();
        if (limit <= 0) {
            return 0.0;
        }

        return (double) used / limit;
    }

    @Transactional
    public boolean shutOffNode(String nodeCode, String reason) {
        CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode).orElse(null);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在: " + nodeCode);
        }

        if (node.getStatus().equals(NodeStatus.STOPPED.getCode())) {
            log.info("节点 [{}] 已处于停用状态", nodeCode);
            return true;
        }

        node.setStatus(NodeStatus.STOPPED.getCode());
        crawlerNodeRepository.save(node);

        log.warn("节点 [{}] 已被拉闸停用（状态：已停用），原因: {}", nodeCode, reason);
        return true;
    }

    @Transactional
    public boolean restNodeOverLimit(String nodeCode) {
        CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode).orElse(null);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在: " + nodeCode);
        }

        if (node.getStatus().equals(NodeStatus.RESTING.getCode())) {
            log.info("节点 [{}] 已处于休息中状态", nodeCode);
            return true;
        }

        node.setStatus(NodeStatus.RESTING.getCode());
        crawlerNodeRepository.save(node);

        log.warn("节点 [{}] 因流量超限进入休息中状态，等待复位", nodeCode);
        return true;
    }

    @Transactional
    public boolean restoreNode(String nodeCode) {
        CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode).orElse(null);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在: " + nodeCode);
        }

        node.setStatus(NodeStatus.RUNNING.getCode());
        crawlerNodeRepository.save(node);

        log.info("节点 [{}] 已恢复运行中状态", nodeCode);
        return true;
    }

    @Transactional
    public CrawlerNode resetAndResumeNode(String nodeCode) {
        CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeCode));

        if (!node.getStatus().equals(NodeStatus.RESTING.getCode())
                && !node.getStatus().equals(NodeStatus.STOPPED.getCode())) {
            log.info("节点 [{}] 当前状态为 {}，无需复位", nodeCode,
                    NodeStatus.fromCode(node.getStatus()).getDesc());
        }

        LocalDate today = LocalDate.now();
        TrafficRecord record = trafficRecordRepository.findByNodeCodeAndRecordDate(nodeCode, today)
                .orElseGet(() -> {
                    TrafficRecord r = new TrafficRecord();
                    r.setNodeCode(nodeCode);
                    r.setRecordDate(today);
                    return r;
                });

        record.setUsedTrafficMb(0L);
        record.setRequestCount(0L);
        record.setOverLimit(false);
        trafficRecordRepository.save(record);

        node.setStatus(NodeStatus.RUNNING.getCode());
        CrawlerNode saved = crawlerNodeRepository.save(node);

        log.info("节点 [{}] 已复位：流量计数清零，状态恢复为运行中", nodeCode);
        return saved;
    }

    @Transactional
    public void checkAndShutOffOverLimitNodes() {
        List<TrafficRecord> overLimitRecords = getTodayOverLimitRecords();
        for (TrafficRecord record : overLimitRecords) {
            String nodeCode = record.getNodeCode();
            CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode).orElse(null);
            if (node != null && node.getStatus().equals(NodeStatus.RUNNING.getCode())) {
                restNodeOverLimit(nodeCode);
            }
        }
    }

    public long getDailyLimit(String nodeCode) {
        CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode).orElse(null);
        return node != null ? node.getDailyTrafficLimitMb() : 0L;
    }

    @Transactional
    public CrawlerNode updateDailyLimit(String nodeCode, long dailyLimitMb) {
        CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeCode));

        node.setDailyTrafficLimitMb(dailyLimitMb);
        return crawlerNodeRepository.save(node);
    }

    public List<CrawlerNode> getRestingNodes() {
        return crawlerNodeRepository.findByStatus(NodeStatus.RESTING.getCode());
    }
}
