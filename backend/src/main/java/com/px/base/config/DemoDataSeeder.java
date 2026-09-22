package com.px.base.config;

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
import com.px.base.service.AnchorRankCacheService;
import com.px.base.service.DutyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地验收用幂等种子数据（仅 local profile）。
 */
@Component
@Profile("local")
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements CommandLineRunner {

    private final AnchorRepository anchorRepository;
    private final FlightRouteRepository routeRepository;
    private final AnchorRankCacheService cacheService;
    private final RouteAnchorRepository routeAnchorRepository;
    private final GroundPersonnelRepository personnelRepository;
    private final GroundCertificateRepository certificateRepository;
    private final DutyAssignmentRepository assignmentRepository;
    private final DutyService dutyService;

    @Override
    @Transactional
    public void run(String... args) {
        seedAnchor("A-GOOD-2000", "2000", "0.00", "20.00", "东区", "全能型重载锚点");
        seedAnchor("A-MID-900", "900", "1.00", "9.00", "东区", "中小风常规锚点");
        seedAnchor("A-SF600", "600", "10.00", "14.00", "西区", "只适配强风但承重仅600kg的锚点");
        seedAnchor("A-WEAK400", "400", "0.00", "8.00", "南区", "承重不足的微风锚点");
        seedAnchor("A-GALE2600", "2600", "12.00", "25.00", "北区", "疾风重载锚点");
        seedAnchor("A-SCARCE-1800", "1800", "0.00", "16.00", "南区", "稀缺通用锚点(并发争抢样本)");

        seedRoute("R-WEAK", "晨曦微风线", "东区", "2.00", "微风");
        seedRoute("R-LIGHT", "轻风巡航线", "东区", "4.50", "轻风");
        seedRoute("R-MOD", "和风观景线", "南区", "8.00", "和风");
        seedRoute("R-STRONG", "强风挑战线", "西区", "12.00", "强风");
        seedRoute("R-GALE", "疾风极限线", "北区", "16.00", "疾风");

        bind("R-WEAK", "A-GOOD-2000");
        bind("R-MOD", "A-MID-900");
        bind("R-MOD", "A-SCARCE-1800");
        bind("R-STRONG", "A-SF600");
        bind("R-GALE", "A-GALE2600");

        GroundPersonnel zhang = seedPersonnel("P-OP-ZHANG", "张操作", "OPERATOR");
        GroundPersonnel li = seedPersonnel("P-REV-LI", "李复核", "OPERATOR");
        GroundPersonnel wang = seedPersonnel("P-MGR-WANG", "王主管", "SAFETY_MANAGER");
        GroundPersonnel chen = seedPersonnel("P-PART-CHEN", "陈分区", "OPERATOR");
        GroundPersonnel revokedHolder = seedPersonnel("P-REVOKED-ZHAO", "赵旧证", "OPERATOR");

        seedCertificate("C-ALL-ZHANG", zhang, allWind(), allZone(), -365, 365);
        seedCertificate("C-ALL-LI", li, allWind(), allZone(), -365, 365);
        seedCertificate("C-PART-CHEN", chen, allWind(), List.of("东区", "南区", "西区"), -365, 365);
        GroundCertificate revoked = seedCertificate("C-REVOKED-OLD", revokedHolder, allWind(), allZone(), -365, 365);
        seedCertificate("C-MGR-WANG", wang, allWind(), allZone(), -365, 365);
        seedFutureDutySamples(zhang, li, revokedHolder, revoked);

        List<Anchor> all = anchorRepository.findByStatus(1);
        all.forEach(cacheService::addAnchorRanks);
        log.info("本地种子数据就绪：锚点{}个、航线{}个、人员{}个、值守{}个，Redis排序缓存已全量初始化",
                all.size(), routeRepository.findByStatus(1).size(),
                personnelRepository.count(), assignmentRepository.count());
    }

    private void seedAnchor(String code, String weight, String minWind, String maxWind, String zone, String desc) {
        Anchor anchor = anchorRepository.findByAnchorCode(code).orElse(null);
        if (anchor == null) {
            anchor = Anchor.builder()
                    .anchorCode(code)
                    .maxWeight(new BigDecimal(weight))
                    .minWindSpeed(new BigDecimal(minWind))
                    .maxWindSpeed(new BigDecimal(maxWind))
                    .anchorZone(zone)
                    .locationDesc(desc)
                    .status(1)
                    .build();
        }
        if (anchor.getAnchorZone() == null || anchor.getAnchorZone().isBlank()) {
            anchor.setAnchorZone(zone);
        }
        anchorRepository.save(anchor);
    }

    private void seedRoute(String code, String name, String group, String wind, String level) {
        if (routeRepository.existsByRouteCode(code)) return;
        routeRepository.save(FlightRoute.builder()
                .routeCode(code)
                .routeName(name)
                .routeGroup(group)
                .windSpeed(new BigDecimal(wind))
                .windLevel(level)
                .description(name)
                .status(1)
                .build());
    }

    private void bind(String routeCode, String anchorCode) {
        FlightRoute route = routeRepository.findByRouteCode(routeCode).orElseThrow();
        Anchor anchor = anchorRepository.findByAnchorCode(anchorCode).orElseThrow();
        boolean exists = routeAnchorRepository.findByRouteIdAndAnchorId(route.getId(), anchor.getId()).isPresent();
        if (exists) return;
        routeAnchorRepository.save(RouteAnchor.builder()
                .routeId(route.getId())
                .anchorId(anchor.getId())
                .status(1)
                .bindTime(LocalDateTime.now())
                .build());
    }

    private GroundPersonnel seedPersonnel(String no, String name, String role) {
        return personnelRepository.findByEmployeeNo(no).orElseGet(() -> personnelRepository.save(
                GroundPersonnel.builder().employeeNo(no).personName(name).roleCode(role).active(true).build()));
    }

    private GroundCertificate seedCertificate(String no, GroundPersonnel person, List<String> winds,
                                              List<String> zones, int effectiveOffset, int expiryOffset) {
        if (certificateRepository.findByCertNo(no).isEmpty()) {
            GroundCertificate cert = GroundCertificate.builder()
                    .certNo(no).personnelId(person.getId()).personName(person.getPersonName())
                    .applicableWindLevels(winds).applicableAnchorZones(zones)
                    .effectiveDate(LocalDate.now().plusDays(effectiveOffset))
                    .expiryDate(LocalDate.now().plusDays(expiryOffset))
                    .status(GroundCertificate.VALID)
                    .build();
            if (expiryOffset < 0) {
                cert.setStatus(GroundCertificate.EXPIRED);
            }
            return certificateRepository.save(cert);
        }
        return certificateRepository.findByCertNo(no).orElseThrow();
    }

    private void seedFutureDutySamples(GroundPersonnel zhang, GroundPersonnel li,
                                       GroundPersonnel revokedHolder, GroundCertificate revoked) {
        FlightRoute mod = routeRepository.findByRouteCode("R-MOD").orElseThrow();
        LocalDate date = LocalDate.now().plusDays(1);
        if (assignmentRepository.findByRouteIdAndFlightDate(mod.getId(), date).isEmpty()) {
            LocalDateTime start = date.atTime(23, 0);
            var request = new com.px.base.dto.AssignmentRequestDTO();
            request.setRouteId(mod.getId());
            request.setScheduledStartAt(start);
            request.setScheduledEndAt(start.plusHours(2));
            request.setOperatorId(zhang.getId());
            request.setReviewerId(li.getId());
            dutyService.createOrUpdateAssignment(request);
        }
        FlightRoute galeRoute = routeRepository.findByRouteCode("R-GALE").orElseThrow();
        LocalDate revokedDate = LocalDate.now().plusDays(3);
        if (assignmentRepository.findByRouteIdAndFlightDate(galeRoute.getId(), revokedDate).isEmpty()) {
            LocalDateTime revokedStart = revokedDate.atTime(9, 0);
            var revokedRequest = new com.px.base.dto.AssignmentRequestDTO();
            revokedRequest.setRouteId(galeRoute.getId());
            revokedRequest.setScheduledStartAt(revokedStart);
            revokedRequest.setScheduledEndAt(revokedStart.plusHours(1));
            revokedRequest.setOperatorId(revokedHolder.getId());
            revokedRequest.setOperatorCertId(revoked.getId());
            revokedRequest.setReviewerId(seedPersonnel("P-MGR-WANG", "王主管", "SAFETY_MANAGER").getId());
            dutyService.createOrUpdateAssignment(revokedRequest);
        }
        // 通过安全主管服务吊销，确保随后页面无需等待事件也能看到未来值守已退回草拟。
        dutyService.revokeCertificate(revoked.getId(), "种子数据：验收吊销后重判",
                new com.px.base.security.ActorContext(wang.getId(), wang.getPersonName(), wang.getRoleCode(), true));

        FlightRoute gale = routeRepository.findByRouteCode("R-GALE").orElseThrow();
        LocalDate historicalDate = LocalDate.now().plusDays(2);
        if (assignmentRepository.findByRouteIdAndFlightDate(gale.getId(), historicalDate).isEmpty()) {
            LocalDateTime start = historicalDate.atTime(20, 0);
            var req = new com.px.base.dto.AssignmentRequestDTO();
            req.setRouteId(gale.getId());
            req.setScheduledStartAt(start);
            req.setScheduledEndAt(start.plusHours(1));
            req.setOperatorId(zhang.getId());
            req.setReviewerId(li.getId());
            var view = dutyService.createOrUpdateAssignment(req);
            dutyService.confirmArrival(view.getId(),
                    new com.px.base.security.ActorContext(zhang.getId(), zhang.getPersonName(), zhang.getRoleCode(), false));
            dutyService.confirmReady(view.getId(),
                    new com.px.base.security.ActorContext(li.getId(), li.getPersonName(), li.getRoleCode(), false));
            assignmentRepository.findById(view.getId()).ifPresent(history -> {
                history.setScheduledStartAt(LocalDateTime.now().minusHours(2));
                history.setScheduledEndAt(LocalDateTime.now().minusHours(1));
                assignmentRepository.save(history);
            });
        }
    }

    private List<String> allWind() {
        return List.of("微风", "轻风", "和风", "强风", "疾风");
    }

    private List<String> allZone() {
        return List.of("东区", "南区", "西区", "北区");
    }
}
