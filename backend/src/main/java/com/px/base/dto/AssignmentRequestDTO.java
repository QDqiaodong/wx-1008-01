package com.px.base.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssignmentRequestDTO {
    private Long routeId;
    private LocalDateTime scheduledStartAt;
    private LocalDateTime scheduledEndAt;
    private Long operatorId;
    private Long operatorCertId;
    private Long reviewerId;
    private Long reviewerCertId;
}
