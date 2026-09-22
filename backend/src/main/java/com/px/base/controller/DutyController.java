package com.px.base.controller;

import com.px.base.dto.AssignmentRequestDTO;
import com.px.base.dto.AssignmentView;
import com.px.base.dto.CancelAssignmentRequestDTO;
import com.px.base.dto.CertificateDTO;
import com.px.base.dto.DutyPolicyView;
import com.px.base.dto.PersonnelCertificateView;
import com.px.base.dto.PersonnelDTO;
import com.px.base.dto.ResponseDTO;
import com.px.base.dto.RevokeCertificateRequestDTO;
import com.px.base.dto.RouteDutyEntryView;
import com.px.base.entity.GroundCertificate;
import com.px.base.entity.GroundPersonnel;
import com.px.base.security.ActorContext;
import com.px.base.service.ActorService;
import com.px.base.service.DutyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/duty")
@RequiredArgsConstructor
public class DutyController {
    private final DutyService dutyService;
    private final ActorService actorService;

    @GetMapping("/policy")
    public ResponseDTO<DutyPolicyView> policy() {
        return ResponseDTO.success(dutyService.policy());
    }

    @GetMapping("/personnel")
    public ResponseDTO<List<PersonnelCertificateView>> personnel() {
        return ResponseDTO.success(dutyService.listPersonnel());
    }

    @PostMapping("/personnel")
    public ResponseDTO<GroundPersonnel> createPersonnel(@RequestBody PersonnelDTO dto) {
        return ResponseDTO.success(dutyService.createPersonnel(dto));
    }

    @PutMapping("/personnel/{id}")
    public ResponseDTO<GroundPersonnel> updatePersonnel(@PathVariable Long id, @RequestBody PersonnelDTO dto) {
        return ResponseDTO.success(dutyService.updatePersonnel(id, dto));
    }

    @PostMapping("/certificates")
    public ResponseDTO<GroundCertificate> createCertificate(@RequestBody CertificateDTO dto) {
        return ResponseDTO.success(dutyService.createCertificate(dto));
    }

    @PutMapping("/certificates/{id}")
    public ResponseDTO<GroundCertificate> updateCertificate(@PathVariable Long id, @RequestBody CertificateDTO dto) {
        return ResponseDTO.success(dutyService.updateCertificate(id, dto));
    }

    @PostMapping("/certificates/{id}/revoke")
    public ResponseDTO<GroundCertificate> revokeCertificate(@PathVariable Long id,
                                                            @RequestBody(required = false) RevokeCertificateRequestDTO body,
                                                            @RequestHeader(value = ActorContext.HEADER, required = false) String actorId) {
        ActorContext actor = actorService.requireSafetyManager(actorId);
        String reason = body == null ? null : body.getReason();
        return ResponseDTO.success("证书已吊销，未来未就绪安排已立即重判",
                dutyService.revokeCertificate(id, reason, actor));
    }

    @GetMapping("/assignments")
    public ResponseDTO<List<AssignmentView>> assignments(@RequestParam(required = false) LocalDate date) {
        return ResponseDTO.success(dutyService.listAssignments(date));
    }

    @GetMapping("/assignments/{id}")
    public ResponseDTO<AssignmentView> assignment(@PathVariable Long id) {
        return ResponseDTO.success(dutyService.getAssignment(id));
    }

    @PostMapping("/assignments")
    public ResponseDTO<AssignmentView> saveAssignment(@RequestBody AssignmentRequestDTO dto) {
        return ResponseDTO.success(dutyService.createOrUpdateAssignment(dto));
    }

    @PostMapping("/assignments/{id}/arrive")
    public ResponseDTO<AssignmentView> arrive(@PathVariable Long id,
                                              @RequestHeader(value = ActorContext.HEADER, required = false) String actorId) {
        ActorContext actor = actorService.require(actorId);
        return ResponseDTO.success("操作员现场到位已确认，值守进入待复核",
                dutyService.confirmArrival(id, actor));
    }

    @PostMapping("/assignments/{id}/ready")
    public ResponseDTO<AssignmentView> ready(@PathVariable Long id,
                                             @RequestHeader(value = ActorContext.HEADER, required = false) String actorId) {
        ActorContext actor = actorService.require(actorId);
        return ResponseDTO.success("复核员已确认就绪，证书编号、适用范围和姓名已形成历史快照",
                dutyService.confirmReady(id, actor));
    }

    @PostMapping("/assignments/{id}/cancel")
    public ResponseDTO<AssignmentView> cancel(@PathVariable Long id,
                                              @RequestBody(required = false) CancelAssignmentRequestDTO body,
                                              @RequestHeader(value = ActorContext.HEADER, required = false) String actorId) {
        ActorContext actor = actorService.requireSafetyManager(actorId);
        String reason = body == null ? null : body.getReason();
        return ResponseDTO.success("已就绪值守已由安全主管取消", dutyService.cancelReady(id, reason, actor));
    }

    @GetMapping("/route-entries")
    public ResponseDTO<List<RouteDutyEntryView>> routeEntries(@RequestParam(required = false) LocalDate date) {
        return ResponseDTO.success(dutyService.routeEntries(date));
    }
}
