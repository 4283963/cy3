package com.cy3.trafficmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = "com.cy3.common.entity")
@EnableJpaRepositories(basePackages = "com.cy3.trafficmonitor.repository")
public class TrafficMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrafficMonitorApplication.class, args);
    }
}
