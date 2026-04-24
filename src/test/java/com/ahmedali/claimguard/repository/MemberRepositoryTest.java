package com.ahmedali.claimguard.repository;

import com.ahmedali.claimguard.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void saveAndFindById_roundTripsAllFields() {
        Member newMember = Member.builder()
                .memberId("M999")
                .firstName("Test")
                .lastName("User")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .planCode("PPO_GOLD")
                .eligibilityStart(LocalDate.of(2024, 1, 1))
                .build();

        Member saved = memberRepository.save(newMember);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<Member> found = memberRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get())
                .extracting(Member::getMemberId, Member::getFirstName, Member::getPlanCode)
                .containsExactly("M999", "Test", "PPO_GOLD");
    }

    @Test
    void findByMemberId_returnsSeededMemberWhenPresent() {
        Optional<Member> found = memberRepository.findByMemberId("M001");

        assertThat(found).isPresent();
        assertThat(found.get())
                .extracting(Member::getFirstName, Member::getLastName, Member::getPlanCode)
                .containsExactly("John", "Smith", "PPO_GOLD");
        assertThat(found.get().getEligibilityEnd()).isNull();
    }

    @Test
    void findByMemberId_returnsEmptyWhenNoMatch() {
        Optional<Member> found = memberRepository.findByMemberId("M_DOES_NOT_EXIST");

        assertThat(found).isEmpty();
    }

    @Test
    void duplicateMemberId_throwsDataIntegrityViolationException() {
        Member duplicate = Member.builder()
                .memberId("M001")
                .firstName("Different")
                .lastName("Person")
                .dateOfBirth(LocalDate.of(1995, 6, 15))
                .planCode("HMO_SILVER")
                .eligibilityStart(LocalDate.of(2025, 1, 1))
                .build();

        assertThatThrownBy(() -> memberRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
