package com.px.base.rule;

import com.px.base.dto.CertificateCheckView;
import com.px.base.dto.RoleQualificationView;
import com.px.base.entity.DutyQualificationSnapshot;
import com.px.base.entity.GroundCertificate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 地勤资质判定引擎。人员列表、值守详情和航线入口必须共用这里的规则，
 * 避免不同页面对同一航线、同一飞行日给出不同结论。
 */
@Component
public class DutyQualificationEvaluator {

    public String currentCertificateStatus(GroundCertificate cert, LocalDate qualificationDate) {
        if (GroundCertificate.REVOKED.equals(cert.getStatus())) {
            return GroundCertificate.REVOKED;
        }
        if (qualificationDate.isBefore(cert.getEffectiveDate())) {
            return GroundCertificate.PENDING;
        }
        if (qualificationDate.isAfter(cert.getExpiryDate())) {
            return GroundCertificate.EXPIRED;
        }
        return GroundCertificate.VALID;
    }

    public RoleQualificationView evaluate(String role, String roleLabel, Long personnelId, String personName,
                                          Long selectedCertId, List<GroundCertificate> certs,
                                          String requiredWindLevel, List<String> requiredZones,
                                          LocalDate qualificationDate) {
        List<CertificateCheckView> checks = new ArrayList<>();
        for (GroundCertificate cert : certs) {
            checks.add(checkCertificate(cert, requiredWindLevel, requiredZones, qualificationDate,
                    cert.getId().equals(selectedCertId)));
        }

        Optional<CertificateCheckView> selected = checks.stream()
                .filter(c -> c.isSelected() && c.isEligible())
                .findFirst();
        Optional<CertificateCheckView> fallback = selectedCertId == null
                ? checks.stream().filter(CertificateCheckView::isEligible)
                    .max(Comparator.comparing(CertificateCheckView::getExpiryDate)
                            .thenComparing(CertificateCheckView::getCertificateId))
                : Optional.empty();

        CertificateCheckView chosen = selected.orElse(fallback.orElse(null));
        List<String> roleIssues = new ArrayList<>();
        if (personnelId == null) {
            roleIssues.add(roleLabel + "尚未安排人员");
        } else if (chosen == null) {
            roleIssues.add(roleLabel + "缺少可覆盖本次开航的有效资质证");
            checks.stream()
                    .filter(CertificateCheckView::isSelected)
                    .findFirst()
                    .ifPresent(view -> roleIssues.addAll(prefixReasons(roleLabel, view.getReasons())));
            if (selectedCertId == null) {
                roleIssues.add(roleLabel + "未选定资质证");
            }
        }

        return RoleQualificationView.builder()
                .role(role)
                .roleLabel(roleLabel)
                .personnelId(personnelId)
                .personName(personName)
                .selectedCertificateId(chosen != null ? chosen.getCertificateId() : selectedCertId)
                .selectedCertificateNo(chosen != null ? chosen.getCertificateNo() : null)
                .qualified(chosen != null)
                .currentSnapshotUsed(false)
                .certificateChecks(checks)
                .missingReasons(roleIssues)
                .build();
    }

    public CertificateCheckView checkCertificate(GroundCertificate cert, String requiredWindLevel,
                                                 List<String> requiredZones, LocalDate date,
                                                 boolean selected) {
        String status = currentCertificateStatus(cert, date);
        List<String> missingWind = GroundCertificate.VALID.equals(status)
                && !cert.getApplicableWindLevels().contains(requiredWindLevel)
                ? List.of(requiredWindLevel) : List.of();
        List<String> missingZones = GroundCertificate.VALID.equals(status)
                ? missing(requiredZones, cert.getApplicableAnchorZones()) : List.of();

        List<String> codes = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        if (GroundCertificate.REVOKED.equals(status)) {
            codes.add("CERT_REVOKED");
            reasons.add("证书已吊销，改飞行日也不能重新使用该证书");
        } else if (GroundCertificate.EXPIRED.equals(status)) {
            codes.add("CERT_EXPIRED");
            reasons.add("按计划起飞日" + date + "判断，证书已于" + cert.getExpiryDate() + "到期");
        } else if (GroundCertificate.PENDING.equals(status)) {
            codes.add("CERT_NOT_EFFECTIVE");
            reasons.add("按计划起飞日" + date + "判断，证书要到" + cert.getEffectiveDate() + "才生效");
        }
        if (!missingWind.isEmpty()) {
            codes.add("WIND_LEVEL_NOT_COVERED");
            reasons.add("不适用航线当前风级：" + requiredWindLevel);
        }
        if (!missingZones.isEmpty()) {
            codes.add("ANCHOR_ZONE_NOT_COVERED");
            reasons.add("未覆盖航线在用锚点区域：" + String.join("、", missingZones));
        }

        return CertificateCheckView.builder()
                .certificateId(cert.getId())
                .certificateNo(cert.getCertNo())
                .status(status)
                .applicableWindLevels(cert.getApplicableWindLevels())
                .applicableAnchorZones(cert.getApplicableAnchorZones())
                .effectiveDate(cert.getEffectiveDate())
                .expiryDate(cert.getExpiryDate())
                .selected(selected)
                .eligible(codes.isEmpty())
                .missingWindLevels(missingWind)
                .missingAnchorZones(missingZones)
                .failureCodes(codes)
                .reasons(reasons)
                .build();
    }

    public RoleQualificationView fromSnapshot(String role, String roleLabel, DutyQualificationSnapshot snapshot) {
        if (snapshot == null) {
            return RoleQualificationView.builder()
                    .role(role).roleLabel(roleLabel).qualified(false)
                    .currentSnapshotUsed(true)
                    .certificateChecks(List.of())
                    .missingReasons(List.of(roleLabel + "缺少就绪时资格快照"))
                    .build();
        }
        return RoleQualificationView.builder()
                .role(role)
                .roleLabel(roleLabel)
                .personnelId(snapshot.getPersonnelId())
                .personName(snapshot.getPersonName())
                .selectedCertificateId(snapshot.getCertificateId())
                .selectedCertificateNo(snapshot.getCertificateNo())
                .qualified(true)
                .currentSnapshotUsed(true)
                .certificateChecks(List.of())
                .missingReasons(List.of())
                .readySnapshot(snapshot)
                .build();
    }

    public DutyQualificationSnapshot createSnapshot(String role,
                                                    RoleQualificationView qualification, LocalDate qualificationDate) {
        CertificateCheckView cert = qualification.getCertificateChecks().stream()
                .filter(c -> c.getCertificateId().equals(qualification.getSelectedCertificateId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(role + "资格结论未找到可冻结证书"));
        return DutyQualificationSnapshot.builder()
                .personnelId(qualification.getPersonnelId())
                .personName(qualification.getPersonName())
                .certificateId(cert.getCertificateId())
                .certificateNo(cert.getCertificateNo())
                .applicableWindLevels(List.copyOf(cert.getApplicableWindLevels()))
                .applicableAnchorZones(List.copyOf(cert.getApplicableAnchorZones()))
                .effectiveDate(cert.getEffectiveDate())
                .expiryDate(cert.getExpiryDate())
                .certificateStatusAtReady(cert.getStatus())
                .qualificationDateAtReady(qualificationDate)
                .build();
    }

    private List<String> missing(List<String> required, List<String> covered) {
        if (required == null || required.isEmpty()) return List.of();
        Set<String> coverSet = new HashSet<>(covered == null ? List.of() : covered);
        return required.stream().filter(item -> !coverSet.contains(item)).distinct().toList();
    }

    private List<String> prefixReasons(String roleLabel, List<String> reasons) {
        return reasons.stream().map(reason -> roleLabel + "：" + reason).toList();
    }
}
