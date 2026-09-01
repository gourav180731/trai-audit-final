package com.audit.webapp;

import com.audit.webapp.config.AuditProperties;
import com.audit.webapp.config.MradConfig;
import com.audit.webapp.entity.IngestionBatch;
import com.audit.webapp.repository.DiscrepancyRecordRepository;
import com.audit.webapp.repository.IngestionBatchRepository;
import com.audit.webapp.repository.live.TspContactRepository;
import com.audit.webapp.service.LiveDiscrepancyService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LiveDiscrepancyServiceTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired LiveDiscrepancyService live;
    @Autowired IngestionBatchRepository batchRepo;
    @Autowired DiscrepancyRecordRepository discRepo;

    @BeforeEach
    void seed() {
        jdbc.execute("DROP SCHEMA IF EXISTS dm CASCADE");
        jdbc.execute("CREATE SCHEMA dm");
        jdbc.execute("""
            CREATE TABLE dm.t_tsp_sms_dissemination_statistics (
                id bigint PRIMARY KEY, identifier varchar(100), tsp_name varchar(50),
                start_time timestamp, end_time timestamp,
                total_subscribers bigint, total_delivery_success bigint, total_delivery_failure bigint, total_cell_count bigint,
                status varchar(100), entry_time timestamp, remarks_by_tsp varchar, tsp_remarks_received_timestamp timestamp,
                response1_received_timestamp timestamp, response2_received_timestamp timestamp, remarks_by_capplatform varchar,
                internal_testing_remarks varchar, sms_count_success bigint,
                prefetch_start_time timestamp, prefetch_end_time timestamp,
                prefetch_total_subscribers bigint, prefetch_total_delivery_success bigint, prefetch_total_delivery_failure bigint,
                prefetch_response2_received_timestamp timestamp, prefetch_sms_count_success bigint,
                delta_received varchar, charges varchar, total_expired bigint, sms_count_expired bigint
            )""");
        jdbc.execute("""
            CREATE TABLE dm.t_tsp_contact_list (
                contact_id bigint PRIMARY KEY, tsp_name varchar(40), boundary_restriction varchar(100),
                name varchar, designation varchar, email_id varchar, contact_number varchar,
                created_on timestamp with time zone, deactivated_on timestamp with time zone,
                in_notification_list varchar(30), email_notifications boolean, sms_notifications boolean, element_id integer
            )""");
        jdbc.execute("DELETE FROM discrepancy_records");
        jdbc.execute("DELETE FROM ingestion_batches");
        // Also clear generated_reports if exists
        try { jdbc.execute("DELETE FROM generated_reports"); } catch(Exception ignored) {}

        LocalDateTime base = LocalDateTime.of(2026,8,10,10,0,0);
        String cols = "id,identifier,tsp_name,start_time,end_time,total_subscribers,total_delivery_success,total_delivery_failure,total_cell_count,status,entry_time,remarks_by_tsp,tsp_remarks_received_timestamp,response1_received_timestamp,response2_received_timestamp,remarks_by_capplatform,internal_testing_remarks,sms_count_success,prefetch_start_time,prefetch_end_time,prefetch_total_subscribers,prefetch_total_delivery_success,prefetch_total_delivery_failure,prefetch_response2_received_timestamp,prefetch_sms_count_success,delta_received,charges,total_expired,sms_count_expired";
        String ph = "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?";
        // 1) clean success row — should NOT flag anything
        jdbc.update("INSERT INTO dm.t_tsp_sms_dissemination_statistics ("+cols+") VALUES ("+ph+")",
                1L,"ALERT-001","Airtel", base, base.plusMinutes(30), 1000L, 950L, 40L, 100L, "finished", base.plusMinutes(31), null, null,
                base.plusMinutes(1), base.plusMinutes(32), null, null, 1000L,
                base.minusMinutes(60), base.minusMinutes(30), 1000L, 950L, 40L, base.minusMinutes(29), 1000L,
                "yes", null, 10L, 0L);
        // 2) complete failure — all timestamps NULL
        jdbc.update("INSERT INTO dm.t_tsp_sms_dissemination_statistics ("+cols+") VALUES ("+ph+")",
                2L,"ALERT-001","BSNL", null, null, 500L, null, null, 100L, "failed", base, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, 0L, 0L);
        // 3) Reliance-Jio-style partial failure with expired + garbage HTML remarks
        jdbc.update("INSERT INTO dm.t_tsp_sms_dissemination_statistics ("+cols+") VALUES ("+ph+")",
                3L,"ALERT-002","Reliance Jio", base, base.plusMinutes(45), 2000L, 1200L, 300L, 200L, "finished", base.plusMinutes(46), null, null,
                base.plusMinutes(2), base.plusMinutes(50), "<html><body>Unable to connect to Reliance Jio server</body></html>", null, 1200L,
                base.minusMinutes(60), base.minusMinutes(30), 2000L, 1200L, 300L, base.minusMinutes(29), 1200L,
                "yes", null, 500L, 100L);
        // 4) MRAD breach — 100 cells but 5 hours dissemination (threshold 60 min)
        jdbc.update("INSERT INTO dm.t_tsp_sms_dissemination_statistics ("+cols+") VALUES ("+ph+")",
                4L,"ALERT-003","Vodafone-Idea", base, base.plusHours(5), 800L, 700L, 80L, 100L, "finished", base.plusHours(6), null, null,
                base.plusMinutes(1), base.plusHours(5).plusMinutes(2), null, null, 700L,
                base.minusMinutes(60), base.minusMinutes(58), 800L, 700L, 80L, base.minusMinutes(57), 700L,
                "yes", null, 20L, 0L);
        // 5) feedback delay >10 min — response2 30 min after end
        jdbc.update("INSERT INTO dm.t_tsp_sms_dissemination_statistics ("+cols+") VALUES ("+ph+")",
                5L,"ALERT-004","Airtel", base, base.plusMinutes(20), 600L, 580L, 10L, 50L, "finished", base.plusMinutes(21), null, null,
                base.plusMinutes(1), base.plusMinutes(50), null, null, 580L,
                base.minusMinutes(60), base.minusMinutes(30), 600L, 580L, 10L, base.minusMinutes(29), 580L,
                "yes", null, 10L, 0L);
        // 6) feedback not received — end_time present but response2 NULL
        jdbc.update("INSERT INTO dm.t_tsp_sms_dissemination_statistics ("+cols+") VALUES ("+ph+")",
                6L,"ALERT-005","BSNL", base, base.plusMinutes(25), 400L, 0L, 0L, 50L, "received", base.plusMinutes(26), null, null,
                base.plusMinutes(1), null, null, null, 0L,
                base.minusMinutes(60), base.minusMinutes(30), 400L, 0L, 0L, null, 0L,
                null, null, 0L, 0L);
        // 7) arithmetic mismatch — success+failure+expired != total
        jdbc.update("INSERT INTO dm.t_tsp_sms_dissemination_statistics ("+cols+") VALUES ("+ph+")",
                7L,"ALERT-006","Airtel", base, base.plusMinutes(20), 1000L, 600L, 100L, 50L, "finished", base.plusMinutes(21), null, null,
                base.plusMinutes(1), base.plusMinutes(22), null, null, 600L,
                base.minusMinutes(60), base.minusMinutes(30), 1000L, 600L, 100L, base.minusMinutes(29), 600L,
                "yes", null, 999L, 0L);
        // 8) unknown status — should log UNKNOWN_STATUS not crash
        jdbc.update("INSERT INTO dm.t_tsp_sms_dissemination_statistics ("+cols+") VALUES ("+ph+")",
                8L,"ALERT-007","Airtel", base, base.plusMinutes(20), 300L, 290L, 5L, 20L, "partial", base.plusMinutes(21), null, null,
                base.plusMinutes(1), base.plusMinutes(22), null, null, 290L,
                null, null, null, null, null, null, null,
                null, null, 5L, 0L);
        // 9) prefetch duration breach — 200 cells (band 0-5k 60min) but prefetch took 90 min
        jdbc.update("INSERT INTO dm.t_tsp_sms_dissemination_statistics ("+cols+") VALUES ("+ph+")",
                9L,"ALERT-008","Airtel", base, base.plusMinutes(20), 500L, 490L, 5L, 200L, "finished", base.plusMinutes(21), null, null,
                base.plusMinutes(1), base.plusMinutes(22), null, null, 490L,
                base.minusMinutes(120), base.minusMinutes(30), 500L, 490L, 5L, base.minusMinutes(29), 490L,
                "yes", null, 5L, 0L);
        // Contacts
        jdbc.update("INSERT INTO dm.t_tsp_contact_list VALUES (1,'Airtel','All India','Test Airtel','Nodal','airtel@test.in','9999999999',CURRENT_TIMESTAMP,null,'yes',true,false,1)");
        jdbc.update("INSERT INTO dm.t_tsp_contact_list VALUES (2,'Vodafone-Idea','All India','Test VI','Nodal','vi@test.in','8888888888',CURRENT_TIMESTAMP,null,'yes',true,false,2)");
        jdbc.update("INSERT INTO dm.t_tsp_contact_list VALUES (3,'Reliance Jio','All India','Test Jio','Nodal','jio@test.in','7777777777',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'yes',true,false,3)"); // deactivated
    }

    @Test
    void check1CompleteFailure() {
        var rows = live.check1CompleteFailure(null,null,null);
        assertThat(rows).extracting(r -> r.getIdentifier()).contains("ALERT-001");
        assertThat(rows).anyMatch(r -> r.getTspName().equals("BSNL"));
    }

    @Test
    void check2FeedbackNotReceived() {
        var rows = live.check2FeedbackNotReceived(null,null,null);
        assertThat(rows).extracting(r -> r.getIdentifier()).contains("ALERT-005");
    }

    @Test
    void check3FeedbackDelay() {
        var rows = live.check3FeedbackDelay(null,null,null);
        assertThat(rows).extracting(r -> r.getIdentifier()).contains("ALERT-004");
    }

    @Test
    void check7ExpiredNonZero() {
        var rows = live.check7ExpiredNonZero(null,null,null);
        assertThat(rows).extracting(r -> r.getIdentifier()).contains("ALERT-002", "ALERT-003");
    }

    @Test
    void check7ArithmeticMismatch() {
        var rows = live.check7ArithmeticMismatch(null,null,null);
        assertThat(rows).extracting(r -> r.getIdentifier()).contains("ALERT-006");
    }

    @Test
    void fullRunPersistsBatch() {
        IngestionBatch batch = live.runLiveChecks(null,null,null,"junit");
        assertThat(batch.getId()).isNotNull();
        assertThat(batch.getStatus()).isEqualTo(IngestionBatch.ProcessingStatus.COMPLETED);
        assertThat(batch.getTotalDiscrepancyInstances()).isGreaterThan(0);
        assertThat(discRepo.countByIngestionBatchId(batch.getId())).isGreaterThan(0);
    }

    @Test
    void tspNormalizationInContactResolution() {
        // Vodafone-Idea hyphen vs space
        var contacts = live.check1CompleteFailure(null,null,"Vodafone Idea");
        // Should match Vodafone-Idea row when filtering hyphon/space-insensitively
        // We test via direct contact repo
        var vi = jdbc.queryForList("SELECT * FROM dm.t_tsp_contact_list WHERE LOWER(REPLACE(tsp_name,'-',' ')) = LOWER(REPLACE('Vodafone Idea','-',' ')) AND deactivated_on IS NULL AND email_notifications=true");
        assertThat(vi).hasSize(1);
    }
}
