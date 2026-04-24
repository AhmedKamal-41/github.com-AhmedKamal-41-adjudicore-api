package com.ahmedali.claimguard.adjudication;

import java.math.BigDecimal;
import java.util.List;

public sealed interface AdjudicationDecision
        permits AdjudicationDecision.Approve,
        AdjudicationDecision.Deny,
        AdjudicationDecision.Pend {

    enum Outcome {APPROVE, DENY, PEND}

    Outcome outcome();

    List<String> reasonCodes();

    String notes();

    static AdjudicationDecision approve(BigDecimal allowedAmount, String notes) {
        return new Approve(allowedAmount, notes);
    }

    static AdjudicationDecision deny(String code, String notes) {
        return new Deny(List.of(code), notes);
    }

    static AdjudicationDecision pend(String code, String notes) {
        return new Pend(List.of(code), notes);
    }

    record Approve(BigDecimal allowedAmount, String notes) implements AdjudicationDecision {
        @Override
        public Outcome outcome() {
            return Outcome.APPROVE;
        }

        @Override
        public List<String> reasonCodes() {
            return List.of();
        }
    }

    record Deny(List<String> reasonCodes, String notes) implements AdjudicationDecision {
        public Deny {
            reasonCodes = List.copyOf(reasonCodes);
        }

        @Override
        public Outcome outcome() {
            return Outcome.DENY;
        }
    }

    record Pend(List<String> reasonCodes, String notes) implements AdjudicationDecision {
        public Pend {
            reasonCodes = List.copyOf(reasonCodes);
        }

        @Override
        public Outcome outcome() {
            return Outcome.PEND;
        }
    }
}
