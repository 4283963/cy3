package com.cy3.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
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
}
