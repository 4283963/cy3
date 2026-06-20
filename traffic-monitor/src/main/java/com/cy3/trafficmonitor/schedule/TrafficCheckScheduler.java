package com.cy3.trafficmonitor.schedule;

import com.cy3.trafficmonitor.service.TrafficMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrafficCheckScheduler {

    private final TrafficMonitorService trafficMonitorService;

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
