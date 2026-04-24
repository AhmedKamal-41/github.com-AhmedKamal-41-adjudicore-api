package com.ahmedali.claimguard.repository;

import com.ahmedali.claimguard.domain.Provider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProviderRepositoryTest {

    @Autowired
    private ProviderRepository providerRepository;

    @Test
    void saveAndFindById_roundTripsAllFields() {
        Provider newProvider = Provider.builder()
                .npi("9999999999")
                .name("Test Clinic")
                .specialty("ORTHOPEDICS")
                .isInNetwork(true)
                .build();

        Provider saved = providerRepository.save(newProvider);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<Provider> found = providerRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get())
                .extracting(Provider::getNpi, Provider::getName, Provider::getSpecialty, Provider::getIsInNetwork)
                .containsExactly("9999999999", "Test Clinic", "ORTHOPEDICS", true);
    }

    @Test
    void findByNpi_returnsSeededProviderWhenPresent() {
        Optional<Provider> found = providerRepository.findByNpi("3456789012");

        assertThat(found).isPresent();
        assertThat(found.get())
                .extracting(Provider::getName, Provider::getSpecialty, Provider::getIsInNetwork)
                .containsExactly("Westside Cardiology", "CARDIOLOGY", true);
    }

    @Test
    void findByNpi_returnsEmptyWhenNoMatch() {
        Optional<Provider> found = providerRepository.findByNpi("0000000000");

        assertThat(found).isEmpty();
    }

    @Test
    void duplicateNpi_throwsDataIntegrityViolationException() {
        Provider duplicate = Provider.builder()
                .npi("1234567890")
                .name("Another Hospital")
                .specialty("EMERGENCY_MEDICINE")
                .isInNetwork(false)
                .build();

        assertThatThrownBy(() -> providerRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
