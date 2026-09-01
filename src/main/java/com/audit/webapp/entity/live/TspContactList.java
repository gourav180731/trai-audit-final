package com.audit.webapp.entity.live;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "t_tsp_contact_list", schema = "dm")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TspContactList {

    @Id
    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "tsp_name")
    private String tspName;

    @Column(name = "boundary_restriction")
    private String boundaryRestriction;

    @Column(name = "name")
    private String name;

    @Column(name = "designation")
    private String designation;

    @Column(name = "email_id")
    private String emailId;

    @Column(name = "contact_number")
    private String contactNumber;

    @Column(name = "created_on")
    private OffsetDateTime createdOn;

    @Column(name = "deactivated_on")
    private OffsetDateTime deactivatedOn;

    @Column(name = "in_notification_list")
    private String inNotificationList;

    @Column(name = "email_notifications")
    private Boolean emailNotifications;

    @Column(name = "sms_notifications")
    private Boolean smsNotifications;

    @Column(name = "element_id")
    private Integer elementId;
}
