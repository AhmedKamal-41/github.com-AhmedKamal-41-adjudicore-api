package com.ahmedali.claimguard.repository;

import com.ahmedali.claimguard.domain.ClaimAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimAuditLogRepository extends JpaRepository<ClaimAuditLog, Long> {

    List<ClaimAuditLog> findAllByClaimIdOrderByChangedAtDesc(String claimId);
}
