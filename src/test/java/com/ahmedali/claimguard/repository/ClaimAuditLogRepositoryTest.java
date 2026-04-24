package com.ahmedali.claimguard.repository;

import com.ahmedali.claimguard.domain.ClaimAuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClaimAuditLogRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ClaimAuditLogRepository auditLogRepository;

    @Test
    void saveAndFindById_roundTripsAllFields() {
        ClaimAuditLog log = ClaimAuditLog.builder()
                .claimId("CLM-001")
                .previousStatus("SUBMITTED")
                .newStatus("VALIDATED")
                .reasonCodes("CARC-1,CARC-2")
                .notes("validation passed")
                .changedBy("SYSTEM")
                .build();

        ClaimAuditLog saved = auditLogRepository.save(log);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getChangedAt()).isNotNull();

        ClaimAuditLog reloaded = auditLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded)
                .extracting(ClaimAuditLog::getClaimId, ClaimAuditLog::getPreviousStatus,
                        ClaimAuditLog::getNewStatus, ClaimAuditLog::getReasonCodes,
                        ClaimAuditLog::getNotes, ClaimAuditLog::getChangedBy)
                .containsExactly("CLM-001", "SUBMITTED", "VALIDATED", "CARC-1,CARC-2", "validation passed", "SYSTEM");
    }

    @Test
    void findAllByClaimIdOrderByChangedAtDesc_returnsLogsMostRecentFirst() {
        // Use native SQL to control changed_at — @CreationTimestamp would overwrite an entity-supplied value.
        insertAudit("CLM-001", null, "SUBMITTED", LocalDateTime.of(2024, 6, 1, 10, 0));
        insertAudit("CLM-001", "SUBMITTED", "VALIDATED", LocalDateTime.of(2024, 6, 2, 11, 30));
        insertAudit("CLM-001", "VALIDATED", "APPROVED", LocalDateTime.of(2024, 6, 3, 9, 15));

        List<ClaimAuditLog> logs = auditLogRepository.findAllByClaimIdOrderByChangedAtDesc("CLM-001");

        assertThat(logs).hasSize(3);
        assertThat(logs)
                .extracting(ClaimAuditLog::getNewStatus)
                .containsExactly("APPROVED", "VALIDATED", "SUBMITTED");
    }

    @Test
    void findAllByClaimIdOrderByChangedAtDesc_returnsEmptyListWhenNoLogs() {
        List<ClaimAuditLog> logs = auditLogRepository.findAllByClaimIdOrderByChangedAtDesc("CLM-NONE");

        assertThat(logs).isEmpty();
    }

    @Test
    void findAllByClaimIdOrderByChangedAtDesc_filtersByClaimId() {
        insertAudit("CLM-A", null, "SUBMITTED", LocalDateTime.of(2024, 6, 1, 10, 0));
        insertAudit("CLM-B", null, "SUBMITTED", LocalDateTime.of(2024, 6, 1, 10, 5));
        insertAudit("CLM-A", "SUBMITTED", "REJECTED", LocalDateTime.of(2024, 6, 2, 12, 0));

        List<ClaimAuditLog> logs = auditLogRepository.findAllByClaimIdOrderByChangedAtDesc("CLM-A");

        assertThat(logs).hasSize(2);
        assertThat(logs)
                .extracting(ClaimAuditLog::getClaimId)
                .containsOnly("CLM-A");
    }

    private void insertAudit(String claimId, String previousStatus, String newStatus, LocalDateTime changedAt) {
        em.getEntityManager().createNativeQuery(
                "INSERT INTO claim_audit_log (claim_id, previous_status, new_status, changed_at, changed_by) " +
                        "VALUES (?, ?, ?, ?, 'TEST')")
                .setParameter(1, claimId)
                .setParameter(2, previousStatus)
                .setParameter(3, newStatus)
                .setParameter(4, changedAt)
                .executeUpdate();
        em.flush();
        em.clear();
    }
}
