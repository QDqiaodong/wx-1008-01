package com.px.base.entity;

import com.px.base.converter.DutyQualificationSnapshotConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "duty_assignment",
        uniqueConstraints = @UniqueConstraint(name = "uk_route_flight_date",
                columnNames = {"route_id", "flight_date"}),
        indexes = {
                @Index(name = "idx_duty_operator", columnList = "operator_id"),
                @Index(name = "idx_duty_reviewer", columnList = "reviewer_id"),
                @Index(name = "idx_duty_date", columnList = "flight_date")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DutyAssignment {
    public static final String DRAFT = "DRAFT";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String READY = "READY";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "route_code", nullable = false, length = 50)
    private String routeCode;

    @Column(name = "route_name", nullable = false, length = 100)
    private String routeName;

    @Column(name = "flight_date", nullable = false)
    private LocalDate flightDate;

    /** 口径固定为“按计划起飞时刻判断”；跨午夜区间不因预计结束跨越证书到期日而失败。 */
    @Column(name = "scheduled_start_at", nullable = false)
    private LocalDateTime scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private LocalDateTime scheduledEndAt;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "operator_name", nullable = false, length = 100)
    private String operatorName;

    @Column(name = "operator_cert_id")
    private Long operatorCertId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "reviewer_name", nullable = false, length = 100)
    private String reviewerName;

    @Column(name = "reviewer_cert_id")
    private Long reviewerCertId;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = DRAFT;

    @Column(name = "operator_arrived", nullable = false, columnDefinition = "TINYINT(1)")
    @Builder.Default
    private Boolean operatorArrived = false;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "ready_confirmed_by_id")
    private Long readyConfirmedById;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by_id")
    private Long cancelledById;

    @Column(name = "cancelled_by_name", length = 100)
    private String cancelledByName;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "operator_snapshot", columnDefinition = "TEXT")
    @Convert(converter = DutyQualificationSnapshotConverter.class)
    private DutyQualificationSnapshot operatorSnapshot;

    /** 历史行冻结姓名/证书范围时的状态快照；避免后续人员改名影响 cancelled/finished 展示。 */
    @Column(name = "operator_name_snapshot", length = 100)
    private String operatorNameSnapshot;

    @Column(name = "reviewer_snapshot", columnDefinition = "TEXT")
    @Convert(converter = DutyQualificationSnapshotConverter.class)
    private DutyQualificationSnapshot reviewerSnapshot;

    @Column(name = "reviewer_name_snapshot", length = 100)
    private String reviewerNameSnapshot;

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
