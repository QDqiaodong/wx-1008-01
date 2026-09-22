package com.px.base.dto;

import com.px.base.entity.GroundCertificate;
import com.px.base.entity.GroundPersonnel;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PersonnelCertificateView {
    private GroundPersonnel personnel;
    private String roleLabel;
    private boolean safetyManager;
    private List<GroundCertificate> certificates;
}
