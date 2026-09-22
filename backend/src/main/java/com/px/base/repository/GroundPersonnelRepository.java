package com.px.base.repository;

import com.px.base.entity.GroundPersonnel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroundPersonnelRepository extends JpaRepository<GroundPersonnel, Long> {
    Optional<GroundPersonnel> findByEmployeeNo(String employeeNo);
    boolean existsByEmployeeNo(String employeeNo);
    List<GroundPersonnel> findByActiveTrueOrderByIdAsc();
}
