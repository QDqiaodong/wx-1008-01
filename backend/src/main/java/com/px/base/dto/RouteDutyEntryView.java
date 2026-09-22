package com.px.base.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class RouteDutyEntryView {
    private Long routeId;
    private String routeCode;
    private String routeName;
    private String windLevel;
    private LocalDate flightDate;
    private Long assignmentId;
    private String assignmentStatus;
    private String assignmentStatusLabel;
    private String operatorName;
    private String reviewerName;
    private boolean operatorArrived;
    private List<String> requiredAnchorZones;
    private boolean qualified;
    private boolean ready;
    private List<String> issues;
}
