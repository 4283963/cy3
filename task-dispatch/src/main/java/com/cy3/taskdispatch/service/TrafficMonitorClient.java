package com.cy3.taskdispatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class TrafficMonitorClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${traffic-monitor.service.url:http://localhost:8082}")
    private String trafficMonitorUrl;

    public boolean isNodeAvailable(String nodeCode) {
        try {
            String url = trafficMonitorUrl + "/api/traffic/available?nodeCode=" + nodeCode;
            Boolean result = restTemplate.getForObject(url, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.warn("检查节点流量状态失败，节点: {}, 错误: {}", nodeCode, e.getMessage());
            return true;
        }
    }

    public long getTodayUsedTraffic(String nodeCode) {
        try {
            String url = trafficMonitorUrl + "/api/traffic/today?nodeCode=" + nodeCode;
            Long result = restTemplate.getForObject(url, Long.class);
            return result != null ? result : 0L;
        } catch (Exception e) {
            log.warn("获取节点今日流量失败，节点: {}, 错误: {}", nodeCode, e.getMessage());
            return 0L;
        }
    }

    public void recordTraffic(String nodeCode, long sizeKb) {
        try {
            String url = trafficMonitorUrl + "/api/traffic/record?nodeCode=" + nodeCode + "&sizeKb=" + sizeKb;
            restTemplate.postForObject(url, null, String.class);
        } catch (Exception e) {
            log.error("记录流量失败，节点: {}, 大小: {}KB, 错误: {}", nodeCode, sizeKb, e.getMessage());
        }
    }
}
