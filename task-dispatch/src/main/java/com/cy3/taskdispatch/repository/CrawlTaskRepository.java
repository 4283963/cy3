package com.cy3.taskdispatch.repository;

import com.cy3.common.entity.CrawlTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
