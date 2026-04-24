package com.ahmedali.claimguard.repository;

import com.ahmedali.claimguard.domain.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByNpi(String npi);
}
