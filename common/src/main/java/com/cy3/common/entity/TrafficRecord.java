package com.cy3.common.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "traffic_record", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"nodeCode", "recordDate"})
})
public class TrafficRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_code", nullable = false, length = 64)
    private String nodeCode;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "used_traffic_mb", nullable = false)
    private Long usedTrafficMb = 0L;

    @Column(name = "request_count", nullable = false)
    private Long requestCount = 0L;

    @Column(name = "is_over_limit", nullable = false)
    private Boolean overLimit = false;

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
        if (recordDate == null) {
            recordDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
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

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public Long getUsedTrafficMb() {
        return usedTrafficMb;
    }

    public void setUsedTrafficMb(Long usedTrafficMb) {
        this.usedTrafficMb = usedTrafficMb;
    }

    public Long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(Long requestCount) {
        this.requestCount = requestCount;
    }

    public Boolean getOverLimit() {
        return overLimit;
    }

    public void setOverLimit(Boolean overLimit) {
        this.overLimit = overLimit;
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
