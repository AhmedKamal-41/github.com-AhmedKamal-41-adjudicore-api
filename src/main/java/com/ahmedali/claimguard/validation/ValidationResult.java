package com.ahmedali.claimguard.validation;

import java.util.List;

public sealed interface ValidationResult
        permits ValidationResult.Valid, ValidationResult.Invalid {

    static ValidationResult valid() {
        return new Valid();
    }

    static ValidationResult invalid(String rejectCode, String message) {
        return new Invalid(List.of(rejectCode), List.of(message));
    }

    static ValidationResult invalid(List<String> rejectCodes, List<String> messages) {
        return new Invalid(List.copyOf(rejectCodes), List.copyOf(messages));
    }

    boolean isValid();

    List<String> rejectCodes();

    List<String> messages();

    record Valid() implements ValidationResult {
        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public List<String> rejectCodes() {
            return List.of();
        }

        @Override
        public List<String> messages() {
            return List.of();
        }
    }

    record Invalid(List<String> rejectCodes, List<String> messages) implements ValidationResult {
        public Invalid {
            rejectCodes = List.copyOf(rejectCodes);
            messages = List.copyOf(messages);
        }

        @Override
        public boolean isValid() {
            return false;
        }
    }
}
