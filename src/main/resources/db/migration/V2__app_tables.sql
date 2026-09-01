-- V2 — application tables (audit, discrepancy persist, report history)
-- Kept in public schema so Flyway can manage dm + public together.

-- Discrepancy records — populated by live SQL checks (one row per check hit)
CREATE TABLE IF NOT EXISTS public.discrepancy_records (
    id                              bigserial PRIMARY KEY,
    alert_id                        varchar(500) NOT NULL,
    tsp                             varchar(100) NOT NULL,
    discrepancy_type                varchar(100) NOT NULL,
    detection_time                  timestamp without time zone NOT NULL,
    state                           varchar(200),
    event                           varchar(200),
    alert_creation_time             varchar(500),
    area_description                varchar(1000),
    relevant_parameters             text,
    actual_value                    varchar(200),
    expected_value                  varchar(200),
    deviation                       varchar(200),
    status                          varchar(50) NOT NULL DEFAULT 'OPEN',
    reason                          text,
    note                            text,
    sl_no                           integer,
    district                        varchar(200),
    alert_authorizing_agency        varchar(200),
    severity                        varchar(50),
    priority                        varchar(50),
    cell_count                      bigint,
    subscriber_count                bigint,
    sms_count                       bigint,
    dissemination_duration_seconds  bigint,
    feedback_delay_seconds          bigint,
    ingestion_batch_id              bigint NOT NULL,
    -- transient live-source id for traceability
    source_row_id                   bigint
);
CREATE INDEX IF NOT EXISTS idx_disc_alert_id  ON public.discrepancy_records(alert_id);
CREATE INDEX IF NOT EXISTS idx_disc_tsp       ON public.discrepancy_records(tsp);
CREATE INDEX IF NOT EXISTS idx_disc_type      ON public.discrepancy_records(discrepancy_type);
CREATE INDEX IF NOT EXISTS idx_disc_time      ON public.discrepancy_records(detection_time);
CREATE INDEX IF NOT EXISTS idx_disc_batch     ON public.discrepancy_records(ingestion_batch_id);

-- Ingestion batches — one per Generate-Report run against live DB
CREATE TABLE IF NOT EXISTS public.ingestion_batches (
    id                                          bigserial PRIMARY KEY,
    ingestion_time                              timestamp without time zone NOT NULL,
    warning_report_filename                     varchar(500),
    trai_baseline_filename                      varchar(500),
    -- live-DB filters for this run
    date_from                                   timestamp without time zone,
    date_to                                     timestamp without time zone,
    tsp_filter                                  varchar(200),
    total_alerts_processed                      integer NOT NULL DEFAULT 0,
    total_tsp_rows_processed                    integer NOT NULL DEFAULT 0,
    total_alerts_with_discrepancies             integer NOT NULL DEFAULT 0,
    total_discrepancy_instances                 integer NOT NULL DEFAULT 0,
    count_complete_failure                      integer DEFAULT 0,
    count_zero_subscriber_with_cell_count       integer DEFAULT 0,
    count_zero_subscriber_without_cell_count    integer DEFAULT 0,
    count_statistics_pending                    integer DEFAULT 0,
    count_statistics_awaited                    integer DEFAULT 0,
    count_delta_pending                         integer DEFAULT 0,
    count_feedback_delay_exceeds                integer DEFAULT 0,
    count_prefetch_duration_breach              integer DEFAULT 0,
    count_total_duration_breach                 integer DEFAULT 0,
    count_inordinate_ratio                      integer DEFAULT 0,
    count_dissemination_completed_zero_prefetch integer DEFAULT 0,
    count_disseminated_after_expiry             integer DEFAULT 0,
    -- check 7 (new)
    count_expired_nonzero                       integer DEFAULT 0,
    count_arithmetic_mismatch                   integer DEFAULT 0,
    status                                      varchar(50) NOT NULL,
    error_message                               text,
    triggered_by                                varchar(200)
);

-- Generated reports — auditable history for the 2-click flow
CREATE TABLE IF NOT EXISTS public.generated_reports (
    id                  bigserial PRIMARY KEY,
    batch_id            bigint NOT NULL REFERENCES public.ingestion_batches(id),
    generated_at        timestamp without time zone NOT NULL,
    generated_by        varchar(200),
    date_from           timestamp without time zone,
    date_to             timestamp without time zone,
    tsp_filter          varchar(200),
    file_name           varchar(500),
    file_size_bytes     bigint,
    discrepancy_count   integer NOT NULL DEFAULT 0,
    checksum_sha256     varchar(100),
    -- email audit
    email_sent          boolean NOT NULL DEFAULT false,
    email_sent_at       timestamp without time zone,
    email_recipients    text,
    email_subject       varchar(500),
    email_status        varchar(50),
    email_error         text
);
CREATE INDEX IF NOT EXISTS idx_genrep_batch ON public.generated_reports(batch_id);
