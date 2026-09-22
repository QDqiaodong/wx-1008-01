package com.px.base.repository;

import com.px.base.entity.GroundCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroundCertificateRepository extends JpaRepository<GroundCertificate, Long> {
    Optional<GroundCertificate> findByCertNo(String certNo);
    boolean existsByCertNo(String certNo);
    List<GroundCertificate> findByPersonnelIdOrderByExpiryDateDescIdDesc(Long personnelId);
}
