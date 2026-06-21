package com.cy3.trafficmonitor.schedule;

import com.cy3.trafficmonitor.service.TrafficMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrafficCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrafficCheckScheduler.class);

    private final TrafficMonitorService trafficMonitorService;

    public TrafficCheckScheduler(TrafficMonitorService trafficMonitorService) {
        this.trafficMonitorService = trafficMonitorService;
    }

    @Scheduled(fixedRate = 60000)
    public void checkOverLimitNodes() {
        log.debug("开始定时检查超限节点...");
        trafficMonitorService.checkAndShutOffOverLimitNodes();
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void dailyResetLog() {
        log.info("每日流量统计周期已刷新");
    }
}
