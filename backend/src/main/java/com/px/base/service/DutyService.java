package com.px.base.service;

import com.px.base.dto.AssignmentRequestDTO;
import com.px.base.dto.AssignmentView;
import com.px.base.dto.CertificateDTO;
import com.px.base.dto.CertificateCheckView;
import com.px.base.dto.DutyPolicyView;
import com.px.base.dto.PersonnelCertificateView;
import com.px.base.dto.PersonnelDTO;
import com.px.base.dto.RoleQualificationView;
import com.px.base.dto.RouteAnchorZoneView;
import com.px.base.dto.RouteDutyEntryView;
import com.px.base.entity.Anchor;
import com.px.base.entity.DutyAssignment;
import com.px.base.entity.DutyQualificationSnapshot;
import com.px.base.entity.FlightRoute;
import com.px.base.entity.GroundCertificate;
import com.px.base.entity.GroundPersonnel;
import com.px.base.entity.RouteAnchor;
import com.px.base.repository.AnchorRepository;
import com.px.base.repository.DutyAssignmentRepository;
import com.px.base.repository.FlightRouteRepository;
import com.px.base.repository.GroundCertificateRepository;
import com.px.base.repository.GroundPersonnelRepository;
import com.px.base.repository.RouteAnchorRepository;
import com.px.base.rule.DutyQualificationEvaluator;
import com.px.base.rule.DutyRules;
import com.px.base.security.ActorContext;
import com.px.base.security.ForbiddenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DutyService {
    private final GroundPersonnelRepository personnelRepository;
    private final GroundCertificateRepository certificateRepository;
    private final DutyAssignmentRepository assignmentRepository;
    private final FlightRouteRepository routeRepository;
    private final RouteAnchorRepository routeAnchorRepository;
    private final AnchorRepository anchorRepository;
    private final DutyQualificationEvaluator evaluator;

    public DutyPolicyView policy() {
        return DutyPolicyView.builder()
                .selectedRuleCode(DutyRules.POLICY_CODE)
                .selectedRule(DutyRules.POLICY_NAME)
                .explanation(DutyRules.POLICY_EXPLANATION)
                .rejectedAlternative(DutyRules.REJECTED_ALTERNATIVE)
                .rejectedAlternativeReason(DutyRules.REJECTED_ALTERNATIVE_REASON)
                .statusChain(DutyRules.STATUS_CHAIN)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PersonnelCertificateView> listPersonnel() {
        LocalDate today = LocalDate.now();
        return personnelRepository.findAll().stream()
                .sorted(Comparator.comparing(GroundPersonnel::getId))
                .map(p -> PersonnelCertificateView.builder()
                        .personnel(p)
                        .roleLabel(roleLabel(p.getRoleCode()))
                        .safetyManager(ActorContext.ROLE_SAFETY_MANAGER.equals(p.getRoleCode()))
                        .certificates(certificateRepository.findByPersonnelIdOrderByExpiryDateDescIdDesc(p.getId()).stream()
                                .map(c -> certificateView(c, today))
                                .toList())
                        .build())
                .toList();
    }

    private GroundCertificate certificateView(GroundCertificate cert, LocalDate date) {
        GroundCertificate view = GroundCertificate.builder()
                .id(cert.getId())
                .certNo(cert.getCertNo())
                .personnelId(cert.getPersonnelId())
                .personName(cert.getPersonName())
                .applicableWindLevels(cert.getApplicableWindLevels())
                .applicableAnchorZones(cert.getApplicableAnchorZones())
                .effectiveDate(cert.getEffectiveDate())
                .expiryDate(cert.getExpiryDate())
                .status(evaluator.currentCertificateStatus(cert, date))
                .revokedAt(cert.getRevokedAt())
                .revokedById(cert.getRevokedById())
                .revokedByName(cert.getRevokedByName())
                .revokeReason(cert.getRevokeReason())
                .createTime(cert.getCreateTime())
                .updateTime(cert.getUpdateTime())
                .build();
        return view;
    }

    @Transactional
    public GroundPersonnel createPersonnel(PersonnelDTO dto) {
        validatePersonnel(dto);
        if (personnelRepository.existsByEmployeeNo(dto.getEmployeeNo().trim())) {
            throw new IllegalArgumentException("工号已存在: " + dto.getEmployeeNo());
        }
        return personnelRepository.save(GroundPersonnel.builder()
                .employeeNo(dto.getEmployeeNo().trim())
                .personName(dto.getPersonName().trim())
                .roleCode(normalizeRole(dto.getRoleCode()))
                .active(dto.getActive() == null || dto.getActive())
                .build());
    }

    @Transactional
    public GroundPersonnel updatePersonnel(Long id, PersonnelDTO dto) {
        validatePersonnel(dto);
        GroundPersonnel person = loadPersonnel(id);
        personnelRepository.findByEmployeeNo(dto.getEmployeeNo().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new IllegalArgumentException("工号已存在: " + dto.getEmployeeNo());
            }
        });
        person.setEmployeeNo(dto.getEmployeeNo().trim());
        // 人员档案改名不回写任何已就绪/已结束值守快照，只影响新安排和当前证书列表姓名。
        person.setPersonName(dto.getPersonName().trim());
        person.setRoleCode(normalizeRole(dto.getRoleCode()));
        person.setActive(dto.getActive() == null || dto.getActive());
        return personnelRepository.save(person);
    }

    @Transactional
    public GroundCertificate createCertificate(CertificateDTO dto) {
        GroundPersonnel person = loadPersonnel(dto.getPersonnelId());
        validateCertificate(dto);
        if (certificateRepository.existsByCertNo(dto.getCertNo().trim())) {
            throw new IllegalArgumentException("资质证编号已存在: " + dto.getCertNo());
        }
        GroundCertificate cert = GroundCertificate.builder()
                .certNo(dto.getCertNo().trim())
                .personnelId(person.getId())
                .personName(person.getPersonName())
                .applicableWindLevels(normalizeList(dto.getApplicableWindLevels()))
                .applicableAnchorZones(normalizeList(dto.getApplicableAnchorZones()))
                .effectiveDate(dto.getEffectiveDate())
                .expiryDate(dto.getExpiryDate())
                .status(GroundCertificate.PENDING)
                .build();
        cert.setStatus(evaluator.currentCertificateStatus(cert, LocalDate.now()));
        return certificateRepository.save(cert);
    }

    @Transactional
    public GroundCertificate updateCertificate(Long id, CertificateDTO dto) {
        validateCertificate(dto);
        GroundCertificate cert = certificateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资质证不存在: " + id));
        if (GroundCertificate.REVOKED.equals(cert.getStatus())) {
            throw new IllegalStateException("已吊销证书不能编辑；如需恢复资格，应核发新证，而不是修改旧证或改飞行日");
        }
        validateCertificate(dto);
        if (dto.getPersonnelId() != null && !dto.getPersonnelId().equals(cert.getPersonnelId())) {
            GroundPersonnel person = loadPersonnel(dto.getPersonnelId());
            cert.setPersonnelId(person.getId());
            cert.setPersonName(person.getPersonName());
        }
        certificateRepository.findByCertNo(dto.getCertNo().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new IllegalArgumentException("资质证编号已存在: " + dto.getCertNo());
            }
        });
        cert.setCertNo(dto.getCertNo().trim());
        cert.setApplicableWindLevels(normalizeList(dto.getApplicableWindLevels()));
        cert.setApplicableAnchorZones(normalizeList(dto.getApplicableAnchorZones()));
        cert.setEffectiveDate(dto.getEffectiveDate());
        cert.setExpiryDate(dto.getExpiryDate());
        cert.setStatus(evaluator.currentCertificateStatus(cert, LocalDate.now()));
        GroundCertificate saved = certificateRepository.save(cert);
        rejudgeByCertificate(saved);
        return saved;
    }

    @Transactional
    public GroundCertificate revokeCertificate(Long certId, String reason, ActorContext actor) {
        GroundCertificate cert = certificateRepository.findById(certId)
                .orElseThrow(() -> new IllegalArgumentException("资质证不存在: " + certId));
        if (GroundCertificate.REVOKED.equals(cert.getStatus())) {
            return cert;
        }
        cert.setStatus(GroundCertificate.REVOKED);
        cert.setRevokedAt(LocalDateTime.now());
        cert.setRevokedById(actor.id());
        cert.setRevokedByName(actor.name());
        cert.setRevokeReason(reason == null || reason.isBlank() ? "安全主管吊销" : reason.trim());
        GroundCertificate saved = certificateRepository.save(cert);
        rejudgeByCertificate(saved);
        log.warn("安全主管{}吊销证书{}，未就绪未来安排已重新判定", actor.name(), saved.getCertNo());
        return saved;
    }

    @Transactional
    public List<AssignmentView> listAssignments(LocalDate date) {
        LocalDate queryDate = date == null ? LocalDate.now() : date;
        return assignmentRepository.findByFlightDateOrderByScheduledStartAtAscRouteCodeAsc(queryDate).stream()
                .map(this::refreshAndView)
                .toList();
    }

    @Transactional
    public AssignmentView getAssignment(Long id) {
        return refreshAndView(loadAssignment(id));
    }

    @Transactional
    public List<RouteDutyEntryView> routeEntries(LocalDate date) {
        LocalDate queryDate = date == null ? LocalDate.now() : date;
        List<DutyAssignment> assignments = assignmentRepository
                .findByFlightDateOrderByScheduledStartAtAscRouteCodeAsc(queryDate);
        assignments.forEach(a -> rejudgeIfNeeded(a, null));
        return routeRepository.findAll().stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .sorted(Comparator.comparing(FlightRoute::getRouteCode))
                .map(route -> toRouteEntry(route, queryDate, assignments))
                .toList();
    }

    @Transactional
    public AssignmentView createOrUpdateAssignment(AssignmentRequestDTO dto) {
        validateAssignmentRequest(dto);
        FlightRoute route = loadEnabledRoute(dto.getRouteId());
        LocalDate flightDate = dto.getScheduledStartAt().toLocalDate();
        if (dto.getScheduledEndAt().toLocalDate().isAfter(flightDate)) {
            // 仅记录跨午夜事实；口径仍取起飞日，不扩展到结束日。
            log.debug("航线{}值守跨午夜，按起飞日{}判定证书", dto.getRouteId(), flightDate);
        }
        DutyAssignment assignment = assignmentRepository
                .findByRouteIdAndFlightDate(route.getId(), flightDate)
                .orElseGet(() -> DutyAssignment.builder()
                        .routeId(route.getId())
                        .flightDate(flightDate)
                        .build());
        if (DutyAssignment.READY.equals(assignment.getStatus())) {
            throw new IllegalStateException("值守已就绪，不能直接改派；如需调整请由安全主管先取消");
        }
        if (DutyAssignment.CANCELLED.equals(assignment.getStatus())) {
            throw new IllegalStateException("已取消安排不能修改，请重新创建安排");
        }
        if (dto.getOperatorId().equals(dto.getReviewerId())) {
            throw new IllegalArgumentException("操作员与复核员必须是不同人员，系统拒绝同一人承担两个职责");
        }
        GroundPersonnel operator = loadActivePersonnel(dto.getOperatorId());
        GroundPersonnel reviewer = loadActivePersonnel(dto.getReviewerId());
        validateSelectedCertificate(dto.getOperatorCertId(), operator);
        validateSelectedCertificate(dto.getReviewerCertId(), reviewer);

        assignment.setRouteCode(route.getRouteCode());
        assignment.setRouteName(route.getRouteName());
        assignment.setScheduledStartAt(dto.getScheduledStartAt());
        assignment.setScheduledEndAt(dto.getScheduledEndAt());
        assignment.setOperatorId(operator.getId());
        assignment.setOperatorName(operator.getPersonName());
        assignment.setOperatorCertId(dto.getOperatorCertId());
        assignment.setReviewerId(reviewer.getId());
        assignment.setReviewerName(reviewer.getPersonName());
        assignment.setReviewerCertId(dto.getReviewerCertId());

        AssignmentView view = buildView(assignment, route, getBoundZoneViews(route.getId()));
        boolean qualified = view.getOperator().isQualified() && view.getReviewer().isQualified();
        if (Boolean.TRUE.equals(assignment.getOperatorArrived()) && !qualified) {
            resetArrival(assignment);
        }
        boolean arrivalWasConfirmed = Boolean.TRUE.equals(assignment.getOperatorArrived());
        if (arrivalWasConfirmed && qualified) {
            assignment.setStatus(DutyAssignment.PENDING_REVIEW);
        }
        assignment.setOperatorCertId(view.getOperator().getSelectedCertificateId());
        assignment.setReviewerCertId(view.getReviewer().getSelectedCertificateId());
        DutyAssignment saved = assignmentRepository.save(assignment);
        return toAssignmentView(saved);
    }

    @Transactional
    public AssignmentView confirmArrival(Long assignmentId, ActorContext actor) {
        DutyAssignment assignment = loadAssignment(assignmentId);
        if (DutyAssignment.CANCELLED.equals(assignment.getStatus())) {
            throw new IllegalStateException("已取消值守不能确认到位");
        }
        if (assignment.getScheduledStartAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("计划起飞时刻已过，不能补做现场到位确认");
        }
        if (!actor.id().equals(assignment.getOperatorId())) {
            throw new ForbiddenException("普通值班员只能确认自己的现场到位，不能替他人签到");
        }
        FlightRoute currentRoute = routeRepository.findById(assignment.getRouteId()).orElseThrow();
        AssignmentView view = buildView(assignment, currentRoute, getBoundZoneViews(currentRoute.getId()));
        if (!view.getOperator().isQualified() || !view.getReviewer().isQualified()
                || view.getRequiredAnchorZones().isEmpty()) {
            throw new IllegalStateException(String.join("；", view.getBlockingIssues()));
        }
        assignment.setOperatorArrived(true);
        assignment.setArrivedAt(LocalDateTime.now());
        assignment.setStatus(DutyAssignment.PENDING_REVIEW);
        return toAssignmentView(assignmentRepository.save(assignment));
    }

    @Transactional
    public AssignmentView confirmReady(Long assignmentId, ActorContext actor) {
        DutyAssignment assignment = loadAssignment(assignmentId);
        if (DutyAssignment.CANCELLED.equals(assignment.getStatus())) {
            throw new IllegalStateException("已取消值守不能确认就绪");
        }
        if (DutyAssignment.READY.equals(assignment.getStatus())) {
            return toAssignmentView(assignment);
        }
        if (!Boolean.TRUE.equals(assignment.getOperatorArrived())) {
            throw new IllegalStateException("操作员尚未确认现场到位，复核员不能确认就绪");
        }
        if (assignment.getScheduledStartAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("计划起飞时刻已过，不能补确认就绪；未就绪记录保留为待处理历史");
        }
        if (!actor.id().equals(assignment.getReviewerId())) {
            throw new ForbiddenException("操作员不能复核自己的工作；只有本安排的复核员本人可确认就绪");
        }
        FlightRoute currentRoute = routeRepository.findById(assignment.getRouteId()).orElseThrow();
        AssignmentView view = buildView(assignment, currentRoute, getBoundZoneViews(currentRoute.getId()));
        if (!view.getOperator().isQualified() || !view.getReviewer().isQualified()
                || view.getRequiredAnchorZones().isEmpty()) {
            assignment.setStatus(DutyAssignment.DRAFT);
            resetArrival(assignment);
            assignmentRepository.save(assignment);
            throw new IllegalStateException("当前资格已不满足，值守退回草拟：" + String.join("；", view.getBlockingIssues()));
        }
        LocalDate qualificationDate = assignment.getScheduledStartAt().toLocalDate();
        assignment.setOperatorSnapshot(createSnapshot(view.getOperator(), qualificationDate));
        assignment.setReviewerSnapshot(createSnapshot(view.getReviewer(), qualificationDate));
        assignment.setOperatorNameSnapshot(assignment.getOperatorName());
        assignment.setReviewerNameSnapshot(assignment.getReviewerName());
        assignment.setStatus(DutyAssignment.READY);
        assignment.setReadyAt(LocalDateTime.now());
        assignment.setReadyConfirmedById(actor.id());
        log.info("复核员{}确认值守{}就绪，资格快照已冻结", actor.name(), assignment.getId());
        return toAssignmentView(assignmentRepository.save(assignment));
    }

    @Transactional
    public AssignmentView cancelReady(Long assignmentId, String reason, ActorContext actor) {
        DutyAssignment assignment = loadAssignment(assignmentId);
        if (!DutyAssignment.READY.equals(assignment.getStatus())) {
            throw new IllegalStateException("仅已就绪值守需要由安全主管取消；草拟/待复核可直接编辑重排");
        }
        assignment.setStatus(DutyAssignment.CANCELLED);
        assignment.setCancelledAt(LocalDateTime.now());
        assignment.setCancelledById(actor.id());
        assignment.setCancelledByName(actor.name());
        assignment.setCancelReason(reason == null || reason.isBlank() ? "安全主管取消" : reason.trim());
        log.warn("安全主管{}取消已就绪值守{}", actor.name(), assignment.getId());
        return toAssignmentView(assignmentRepository.save(assignment));
    }

    @Scheduled(cron = "${px.duty.expiry-recheck-cron:0 */10 * * * *}")
    @Transactional
    public void recheckExpiredCertificates() {
        refreshCertificateLifecycleStatuses();
        LocalDate today = LocalDate.now();
        List<DutyAssignment> futures = assignmentRepository
                .findByFlightDateGreaterThanEqualOrderByFlightDateAscScheduledStartAtAsc(today);
        int changed = 0;
        for (DutyAssignment assignment : futures) {
            if (rejudgeIfNeeded(assignment, null)) changed++;
        }
        if (changed > 0) log.info("证书到期定时复判：{}条未来未就绪值守退回草拟", changed);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onReferenceChanged(DutyReferenceChangedEvent event) {
        switch (event.referenceType()) {
            case DutyReferenceChangedEvent.CERTIFICATE -> rejudgeByCertificateId(event.referenceId());
            case DutyReferenceChangedEvent.ROUTE, DutyReferenceChangedEvent.ROUTE_BINDING ->
                    rejudgeByRouteId(event.referenceId());
            case DutyReferenceChangedEvent.ANCHOR -> rejudgeAllFuture();
            default -> log.debug("未知值守引用变化事件: {}", event.referenceType());
        }
    }

    private void refreshCertificateLifecycleStatuses() {
        LocalDate today = LocalDate.now();
        certificateRepository.findAll().stream()
                .filter(c -> !GroundCertificate.REVOKED.equals(c.getStatus()))
                .filter(c -> !c.getStatus().equals(evaluator.currentCertificateStatus(c, today)))
                .forEach(c -> {
                    c.setStatus(evaluator.currentCertificateStatus(c, today));
                    certificateRepository.save(c);
                });
    }

    private void rejudgeByCertificate(GroundCertificate cert) {
        // 若安排曾自动选过别的证书，也可能因持证人当前证书被吊销/改范围而改变结论；
        // 因此不仅重判直接引用本证的安排，也重判该持证人参与的所有未来安排。
        List<DutyAssignment> related = new ArrayList<>(assignmentRepository
                .findByOperatorCertIdOrReviewerCertId(cert.getId(), cert.getId()));
        assignmentRepository.findByFlightDateGreaterThanEqualOrderByFlightDateAscScheduledStartAtAsc(LocalDate.now())
                .stream()
                .filter(a -> cert.getPersonnelId().equals(a.getOperatorId())
                        || cert.getPersonnelId().equals(a.getReviewerId()))
                .forEach(a -> {
                    if (!related.contains(a)) related.add(a);
                });
        for (DutyAssignment assignment : related) {
            if (!assignment.getFlightDate().isBefore(LocalDate.now())) {
                rejudgeIfNeeded(assignment, null);
            }
        }
    }

    private void rejudgeByCertificateId(Long certId) {
        assignmentRepository.findByOperatorCertIdOrReviewerCertId(certId, certId).forEach(a -> rejudgeIfNeeded(a, null));
    }

    private void rejudgeByRouteId(Long routeId) {
        assignmentRepository.findByRouteIdOrderByFlightDateAsc(routeId).forEach(a -> rejudgeIfNeeded(a, null));
    }

    private void rejudgeAllFuture() {
        assignmentRepository.findByFlightDateGreaterThanEqualOrderByFlightDateAscScheduledStartAtAsc(LocalDate.now())
                .forEach(a -> rejudgeIfNeeded(a, null));
    }

    private boolean rejudgeIfNeeded(DutyAssignment assignment, FlightRoute forcedRoute) {
        if (DutyAssignment.READY.equals(assignment.getStatus())
                || DutyAssignment.CANCELLED.equals(assignment.getStatus())
                || assignment.getFlightDate().isBefore(LocalDate.now())) {
            return false;
        }
        FlightRoute route = forcedRoute != null ? forcedRoute
                : routeRepository.findById(assignment.getRouteId()).orElse(null);
        if (route == null) return false;
        AssignmentView view = buildView(assignment, route, getBoundZoneViews(route.getId()));
        if (view.getOperator().isQualified() && view.getReviewer().isQualified()) {
            return false;
        }
        assignment.setStatus(DutyAssignment.DRAFT);
        resetArrival(assignment);
        assignment.setOperatorCertId(view.getOperator().getSelectedCertificateId());
        assignment.setReviewerCertId(view.getReviewer().getSelectedCertificateId());
        assignmentRepository.save(assignment);
        return true;
    }

    private AssignmentView refreshAndView(DutyAssignment assignment) {
        routeRepository.findById(assignment.getRouteId())
                .ifPresent(route -> rejudgeIfNeeded(assignment, route));
        return toAssignmentView(assignment);
    }

    private AssignmentView toAssignmentView(DutyAssignment assignment) {
        FlightRoute route = routeRepository.findById(assignment.getRouteId())
                .orElseThrow(() -> new IllegalArgumentException("航线不存在: " + assignment.getRouteId()));
        return buildView(assignment, route, getBoundZoneViews(route.getId()));
    }

    private AssignmentView buildView(DutyAssignment assignment, FlightRoute route,
                                     List<RouteAnchorZoneView> zoneViews) {
        LocalDate qualificationDate = assignment.getScheduledStartAt().toLocalDate();
        List<String> requiredZones = zoneViews.stream()
                .filter(RouteAnchorZoneView::isActive)
                .map(RouteAnchorZoneView::getAnchorZone)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        RoleQualificationView operatorView;
        RoleQualificationView reviewerView;
        boolean historical = isHistorical(assignment);
        boolean useSnapshot = DutyAssignment.READY.equals(assignment.getStatus())
                || DutyAssignment.CANCELLED.equals(assignment.getStatus())
                || historical;
        if (useSnapshot) {
            if (assignment.getOperatorSnapshot() != null) {
                operatorView = evaluator.fromSnapshot("OPERATOR", "操作员", assignment.getOperatorSnapshot());
            } else {
                operatorView = evaluateRole("OPERATOR", "操作员", assignment.getOperatorId(),
                        assignment.getOperatorName(), assignment.getOperatorCertId(), route, requiredZones, qualificationDate);
            }
            if (assignment.getReviewerSnapshot() != null) {
                reviewerView = evaluator.fromSnapshot("REVIEWER", "复核员", assignment.getReviewerSnapshot());
            } else {
                reviewerView = evaluateRole("REVIEWER", "复核员", assignment.getReviewerId(),
                        assignment.getReviewerName(), assignment.getReviewerCertId(), route, requiredZones, qualificationDate);
            }
        } else {
            operatorView = evaluateRole("OPERATOR", "操作员", assignment.getOperatorId(),
                    assignment.getOperatorName(), assignment.getOperatorCertId(), route, requiredZones, qualificationDate);
            reviewerView = evaluateRole("REVIEWER", "复核员", assignment.getReviewerId(),
                    assignment.getReviewerName(), assignment.getReviewerCertId(), route, requiredZones, qualificationDate);
        }

        List<String> issues = new ArrayList<>();
        if (requiredZones.isEmpty()) {
            issues.add("航线没有在用锚点，无法确认所有锚点区域均被资质覆盖");
        }
        if (assignment.getOperatorId().equals(assignment.getReviewerId())) {
            issues.add("操作员与复核员是同一人，职责分离失败");
        }
        issues.addAll(operatorView.getMissingReasons());
        issues.addAll(reviewerView.getMissingReasons());
        if (!Boolean.TRUE.equals(assignment.getOperatorArrived())
                && DutyAssignment.PENDING_REVIEW.equals(assignment.getStatus())) {
            issues.add("操作员尚未确认现场到位");
        }
        boolean readyAllowed = !useSnapshot
                && assignment.getOperatorId() != null
                && !assignment.getOperatorId().equals(assignment.getReviewerId())
                && Boolean.TRUE.equals(assignment.getOperatorArrived())
                && operatorView.isQualified() && reviewerView.isQualified()
                && !requiredZones.isEmpty()
                && !DutyAssignment.CANCELLED.equals(assignment.getStatus());

        return AssignmentView.builder()
                .id(assignment.getId())
                .routeId(route.getId())
                .routeCode(route.getRouteCode())
                .routeName(route.getRouteName())
                .routeWindLevel(route.getWindLevel())
                .flightDate(assignment.getFlightDate())
                .scheduledStartAt(assignment.getScheduledStartAt())
                .scheduledEndAt(assignment.getScheduledEndAt())
                .status(assignment.getStatus())
                .statusLabel(statusLabel(assignment.getStatus()))
                .operatorArrived(Boolean.TRUE.equals(assignment.getOperatorArrived()))
                .arrivedAt(assignment.getArrivedAt())
                .readyAt(assignment.getReadyAt())
                .cancelledAt(assignment.getCancelledAt())
                .cancelReason(assignment.getCancelReason())
                .operator(operatorView)
                .reviewer(reviewerView)
                .requiredAnchorZones(requiredZones)
                .routeAnchors(zoneViews)
                .blockingIssues(issues.stream().distinct().toList())
                .readyAllowed(readyAllowed)
                .historical(historical)
                .midnightPolicy(DutyRules.POLICY_NAME + "：" + DutyRules.POLICY_EXPLANATION)
                .midnightPolicyRejectedAlternative(DutyRules.REJECTED_ALTERNATIVE + "。" + DutyRules.REJECTED_ALTERNATIVE_REASON)
                .build();
    }

    private RoleQualificationView evaluateRole(String role, String label, Long personnelId, String fallbackName,
                                               Long selectedCertId, FlightRoute route, List<String> requiredZones,
                                               LocalDate qualificationDate) {
        if (personnelId == null) {
            return evaluator.evaluate(role, label, null, null, null, List.of(),
                    route.getWindLevel(), requiredZones, qualificationDate);
        }
        GroundPersonnel person = personnelRepository.findById(personnelId).orElse(null);
        String name = person != null ? person.getPersonName() : fallbackName;
        List<GroundCertificate> certs = person == null ? List.of()
                : certificateRepository.findByPersonnelIdOrderByExpiryDateDescIdDesc(person.getId());
        RoleQualificationView view = evaluator.evaluate(role, label, personnelId, name, selectedCertId, certs,
                route.getWindLevel(), requiredZones, qualificationDate);
        if (person != null && !Boolean.TRUE.equals(person.getActive())) {
            view.setQualified(false);
            view.getMissingReasons().add(label + "人员档案已停用");
        }
        return view;
    }

    private RouteDutyEntryView toRouteEntry(FlightRoute route, LocalDate date, List<DutyAssignment> assignments) {
        DutyAssignment assignment = assignments.stream()
                .filter(a -> a.getRouteId().equals(route.getId()))
                .findFirst().orElse(null);
        List<RouteAnchorZoneView> anchors = getBoundZoneViews(route.getId());
        List<String> zones = anchors.stream()
                .filter(RouteAnchorZoneView::isActive)
                .map(RouteAnchorZoneView::getAnchorZone)
                .filter(Objects::nonNull).distinct().sorted().toList();
        if (assignment == null) {
            return RouteDutyEntryView.builder()
                    .routeId(route.getId()).routeCode(route.getRouteCode()).routeName(route.getRouteName())
                    .windLevel(route.getWindLevel()).flightDate(date)
                    .requiredAnchorZones(zones).qualified(false).ready(false)
                    .issues(List.of("尚未安排操作员和复核员"))
                    .build();
        }
        AssignmentView detail = buildView(assignment, route, anchors);
        return RouteDutyEntryView.builder()
                .routeId(route.getId()).routeCode(route.getRouteCode()).routeName(route.getRouteName())
                .windLevel(route.getWindLevel()).flightDate(date)
                .assignmentId(assignment.getId())
                .assignmentStatus(assignment.getStatus())
                .assignmentStatusLabel(statusLabel(assignment.getStatus()))
                .operatorName(nameSnapshot(assignment, true))
                .reviewerName(nameSnapshot(assignment, false))
                .operatorArrived(Boolean.TRUE.equals(assignment.getOperatorArrived()))
                .requiredAnchorZones(zones)
                .qualified(detail.getBlockingIssues().isEmpty())
                .ready(DutyAssignment.READY.equals(assignment.getStatus()))
                .issues(detail.getBlockingIssues())
                .build();
    }

    private List<RouteAnchorZoneView> getBoundZoneViews(Long routeId) {
        List<RouteAnchor> binds = routeAnchorRepository.findByRouteIdAndStatus(routeId, 1);
        List<RouteAnchorZoneView> views = new ArrayList<>();
        for (RouteAnchor bind : binds) {
            Anchor anchor = anchorRepository.findById(bind.getAnchorId()).orElse(null);
            if (anchor == null) continue;
            views.add(RouteAnchorZoneView.builder()
                    .anchorId(anchor.getId())
                    .anchorCode(anchor.getAnchorCode())
                    .anchorZone(anchor.getAnchorZone() == null || anchor.getAnchorZone().isBlank()
                            ? "未分区" : anchor.getAnchorZone())
                    .active(anchor.getStatus() != null && anchor.getStatus() == 1)
                    .build());
        }
        return views.stream().sorted(Comparator.comparing(RouteAnchorZoneView::getAnchorZone)
                .thenComparing(RouteAnchorZoneView::getAnchorCode)).toList();
    }

    private DutyQualificationSnapshot createSnapshot(RoleQualificationView view, LocalDate date) {
        CertificateCheckView cert = view.getCertificateChecks().stream()
                .filter(c -> c.getCertificateId().equals(view.getSelectedCertificateId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(view.getRoleLabel() + "资格结论未找到可冻结证书"));
        return DutyQualificationSnapshot.builder()
                .personnelId(view.getPersonnelId())
                .personName(view.getPersonName())
                .certificateId(cert.getCertificateId())
                .certificateNo(cert.getCertificateNo())
                .applicableWindLevels(List.copyOf(cert.getApplicableWindLevels()))
                .applicableAnchorZones(List.copyOf(cert.getApplicableAnchorZones()))
                .effectiveDate(cert.getEffectiveDate())
                .expiryDate(cert.getExpiryDate())
                .certificateStatusAtReady(cert.getStatus())
                .qualificationDateAtReady(date)
                .build();
    }

    private boolean isHistorical(DutyAssignment assignment) {
        return DutyAssignment.READY.equals(assignment.getStatus())
                && assignment.getScheduledEndAt() != null
                && assignment.getScheduledEndAt().isBefore(LocalDateTime.now());
    }

    private void validateAssignmentRequest(AssignmentRequestDTO dto) {
        if (dto.getRouteId() == null) throw new IllegalArgumentException("请选择航线");
        if (dto.getScheduledStartAt() == null || dto.getScheduledEndAt() == null) {
            throw new IllegalArgumentException("请填写计划起飞和预计结束时间");
        }
        if (!dto.getScheduledEndAt().isAfter(dto.getScheduledStartAt())) {
            throw new IllegalArgumentException("预计结束时间必须晚于计划起飞时间");
        }
        if (dto.getScheduledStartAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("不能新建或改派计划起飞时刻已经过去的值守");
        }
        if (dto.getOperatorId() == null || dto.getReviewerId() == null) {
            throw new IllegalArgumentException("必须安排一名操作员和一名复核员");
        }
        if (dto.getOperatorId().equals(dto.getReviewerId())) {
            throw new IllegalArgumentException("操作员与复核员必须是不同人员，系统拒绝同一人承担两个职责");
        }
    }

    private void validateSelectedCertificate(Long certId, GroundPersonnel person) {
        if (certId == null) return;
        GroundCertificate cert = certificateRepository.findById(certId)
                .orElseThrow(() -> new IllegalArgumentException("资质证不存在: " + certId));
        if (!cert.getPersonnelId().equals(person.getId())) {
            throw new IllegalArgumentException("资质证" + cert.getCertNo() + "不属于" + person.getPersonName());
        }
    }

    private void validateCertificate(CertificateDTO dto) {
        if (dto.getPersonnelId() == null) throw new IllegalArgumentException("请选择持证人");
        if (dto.getCertNo() == null || dto.getCertNo().isBlank()) throw new IllegalArgumentException("请输入证书编号");
        if (dto.getEffectiveDate() == null || dto.getExpiryDate() == null) throw new IllegalArgumentException("请选择生效日和到期日");
        if (dto.getExpiryDate().isBefore(dto.getEffectiveDate())) throw new IllegalArgumentException("到期日不能早于生效日");
        List<String> winds = normalizeList(dto.getApplicableWindLevels());
        List<String> zones = normalizeList(dto.getApplicableAnchorZones());
        if (winds.isEmpty()) throw new IllegalArgumentException("请至少选择一个适用风级");
        if (zones.isEmpty()) throw new IllegalArgumentException("请至少选择一个可负责锚点区域");
        for (String wind : winds) {
            if (!DutyRules.WIND_LEVELS.contains(wind)) throw new IllegalArgumentException("不支持的风级: " + wind);
        }
        for (String zone : zones) {
            if (!DutyRules.ANCHOR_ZONES.contains(zone)) throw new IllegalArgumentException("不支持的锚点区域: " + zone);
        }
    }

    private void validatePersonnel(PersonnelDTO dto) {
        if (dto == null) throw new IllegalArgumentException("人员信息不能为空");
        if (dto.getEmployeeNo() == null || dto.getEmployeeNo().isBlank()) throw new IllegalArgumentException("请输入工号");
        if (dto.getPersonName() == null || dto.getPersonName().isBlank()) throw new IllegalArgumentException("请输入姓名");
        normalizeRole(dto.getRoleCode());
    }

    private String normalizeRole(String role) {
        if (!ActorContext.ROLE_OPERATOR.equals(role) && !ActorContext.ROLE_SAFETY_MANAGER.equals(role)) {
            throw new IllegalArgumentException("角色必须是 OPERATOR 或 SAFETY_MANAGER");
        }
        return role;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    private GroundPersonnel loadPersonnel(Long id) {
        return personnelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("地勤人员不存在: " + id));
    }

    private GroundPersonnel loadActivePersonnel(Long id) {
        GroundPersonnel person = loadPersonnel(id);
        if (!Boolean.TRUE.equals(person.getActive())) {
            throw new IllegalArgumentException("人员已停用: " + person.getPersonName());
        }
        return person;
    }

    private DutyAssignment loadAssignment(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("值守安排不存在: " + id));
    }

    private FlightRoute loadEnabledRoute(Long id) {
        FlightRoute route = routeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("航线不存在: " + id));
        if (route.getStatus() == null || route.getStatus() != 1) {
            throw new IllegalArgumentException("航线已停用，不能安排开航值守");
        }
        return route;
    }

    private void resetArrival(DutyAssignment assignment) {
        assignment.setOperatorArrived(false);
        assignment.setArrivedAt(null);
        assignment.setStatus(DutyAssignment.DRAFT);
    }

    private String nameSnapshot(DutyAssignment assignment, boolean operator) {
        String snapshotName = operator ? assignment.getOperatorNameSnapshot() : assignment.getReviewerNameSnapshot();
        return snapshotName != null ? snapshotName
                : (operator ? assignment.getOperatorName() : assignment.getReviewerName());
    }

    private String roleLabel(String role) {
        return ActorContext.ROLE_SAFETY_MANAGER.equals(role) ? "安全主管" : "普通值班员";
    }

    private String statusLabel(String status) {
        return switch (status) {
            case DutyAssignment.DRAFT -> "草拟";
            case DutyAssignment.PENDING_REVIEW -> "待复核";
            case DutyAssignment.READY -> "就绪";
            case DutyAssignment.CANCELLED -> "取消";
            default -> status;
        };
    }
}
