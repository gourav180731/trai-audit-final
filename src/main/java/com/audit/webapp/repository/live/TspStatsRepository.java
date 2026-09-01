package com.audit.webapp.repository.live;

import com.audit.webapp.entity.live.TspSmsDisseminationStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TspStatsRepository extends JpaRepository<TspSmsDisseminationStatistics, Long> {

    // Generic filtered fetch used by checks via service layer (fallback)
    List<TspSmsDisseminationStatistics> findByStartTimeBetween(LocalDateTime from, LocalDateTime to);

    @Query(value = """
        SELECT * FROM dm.t_tsp_sms_dissemination_statistics s
        WHERE (:from IS NULL OR s.start_time >= CAST(:from AS timestamp))
          AND (:to   IS NULL OR s.start_time <= CAST(:to   AS timestamp))
          AND (:tsp  IS NULL OR LOWER(REPLACE(s.tsp_name,'-',' ')) = LOWER(REPLACE(CAST(:tsp AS varchar),'-',' ')))
        ORDER BY s.identifier, s.tsp_name
        """, nativeQuery = true)
    List<TspSmsDisseminationStatistics> findFiltered(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("tsp") String tspCsv);

    @Query(value = "SELECT COUNT(DISTINCT identifier) FROM dm.t_tsp_sms_dissemination_statistics WHERE (:from IS NULL OR start_time >= CAST(:from AS timestamp)) AND (:to IS NULL OR start_time <= CAST(:to AS timestamp))", nativeQuery = true)
    long countDistinctIdentifiers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT COUNT(*) FROM dm.t_tsp_sms_dissemination_statistics WHERE (:from IS NULL OR start_time >= CAST(:from AS timestamp)) AND (:to IS NULL OR start_time <= CAST(:to AS timestamp))", nativeQuery = true)
    long countRows(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
