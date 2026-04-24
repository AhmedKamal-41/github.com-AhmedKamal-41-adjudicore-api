package com.ahmedali.claimguard.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EntityBehaviorTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void memberEqualsAndHashCode_useMemberIdAsBusinessKey() {
        Member a = Member.builder().memberId("M001").firstName("A").build();
        Member sameKey = Member.builder().memberId("M001").firstName("Different").build();
        Member differentKey = Member.builder().memberId("M002").firstName("A").build();

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(sameKey);
        assertThat(a).hasSameHashCodeAs(sameKey);
        assertThat(a).isNotEqualTo(differentKey);
        assertThat(a).isNotEqualTo("not a member");
        assertThat(a).isNotEqualTo(null);
    }

    @Test
    void providerEqualsAndHashCode_useNpiAsBusinessKey() {
        Provider a = Provider.builder().npi("1111111111").name("A").isInNetwork(true).build();
        Provider sameKey = Provider.builder().npi("1111111111").name("Different").isInNetwork(false).build();
        Provider differentKey = Provider.builder().npi("2222222222").name("A").isInNetwork(true).build();

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(sameKey);
        assertThat(a).hasSameHashCodeAs(sameKey);
        assertThat(a).isNotEqualTo(differentKey);
        assertThat(a).isNotEqualTo("not a provider");
        assertThat(a).isNotEqualTo(null);
    }

    @Test
    void claimEqualsAndHashCode_useClaimIdAsBusinessKey() {
        Claim a = baseClaim().claimId("CLM-X").build();
        Claim sameKey = baseClaim().claimId("CLM-X").memberId("M002").build();
        Claim differentKey = baseClaim().claimId("CLM-Y").build();

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(sameKey);
        assertThat(a).hasSameHashCodeAs(sameKey);
        assertThat(a).isNotEqualTo(differentKey);
        assertThat(a).isNotEqualTo("not a claim");
        assertThat(a).isNotEqualTo(null);
    }

    @Test
    void claim_preUpdateRefreshesUpdatedAt() {
        Claim claim = baseClaim().claimId("CLM-UPDATE-TEST").build();
        em.persistAndFlush(claim);

        LocalDateTime initialUpdatedAt = claim.getUpdatedAt();
        assertThat(initialUpdatedAt).isNotNull();

        em.clear();
        Claim reloaded = em.find(Claim.class, claim.getId());
        reloaded.setStatus(ClaimStatus.VALIDATED);
        em.flush();
        em.refresh(reloaded);

        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.VALIDATED);
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(initialUpdatedAt);
    }

    private static Claim.ClaimBuilder baseClaim() {
        return Claim.builder()
                .memberId("M001")
                .providerNpi("1234567890")
                .serviceDate(LocalDate.of(2024, 6, 1))
                .submissionDate(LocalDate.of(2024, 6, 2))
                .procedureCode("99213")
                .diagnosisCode("E11.9")
                .billedAmount(new BigDecimal("150.00"))
                .status(ClaimStatus.SUBMITTED);
    }
}
