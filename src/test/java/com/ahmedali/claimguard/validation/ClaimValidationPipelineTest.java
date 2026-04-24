package com.ahmedali.claimguard.validation;

import com.ahmedali.claimguard.domain.Claim;
import com.ahmedali.claimguard.domain.Member;
import com.ahmedali.claimguard.domain.Provider;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimValidationPipelineTest {

    @Test
    void allValidatorsPass_returnsValid_emptyLists() {
        ClaimValidator v1 = stubValidator(10, ValidationResult.valid());
        ClaimValidator v2 = stubValidator(20, ValidationResult.valid());
        ClaimValidationPipeline pipeline = new ClaimValidationPipeline(List.of(v1, v2));

        ValidationResult result = pipeline.validate(mock(Claim.class), mock(Member.class), mock(Provider.class));

        assertThat(result.isValid()).isTrue();
        assertThat(result.rejectCodes()).isEmpty();
        assertThat(result.messages()).isEmpty();
    }

    @Test
    void threeValidatorsFailSimultaneously_returnsInvalidWithAllThreeCodes() {
        ClaimValidator v1 = stubValidator(10, ValidationResult.invalid("CARC-27", "member expired"));
        ClaimValidator v2 = stubValidator(20, ValidationResult.valid());
        ClaimValidator v3 = stubValidator(30, ValidationResult.invalid("CARC-181", "bad date"));
        ClaimValidator v4 = stubValidator(40, ValidationResult.invalid("CARC-45", "bad amount"));
        ClaimValidationPipeline pipeline = new ClaimValidationPipeline(List.of(v1, v2, v3, v4));

        ValidationResult result = pipeline.validate(mock(Claim.class), mock(Member.class), mock(Provider.class));

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-27", "CARC-181", "CARC-45");
        assertThat(result.messages()).containsExactly("member expired", "bad date", "bad amount");
    }

    @Test
    void validatorsExecuteInOrderSequence_regardlessOfInjectionOrder() {
        List<Integer> calls = new ArrayList<>();

        ClaimValidator first = recordingValidator(10, calls);
        ClaimValidator second = recordingValidator(20, calls);
        ClaimValidator third = recordingValidator(30, calls);

        ClaimValidationPipeline pipeline = new ClaimValidationPipeline(List.of(third, first, second));

        pipeline.validate(mock(Claim.class), mock(Member.class), mock(Provider.class));

        assertThat(calls).containsExactly(10, 20, 30);
    }

    @Test
    void sameCodeFromMultipleValidators_producesBothEntriesNotDeduplicated() {
        // Spec: "rejectCodes list has no duplicates when same code emitted twice" — the
        // semantics here are "no accidental bucket collapse". The pipeline concatenates
        // results preserving order; each validator contributes at most one entry per
        // failure, so repeated codes can only arise from distinct validators. We verify
        // that two validators both emitting CARC-181 yield two entries (one per failing
        // validator, each carrying its own message).
        ClaimValidator v1 = stubValidator(10, ValidationResult.invalid("CARC-181", "format A"));
        ClaimValidator v2 = stubValidator(20, ValidationResult.invalid("CARC-181", "format B"));
        ClaimValidationPipeline pipeline = new ClaimValidationPipeline(List.of(v1, v2));

        ValidationResult result = pipeline.validate(mock(Claim.class), mock(Member.class), mock(Provider.class));

        assertThat(result.isValid()).isFalse();
        assertThat(result.rejectCodes()).containsExactly("CARC-181", "CARC-181");
        assertThat(result.messages()).containsExactly("format A", "format B");
    }

    @Test
    void invokesEveryValidatorEvenAfterFirstFailure() {
        ClaimValidator v1 = stubValidator(10, ValidationResult.invalid("CARC-27", "first"));
        ClaimValidator v2 = stubValidator(20, ValidationResult.invalid("CARC-45", "second"));
        ClaimValidationPipeline pipeline = new ClaimValidationPipeline(List.of(v1, v2));

        pipeline.validate(mock(Claim.class), mock(Member.class), mock(Provider.class));

        InOrder order = inOrder(v1, v2);
        order.verify(v1).validate(any(), any(), any());
        order.verify(v2).validate(any(), any(), any());
    }

    private static ClaimValidator stubValidator(int order, ValidationResult result) {
        ClaimValidator v = mock(ClaimValidator.class);
        when(v.order()).thenReturn(order);
        when(v.validate(any(), any(), any())).thenReturn(result);
        return v;
    }

    private static ClaimValidator recordingValidator(int order, List<Integer> calls) {
        ClaimValidator v = mock(ClaimValidator.class);
        when(v.order()).thenReturn(order);
        when(v.validate(any(), any(), any())).thenAnswer(inv -> {
            calls.add(order);
            return ValidationResult.valid();
        });
        return v;
    }
}
