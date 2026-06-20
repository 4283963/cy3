package com.cy3.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "crawl_task")
public class CrawlTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String taskCode;

    @Column(nullable = false, length = 512)
    private String targetUrl;

    @Column(length = 256)
    private String taskName;

    @Column(name = "assigned_node_code", length = 64)
    private String assignedNodeCode;

    @Column(nullable = false)
    private Integer status;

    @Column(name = "priority", nullable = false)
    private Integer priority = 5;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retry", nullable = false)
    private Integer maxRetry = 3;

    @Column(name = "estimated_size_kb")
    private Long estimatedSizeKb;

    @Column(name = "actual_size_kb")
    private Long actualSizeKb;

    @Column(name = "assign_time")
    private LocalDateTime assignTime;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "finish_time")
    private LocalDateTime finishTime;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) {
            status = TaskStatus.PENDING.getCode();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public enum TaskStatus {
        PENDING(0, "待分发"),
        ASSIGNED(1, "已分配"),
        RUNNING(2, "执行中"),
        SUCCESS(3, "成功"),
        FAILED(4, "失败"),
        CANCELLED(5, "已取消");

        private final int code;
        private final String desc;

        TaskStatus(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public int getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }
    }
}
