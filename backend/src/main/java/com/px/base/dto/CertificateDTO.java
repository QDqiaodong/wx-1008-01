package com.px.base.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CertificateDTO {
    private Long personnelId;
    private String certNo;
    private List<String> applicableWindLevels;
    private List<String> applicableAnchorZones;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
}
