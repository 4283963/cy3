package com.cy3.trafficmonitor.repository;

import com.cy3.common.entity.TrafficRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrafficRecordRepository extends JpaRepository<TrafficRecord, Long> {

    Optional<TrafficRecord> findByNodeCodeAndRecordDate(String nodeCode, LocalDate recordDate);

    List<TrafficRecord> findByRecordDate(LocalDate recordDate);

    List<TrafficRecord> findByOverLimit(Boolean overLimit);

    List<TrafficRecord> findByRecordDateAndOverLimit(LocalDate recordDate, Boolean overLimit);

    boolean existsByNodeCodeAndRecordDate(String nodeCode, LocalDate recordDate);

    @Modifying
    @Query("UPDATE TrafficRecord t SET t.usedTrafficMb = t.usedTrafficMb + :addMb, " +
           "t.requestCount = t.requestCount + 1, t.updateTime = :updateTime " +
           "WHERE t.nodeCode = :nodeCode AND t.recordDate = :recordDate")
    int incrementTrafficAtomically(@Param("nodeCode") String nodeCode,
                                   @Param("recordDate") LocalDate recordDate,
                                   @Param("addMb") long addMb,
                                   @Param("updateTime") LocalDateTime updateTime);
}
