package com.px.base.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 值守进入就绪状态时冻结的资格依据。
 * 之后人员改名、证书改范围或吊销，都只影响当前/未来安排，不回写该历史快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DutyQualificationSnapshot {
    private Long personnelId;
    private String personName;
    private Long certificateId;
    private String certificateNo;
    private List<String> applicableWindLevels;
    private List<String> applicableAnchorZones;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String certificateStatusAtReady;
    private LocalDate qualificationDateAtReady;
}
