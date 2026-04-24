package com.ahmedali.claimguard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "claims")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 30)
    private String claimId;

    @Column(nullable = false, length = 20)
    private String memberId;

    @Column(nullable = false, length = 10)
    private String providerNpi;

    @Column(nullable = false)
    private LocalDate serviceDate;

    @Column(nullable = false)
    private LocalDate submissionDate;

    @Column(nullable = false, length = 10)
    private String procedureCode;

    @Column(nullable = false, length = 10)
    private String diagnosisCode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal billedAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal allowedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Claim other)) return false;
        return claimId != null && Objects.equals(claimId, other.claimId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(claimId);
    }
}
