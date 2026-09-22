package com.px.base.repository;

import com.px.base.entity.DutyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DutyAssignmentRepository extends JpaRepository<DutyAssignment, Long> {
    Optional<DutyAssignment> findByRouteIdAndFlightDate(Long routeId, LocalDate flightDate);
    List<DutyAssignment> findByFlightDateOrderByScheduledStartAtAscRouteCodeAsc(LocalDate flightDate);
    List<DutyAssignment> findByFlightDateGreaterThanEqualOrderByFlightDateAscScheduledStartAtAsc(LocalDate date);
    List<DutyAssignment> findByOperatorCertIdOrReviewerCertId(Long operatorCertId, Long reviewerCertId);
    List<DutyAssignment> findByRouteIdOrderByFlightDateAsc(Long routeId);
}
