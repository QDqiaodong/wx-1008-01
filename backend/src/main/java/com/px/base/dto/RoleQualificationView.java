package com.px.base.dto;

import com.px.base.entity.DutyQualificationSnapshot;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RoleQualificationView {
    private String role;
    private String roleLabel;
    private Long personnelId;
    private String personName;
    private Long selectedCertificateId;
    private String selectedCertificateNo;
    private boolean qualified;
    private boolean currentSnapshotUsed;
    private List<CertificateCheckView> certificateChecks;
    private List<String> missingReasons;
    private DutyQualificationSnapshot readySnapshot;
}
