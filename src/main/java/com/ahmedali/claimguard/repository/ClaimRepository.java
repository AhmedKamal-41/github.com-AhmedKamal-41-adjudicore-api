package com.ahmedali.claimguard.repository;

import com.ahmedali.claimguard.domain.Claim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    Optional<Claim> findByClaimId(String claimId);

    List<Claim> findAllByMemberId(String memberId);

    List<Claim> findByMemberIdAndProcedureCodeAndServiceDate(
            String memberId, String procedureCode, LocalDate serviceDate);
}
