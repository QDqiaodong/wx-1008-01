package com.px.base.service;

import com.px.base.dto.AssignmentRequestDTO;
import com.px.base.dto.AssignmentView;
import com.px.base.entity.Anchor;
import com.px.base.entity.DutyAssignment;
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
import com.px.base.security.ActorContext;
import com.px.base.security.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class DutyServiceTest {
    @Autowired GroundPersonnelRepository personnelRepository;
    @Autowired GroundCertificateRepository certificateRepository;
    @Autowired DutyAssignmentRepository assignmentRepository;
    @Autowired FlightRouteRepository routeRepository;
    @Autowired RouteAnchorRepository routeAnchorRepository;
    @Autowired AnchorRepository anchorRepository;

    DutyService dutyService;
    GroundPersonnel op;
    GroundPersonnel reviewer;
    GroundPersonnel manager;
    GroundPersonnel partial;
    FlightRoute route;

    @BeforeEach
    void setUp() {
        dutyService = new DutyService(personnelRepository, certificateRepository, assignmentRepository,
                routeRepository, routeAnchorRepository, anchorRepository, new DutyQualificationEvaluator());
        op = personnelRepository.save(GroundPersonnel.builder()
                .employeeNo("OP").personName("操作员").roleCode("OPERATOR").active(true).build());
        reviewer = personnelRepository.save(GroundPersonnel.builder()
                .employeeNo("RV").personName("复核员").roleCode("OPERATOR").active(true).build());
        manager = personnelRepository.save(GroundPersonnel.builder()
                .employeeNo("MGR").personName("主管").roleCode("SAFETY_MANAGER").active(true).build());
        partial = personnelRepository.save(GroundPersonnel.builder()
                .employeeNo("PT").personName("分区员").roleCode("OPERATOR").active(true).build());
        route = routeRepository.save(FlightRoute.builder()
                .routeCode("R1").routeName("测试线").routeGroup("东区")
                .windSpeed(java.math.BigDecimal.valueOf(8)).windLevel("和风")
                .status(1).build());
        for (String zone : zones()) {
            Anchor anchor = anchorRepository.save(Anchor.builder()
                    .anchorCode("A-" + zone).maxWeight(java.math.BigDecimal.valueOf(2000))
                    .minWindSpeed(java.math.BigDecimal.ZERO).maxWindSpeed(java.math.BigDecimal.valueOf(20))
                    .anchorZone(zone).status(1).build());
            routeAnchorRepository.save(RouteAnchor.builder()
                    .routeId(route.getId()).anchorId(anchor.getId()).status(1)
                    .bindTime(LocalDateTime.now()).build());
        }
        seedCertificate("C-OP", op, List.of("微风", "轻风", "和风", "强风", "疾风"), zones());
        seedCertificate("C-RV", reviewer, List.of("微风", "轻风", "和风", "强风", "疾风"), zones());
        seedCertificate("C-PT", partial, List.of("微风", "轻风", "和风", "强风", "疾风"),
                List.of("东区", "西区", "北区"));
    }

    @Test
    void rejectsSamePersonForBothDuties() {
        var request = request(LocalDate.now().plusDays(1), op.getId(), op.getId());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> dutyService.createOrUpdateAssignment(request));
        assertTrue(error.getMessage().contains("不同人员"));
        assertEquals(0, assignmentRepository.count());
    }

    @Test
    void listsUncoveredAnchorZonesPerPerson() {
        var request = request(LocalDate.now().plusDays(1), partial.getId(), reviewer.getId());
        AssignmentView view = dutyService.createOrUpdateAssignment(request);
        assertFalse(view.getOperator().isQualified());
        assertTrue(view.getOperator().getMissingReasons().stream().anyMatch(s -> s.contains("南区")));
        assertTrue(view.getBlockingIssues().stream().anyMatch(s -> s.contains("南区")));
    }

    @Test
    void crossMidnightUsesTakeoffDateEvenWhenEndingAfterExpiry() {
        LocalDate takeoff = LocalDate.now().plusDays(2);
        GroundCertificate cert = certificateRepository.findByCertNo("C-OP").orElseThrow();
        cert.setEffectiveDate(takeoff.minusDays(1));
        cert.setExpiryDate(takeoff);
        certificateRepository.save(cert);

        AssignmentView view = dutyService.createOrUpdateAssignment(request(takeoff, op.getId(), reviewer.getId()));
        assertTrue(view.getOperator().isQualified(), view.getBlockingIssues().toString());
        assertTrue(view.getScheduledEndAt().toLocalDate().isAfter(takeoff));
    }

    @Test
    void revocationRejudgesFutureButKeepsFinishedSnapshot() {
        LocalDate future = LocalDate.now().plusDays(3);
        AssignmentView futureView = dutyService.createOrUpdateAssignment(request(future, op.getId(), reviewer.getId()));

        LocalDate readyDate = LocalDate.now().plusDays(6);
        AssignmentView historyView = dutyService.createOrUpdateAssignment(request(readyDate, op.getId(), reviewer.getId()));
        ActorContext opActor = ActorContext.from(op);
        ActorContext reviewerActor = ActorContext.from(reviewer);
        dutyService.confirmArrival(historyView.getId(), opActor);
        dutyService.confirmReady(historyView.getId(), reviewerActor);
        DutyAssignment readyAssignment = assignmentRepository.findById(historyView.getId()).orElseThrow();
        readyAssignment.setScheduledStartAt(LocalDateTime.now().minusHours(2));
        readyAssignment.setScheduledEndAt(LocalDateTime.now().minusHours(1));
        assignmentRepository.save(readyAssignment);

        GroundCertificate opCert = certificateRepository.findByCertNo("C-OP").orElseThrow();
        ActorContext managerActor = ActorContext.from(manager);
        dutyService.revokeCertificate(opCert.getId(), "测试吊销", managerActor);

        DutyAssignment futureAssignment = assignmentRepository.findById(futureView.getId()).orElseThrow();
        assertEquals(DutyAssignment.DRAFT, futureAssignment.getStatus());
        assertFalse(futureAssignment.getOperatorArrived());

        op.setPersonName("操作员改名后");
        personnelRepository.save(op);

        DutyAssignment history = assignmentRepository.findById(historyView.getId()).orElseThrow();
        assertEquals(DutyAssignment.READY, history.getStatus());
        assertNotNull(history.getOperatorSnapshot());
        assertEquals("C-OP", history.getOperatorSnapshot().getCertificateNo());
        assertEquals("操作员", history.getOperatorSnapshot().getPersonName());
        assertEquals("操作员", history.getOperatorNameSnapshot());
        assertEquals(zones(), history.getOperatorSnapshot().getApplicableAnchorZones());
    }

    @Test
    void nonManagerCannotRevokeOrCancelReady() {
        AssignmentView view = dutyService.createOrUpdateAssignment(
                request(LocalDate.now().plusDays(4), op.getId(), reviewer.getId()));
        dutyService.confirmArrival(view.getId(), ActorContext.from(op));
        dutyService.confirmReady(view.getId(), ActorContext.from(reviewer));

        GroundCertificate cert = certificateRepository.findByCertNo("C-OP").orElseThrow();
        ForbiddenException revokeError = assertThrows(ForbiddenException.class,
                () -> dutyService.revokeCertificate(cert.getId(), "越权", ActorContext.from(reviewer)));
        assertTrue(revokeError.getMessage().contains("安全主管"));
        assertEquals(GroundCertificate.VALID, certificateRepository.findById(cert.getId()).orElseThrow().getStatus());

        ForbiddenException cancelError = assertThrows(ForbiddenException.class,
                () -> dutyService.cancelReady(view.getId(), "越权", ActorContext.from(reviewer)));
        assertTrue(cancelError.getMessage().contains("安全主管"));
        assertEquals(DutyAssignment.READY, assignmentRepository.findById(view.getId()).orElseThrow().getStatus());
    }

    @Test
    void operatorCannotReviewOwnWorkAndOnlyOwnArrivalIsAllowed() {
        AssignmentView view = dutyService.createOrUpdateAssignment(
                request(LocalDate.now().plusDays(5), op.getId(), reviewer.getId()));
        ForbiddenException otherOperator = assertThrows(ForbiddenException.class,
                () -> dutyService.confirmArrival(view.getId(), ActorContext.from(reviewer)));
        assertTrue(otherOperator.getMessage().contains("自己的现场到位"));

        dutyService.confirmArrival(view.getId(), ActorContext.from(op));
        ForbiddenException selfReview = assertThrows(ForbiddenException.class,
                () -> dutyService.confirmReady(view.getId(), ActorContext.from(op)));
        assertTrue(selfReview.getMessage().contains("不能复核自己"));
    }

    private AssignmentRequestDTO request(LocalDate date, Long operatorId, Long reviewerId) {
        LocalDateTime start = date.atTime(23, 0);
        var request = new AssignmentRequestDTO();
        request.setRouteId(route.getId());
        request.setScheduledStartAt(start);
        request.setScheduledEndAt(start.plusHours(2));
        request.setOperatorId(operatorId);
        request.setReviewerId(reviewerId);
        return request;
    }

    private void seedCertificate(String no, GroundPersonnel person, List<String> winds, List<String> zones) {
        certificateRepository.save(GroundCertificate.builder()
                .certNo(no).personnelId(person.getId()).personName(person.getPersonName())
                .applicableWindLevels(winds).applicableAnchorZones(zones)
                .effectiveDate(LocalDate.now().minusDays(1))
                .expiryDate(LocalDate.now().plusYears(1))
                .status(GroundCertificate.VALID)
                .build());
    }

    private List<String> zones() {
        return List.of("东区", "南区", "西区", "北区");
    }
}
