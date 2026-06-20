package com.cy3.taskdispatch.repository;

import com.cy3.common.entity.CrawlerNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrawlerNodeRepository extends JpaRepository<CrawlerNode, Long> {

    Optional<CrawlerNode> findByNodeCode(String nodeCode);

    List<CrawlerNode> findByStatus(Integer status);

    boolean existsByNodeCode(String nodeCode);
}
