package com.px.base.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class CertificateCheckView {
    private Long certificateId;
    private String certificateNo;
    private String status;
    private List<String> applicableWindLevels;
    private List<String> applicableAnchorZones;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private boolean selected;
    private boolean eligible;
    private List<String> missingWindLevels;
    private List<String> missingAnchorZones;
    private List<String> failureCodes;
    private List<String> reasons;
}
