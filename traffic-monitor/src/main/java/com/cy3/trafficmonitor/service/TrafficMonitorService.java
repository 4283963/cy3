package com.cy3.trafficmonitor.service;

import com.cy3.common.entity.CrawlerNode;
import com.cy3.common.entity.TrafficRecord;
import com.cy3.trafficmonitor.repository.CrawlerNodeRepository;
import com.cy3.trafficmonitor.repository.TrafficRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficMonitorService {

    private final TrafficRecordRepository trafficRecordRepository;
    private final CrawlerNodeRepository crawlerNodeRepository;

    @Value("${traffic.warning-threshold:0.9}")
    private double warningThreshold;

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
        if (node == null || node.getStatus() != 1) {
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

        LocalDate today = LocalDate.now();
        TrafficRecord record = trafficRecordRepository.findByNodeCodeAndRecordDate(nodeCode, today)
                .orElseGet(() -> createNewRecord(nodeCode, today));

        long sizeMb = (long) Math.ceil(sizeKb / 1024.0);
        record.setUsedTrafficMb(record.getUsedTrafficMb() + sizeMb);
        record.setRequestCount(record.getRequestCount() + 1);

        CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode).orElse(null);
        if (node != null) {
            long limit = node.getDailyTrafficLimitMb();
            boolean wasOverLimit = Boolean.TRUE.equals(record.getOverLimit());
            boolean isOverLimit = record.getUsedTrafficMb() >= limit;

            if (!wasOverLimit && isOverLimit) {
                record.setOverLimit(true);
                log.warn("节点 [{}] 今日流量已超限！已用: {}MB, 限额: {}MB",
                        nodeCode, record.getUsedTrafficMb(), limit);
            }
        }

        return trafficRecordRepository.save(record);
    }

    private TrafficRecord createNewRecord(String nodeCode, LocalDate date) {
        TrafficRecord record = new TrafficRecord();
        record.setNodeCode(nodeCode);
        record.setRecordDate(date);
        record.setUsedTrafficMb(0L);
        record.setRequestCount(0L);
        record.setOverLimit(false);
        return record;
    }

    @Transactional
    public TrafficRecord resetDailyRecord(String nodeCode, long usedTrafficMb, long requestCount) {
        LocalDate today = LocalDate.now();
        TrafficRecord record = trafficRecordRepository.findByNodeCodeAndRecordDate(nodeCode, today)
                .orElseGet(() -> createNewRecord(nodeCode, today));

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

        if (node.getStatus() == 0) {
            log.info("节点 [{}] 已处于停用状态", nodeCode);
            return true;
        }

        node.setStatus(0);
        crawlerNodeRepository.save(node);

        log.warn("节点 [{}] 已被拉闸停用，原因: {}", nodeCode, reason);
        return true;
    }

    @Transactional
    public boolean restoreNode(String nodeCode) {
        CrawlerNode node = crawlerNodeRepository.findByNodeCode(nodeCode).orElse(null);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在: " + nodeCode);
        }

        node.setStatus(1);
        crawlerNodeRepository.save(node);

        log.info("节点 [{}] 已恢复启用", nodeCode);
        return true;
    }

    @Transactional
    public void checkAndShutOffOverLimitNodes() {
        List<TrafficRecord> overLimitRecords = getTodayOverLimitRecords();
        for (TrafficRecord record : overLimitRecords) {
            CrawlerNode node = crawlerNodeRepository.findByNodeCode(record.getNodeCode()).orElse(null);
            if (node != null && node.getStatus() == 1) {
                shutOffNode(record.getNodeCode(), "今日流量已超限");
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
}
