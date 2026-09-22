package com.px.base.entity;

import com.px.base.converter.StringListConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ground_certificate")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroundCertificate {
    public static final String PENDING = "PENDING";
    public static final String VALID = "VALID";
    public static final String EXPIRED = "EXPIRED";
    public static final String REVOKED = "REVOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cert_no", unique = true, nullable = false, length = 80)
    private String certNo;

    @Column(name = "personnel_id", nullable = false)
    private Long personnelId;

    /** 颁证时的姓名快照用于列表展示；值守就绪时另有不可变快照。 */
    @Column(name = "person_name", nullable = false, length = 100)
    private String personName;

    @Column(name = "applicable_wind_levels", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = StringListConverter.class)
    private List<String> applicableWindLevels;

    @Column(name = "applicable_anchor_zones", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = StringListConverter.class)
    private List<String> applicableAnchorZones;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    /** PENDING/VALID/EXPIRED 可按日期实时判定；REVOKED 是终态，由安全主管落库。 */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = PENDING;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by_id")
    private Long revokedById;

    @Column(name = "revoked_by_name", length = 100)
    private String revokedByName;

    @Column(name = "revoke_reason", length = 500)
    private String revokeReason;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
