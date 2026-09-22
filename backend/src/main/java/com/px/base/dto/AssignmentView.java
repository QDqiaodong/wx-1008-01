package com.px.base.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AssignmentView {
    private Long id;
    private Long routeId;
    private String routeCode;
    private String routeName;
    private String routeWindLevel;
    private LocalDate flightDate;
    private LocalDateTime scheduledStartAt;
    private LocalDateTime scheduledEndAt;
    private String status;
    private String statusLabel;
    private boolean operatorArrived;
    private LocalDateTime arrivedAt;
    private LocalDateTime readyAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private RoleQualificationView operator;
    private RoleQualificationView reviewer;
    private List<String> requiredAnchorZones;
    private List<RouteAnchorZoneView> routeAnchors;
    private List<String> blockingIssues;
    private boolean readyAllowed;
    private boolean historical;
    private String midnightPolicy;
    private String midnightPolicyRejectedAlternative;
}
