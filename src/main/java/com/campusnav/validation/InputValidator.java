package com.campusnav.validation;

import java.util.Locale;

public final class InputValidator {
    private InputValidator() { }

    public static String canonicalId(String value, String field) {
        String text = requiredText(value, field).toUpperCase(Locale.ROOT);
        if (!text.matches("[A-Z0-9][A-Z0-9_-]{1,19}")) {
            throw new IllegalArgumentException(field
                    + " must be 2-20 characters using letters, numbers, underscore, or hyphen.");
        }
        return text;
    }

    public static String requiredText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value.trim();
    }

    public static int positiveDistance(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Distance must be greater than zero metres.");
        }
        return value;
    }
}
