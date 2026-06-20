package com.cy3.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "crawler_node")
public class CrawlerNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String nodeCode;

    @Column(nullable = false, length = 128)
    private String nodeName;

    @Column(length = 256)
    private String ipAddress;

    @Column(nullable = false)
    private Integer status;

    @Column(name = "daily_traffic_limit_mb", nullable = false)
    private Long dailyTrafficLimitMb = 10240L;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
