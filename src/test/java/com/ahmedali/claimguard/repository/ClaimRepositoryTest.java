package com.ahmedali.claimguard.repository;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.ClaimStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClaimRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ClaimRepository claimRepository;

    private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 6, 1);

    @BeforeEach
    void seedClaims() {
        em.persistAndFlush(buildClaim("CLM-001", "M001", "1234567890", "99213", SERVICE_DATE));
        em.persistAndFlush(buildClaim("CLM-002", "M001", "2345678901", "99214", SERVICE_DATE.plusDays(7)));
        em.persistAndFlush(buildClaim("CLM-003", "M002", "3456789012", "99213", SERVICE_DATE));
    }

    @Test
    void saveAndFindById_roundTripsAllFields() {
        Claim newClaim = buildClaim("CLM-NEW", "M003", "1234567890", "99215", LocalDate.of(2024, 7, 15));

        Claim saved = claimRepository.save(newClaim);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Claim> found = claimRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get())
                .extracting(Claim::getClaimId, Claim::getMemberId, Claim::getStatus, Claim::getBilledAmount)
                .containsExactly("CLM-NEW", "M003", ClaimStatus.SUBMITTED, new BigDecimal("150.00"));
    }

    @Test
    void findByClaimId_returnsClaimWhenPresent() {
        Optional<Claim> found = claimRepository.findByClaimId("CLM-002");

        assertThat(found).isPresent();
        assertThat(found.get())
                .extracting(Claim::getMemberId, Claim::getProviderNpi, Claim::getProcedureCode)
                .containsExactly("M001", "2345678901", "99214");
    }

    @Test
    void findByClaimId_returnsEmptyWhenNoMatch() {
        Optional<Claim> found = claimRepository.findByClaimId("CLM-NOPE");

        assertThat(found).isEmpty();
    }

    @Test
    void findAllByMemberId_returnsAllClaimsForMember() {
        List<Claim> claims = claimRepository.findAllByMemberId("M001");

        assertThat(claims).hasSize(2);
        assertThat(claims)
                .extracting(Claim::getClaimId)
                .containsExactlyInAnyOrder("CLM-001", "CLM-002");
    }

    @Test
    void findAllByMemberId_returnsEmptyListWhenNoClaims() {
        List<Claim> claims = claimRepository.findAllByMemberId("M005");

        assertThat(claims).isEmpty();
    }

    @Test
    void findByMemberIdAndProcedureCodeAndServiceDate_returnsMatchingClaim() {
        List<Claim> claims = claimRepository.findByMemberIdAndProcedureCodeAndServiceDate(
                "M001", "99213", SERVICE_DATE);

        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).getClaimId()).isEqualTo("CLM-001");
    }

    @Test
    void findByMemberIdAndProcedureCodeAndServiceDate_returnsEmptyWhenServiceDateOffByOneDay() {
        List<Claim> claims = claimRepository.findByMemberIdAndProcedureCodeAndServiceDate(
                "M001", "99213", SERVICE_DATE.plusDays(1));

        assertThat(claims).isEmpty();
    }

    @Test
    void duplicateClaimId_throwsDataIntegrityViolationException() {
        Claim duplicate = buildClaim("CLM-001", "M002", "3456789012", "99213", LocalDate.of(2024, 8, 1));

        assertThatThrownBy(() -> claimRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Claim buildClaim(String claimId, String memberId, String npi,
                                    String procedureCode, LocalDate serviceDate) {
        return Claim.builder()
                .claimId(claimId)
                .memberId(memberId)
                .providerNpi(npi)
                .serviceDate(serviceDate)
                .submissionDate(serviceDate.plusDays(1))
                .procedureCode(procedureCode)
                .diagnosisCode("E11.9")
                .billedAmount(new BigDecimal("150.00"))
                .status(ClaimStatus.SUBMITTED)
                .build();
    }
}
