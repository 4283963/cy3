package com.cy3.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public enum NodeStatus {
        STOPPED(0, "已停用"),
        RUNNING(1, "运行中"),
        RESTING(2, "休息中（流量超限）");

        private final int code;
        private final String desc;

        NodeStatus(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public int getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static NodeStatus fromCode(int code) {
            for (NodeStatus s : values()) {
                if (s.code == code) {
                    return s;
                }
            }
            return STOPPED;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getDailyTrafficLimitMb() {
        return dailyTrafficLimitMb;
    }

    public void setDailyTrafficLimitMb(Long dailyTrafficLimitMb) {
        this.dailyTrafficLimitMb = dailyTrafficLimitMb;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
