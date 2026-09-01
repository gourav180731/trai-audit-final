-- V1 — dm schema: live tables exactly as provided in the brief
-- This file recreates the production schema for local dev/test without needing prod access.

CREATE SCHEMA IF NOT EXISTS dm;

-- ------------------------------------------------------------------
-- Table 1: dm.t_tsp_sms_dissemination_statistics
-- ordinal position 27 is intentionally absent (gap) — do not add a column there
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dm.t_tsp_sms_dissemination_statistics (
    id                                      bigint PRIMARY KEY,
    identifier                              varchar(100),
    tsp_name                                varchar(50),
    start_time                              timestamp without time zone,
    end_time                                timestamp without time zone,
    total_subscribers                       bigint,
    total_delivery_success                  bigint,
    total_delivery_failure                  bigint,
    total_cell_count                        bigint,
    status                                  varchar(100),
    entry_time                              timestamp without time zone,
    remarks_by_tsp                          varchar,
    tsp_remarks_received_timestamp          timestamp without time zone,
    response1_received_timestamp            timestamp without time zone,
    response2_received_timestamp            timestamp without time zone,
    remarks_by_capplatform                  varchar,
    internal_testing_remarks                varchar,
    sms_count_success                       bigint,
    prefetch_start_time                     timestamp without time zone,
    prefetch_end_time                       timestamp without time zone,
    prefetch_total_subscribers               bigint,
    prefetch_total_delivery_success          bigint,
    prefetch_total_delivery_failure          bigint,
    prefetch_response2_received_timestamp    timestamp without time zone,
    prefetch_sms_count_success               bigint,
    delta_received                          varchar,
    charges                                 varchar,
    total_expired                           bigint,
    sms_count_expired                       bigint
);

CREATE INDEX IF NOT EXISTS idx_tsp_stats_identifier ON dm.t_tsp_sms_dissemination_statistics(identifier);
CREATE INDEX IF NOT EXISTS idx_tsp_stats_tsp_name   ON dm.t_tsp_sms_dissemination_statistics(tsp_name);
CREATE INDEX IF NOT EXISTS idx_tsp_stats_start_time ON dm.t_tsp_sms_dissemination_statistics(start_time);
CREATE INDEX IF NOT EXISTS idx_tsp_stats_status     ON dm.t_tsp_sms_dissemination_statistics(status);

-- ------------------------------------------------------------------
-- Table 2: dm.t_tsp_contact_list
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dm.t_tsp_contact_list (
    contact_id            bigint PRIMARY KEY,
    tsp_name              varchar(40),
    boundary_restriction  varchar(100),
    name                  varchar,
    designation           varchar,
    email_id              varchar,
    contact_number        varchar,
    created_on            timestamp with time zone,
    deactivated_on        timestamp with time zone,
    in_notification_list  varchar(30),
    email_notifications   boolean,
    sms_notifications     boolean,
    element_id            integer
);

CREATE INDEX IF NOT EXISTS idx_contact_tsp_name ON dm.t_tsp_contact_list(tsp_name);
CREATE INDEX IF NOT EXISTS idx_contact_email_notif ON dm.t_tsp_contact_list(email_notifications);
