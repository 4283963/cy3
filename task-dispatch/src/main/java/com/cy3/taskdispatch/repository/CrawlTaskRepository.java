package com.cy3.taskdispatch.repository;

import com.cy3.common.entity.CrawlTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CrawlTaskRepository extends JpaRepository<CrawlTask, Long> {

    Optional<CrawlTask> findByTaskCode(String taskCode);

    List<CrawlTask> findByStatus(Integer status);

    Page<CrawlTask> findByStatus(Integer status, Pageable pageable);

    Page<CrawlTask> findByAssignedNodeCode(String nodeCode, Pageable pageable);

    List<CrawlTask> findByAssignedNodeCodeAndStatus(String nodeCode, Integer status);

    @Query("SELECT t FROM CrawlTask t WHERE t.status = 0 ORDER BY t.priority DESC, t.createTime ASC")
    List<CrawlTask> findPendingTasksOrderByPriority(Pageable pageable);

    long countByStatus(Integer status);

    long countByAssignedNodeCodeAndStatus(String nodeCode, Integer status);

    @Modifying
    @Query("UPDATE CrawlTask t SET t.status = :newStatus, t.assignedNodeCode = :nodeCode, " +
           "t.assignTime = :assignTime, t.updateTime = :updateTime " +
           "WHERE t.taskCode = :taskCode AND t.status = :expectedStatus")
    int assignTaskAtomically(@Param("taskCode") String taskCode,
                             @Param("expectedStatus") Integer expectedStatus,
                             @Param("newStatus") Integer newStatus,
                             @Param("nodeCode") String nodeCode,
                             @Param("assignTime") LocalDateTime assignTime,
                             @Param("updateTime") LocalDateTime updateTime);

    @Modifying
    @Query("UPDATE CrawlTask t SET t.status = :newStatus, t.updateTime = :updateTime " +
           "WHERE t.taskCode = :taskCode AND t.status = :expectedStatus")
    int updateStatusAtomically(@Param("taskCode") String taskCode,
                               @Param("expectedStatus") Integer expectedStatus,
                               @Param("newStatus") Integer newStatus,
                               @Param("updateTime") LocalDateTime updateTime);
}
