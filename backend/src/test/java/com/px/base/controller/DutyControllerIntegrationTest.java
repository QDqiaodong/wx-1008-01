package com.px.base.controller;

import com.px.base.entity.Anchor;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:duty-controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class DutyControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired GroundPersonnelRepository personnelRepository;
    @Autowired GroundCertificateRepository certificateRepository;
    @Autowired FlightRouteRepository routeRepository;
    @Autowired AnchorRepository anchorRepository;
    @Autowired RouteAnchorRepository routeAnchorRepository;
    @Autowired DutyAssignmentRepository assignmentRepository;

    private GroundPersonnel operator;
    private GroundPersonnel reviewer;
    private GroundCertificate certificate;
    private Long routeId;

    @BeforeEach
    void setUp() {
        assignmentRepository.deleteAll();
        certificateRepository.deleteAll();
        personnelRepository.deleteAll();
        routeAnchorRepository.deleteAll();
        anchorRepository.deleteAll();
        routeRepository.deleteAll();
        operator = personnelRepository.save(GroundPersonnel.builder()
                .employeeNo("OP1").personName("操作员一").roleCode("OPERATOR").active(true).build());
        reviewer = personnelRepository.save(GroundPersonnel.builder()
                .employeeNo("RV1").personName("复核员一").roleCode("OPERATOR").active(true).build());
        FlightRoute route = routeRepository.save(FlightRoute.builder()
                .routeCode("R-API").routeName("API线").routeGroup("东区").windSpeed(BigDecimal.TEN)
                .windLevel("强风").status(1).build());
        routeId = route.getId();
        Anchor anchor = anchorRepository.save(Anchor.builder().anchorCode("A-API").maxWeight(BigDecimal.valueOf(2000))
                .minWindSpeed(BigDecimal.ZERO).maxWindSpeed(BigDecimal.valueOf(20)).anchorZone("东区").status(1).build());
        routeAnchorRepository.save(RouteAnchor.builder().routeId(routeId).anchorId(anchor.getId())
                .status(1).bindTime(LocalDateTime.now()).build());
        certificate = certificateRepository.save(GroundCertificate.builder()
                .certNo("C-API").personnelId(operator.getId()).personName(operator.getPersonName())
                .applicableWindLevels(List.of("强风")).applicableAnchorZones(List.of("东区"))
                .effectiveDate(java.time.LocalDate.now().minusDays(1))
                .expiryDate(java.time.LocalDate.now().plusYears(1)).status(GroundCertificate.VALID).build());
    }

    @Test
    void revokeWithoutManagerHeaderChangesNothing() throws Exception {
        mvc.perform(post("/api/duty/certificates/{id}/revoke", certificate.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        GroundCertificate reloaded = certificateRepository.findById(certificate.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(GroundCertificate.VALID, reloaded.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(reloaded.getRevokedAt());
    }

    @Test
    void revokeWithOperatorHeaderChangesNothing() throws Exception {
        mvc.perform(post("/api/duty/certificates/{id}/revoke", certificate.getId())
                        .header("X-Actor-Id", reviewer.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("安全主管")));
        org.junit.jupiter.api.Assertions.assertEquals(GroundCertificate.VALID,
                certificateRepository.findById(certificate.getId()).orElseThrow().getStatus());
    }
}
