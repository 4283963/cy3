package com.cy3.trafficmonitor.repository;

import com.cy3.common.entity.TrafficRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrafficRecordRepository extends JpaRepository<TrafficRecord, Long> {

    Optional<TrafficRecord> findByNodeCodeAndRecordDate(String nodeCode, LocalDate recordDate);

    List<TrafficRecord> findByRecordDate(LocalDate recordDate);

    List<TrafficRecord> findByOverLimit(Boolean overLimit);

    List<TrafficRecord> findByRecordDateAndOverLimit(LocalDate recordDate, Boolean overLimit);

    boolean existsByNodeCodeAndRecordDate(String nodeCode, LocalDate recordDate);
}
