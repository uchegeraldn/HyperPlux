package com.imaginit.hyperplux.utils;

import android.text.TextUtils;
import android.util.Patterns;

import java.util.Date;
import java.util.regex.Pattern;

/**
 * Utility class for input validation
 */
public class Validator {
    // Regular expressions for validation
    private static final Pattern EMAIL_PATTERN = Patterns.EMAIL_ADDRESS;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final Pattern PRICE_PATTERN = Pattern.compile("^\\d+(\\.\\d{1,2})?$");
    private static final Pattern URL_PATTERN = Patterns.WEB_URL;

    /**
     * Validate email address
     * @param email Email to validate
     * @return ValidationResult with success status and error message if applicable
     */
    public static ValidationResult validateEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            return new ValidationResult(false, "Email cannot be empty");
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return new ValidationResult(false, "Invalid email format");
        }

        return ValidationResult.SUCCESS;
    }

    /**
     * Validate password
     * @param password Password to validate
     * @return ValidationResult with success status and error message if applicable
     */
    public static ValidationResult validatePassword(String password) {
        if (TextUtils.isEmpty(password)) {
            return new ValidationResult(false, "Password cannot be empty");
        }

        if (password.length() < 6) {
            return new ValidationResult(false, "Password must be at least 6 characters");
        }

        return ValidationResult.SUCCESS;
    }

    /**
     * Validate phone number
     * @param phone Phone number to validate
     * @return ValidationResult with success status and error message if applicable
     */
    public static ValidationResult validatePhone(String phone) {
        if (TextUtils.isEmpty(phone)) {
            return ValidationResult.SUCCESS; // Phone is optional
        }

        if (!PHONE_PATTERN.matcher(phone).matches()) {
            return new ValidationResult(false, "Invalid phone number format");
        }

        return ValidationResult.SUCCESS;
    }

    /**
     * Validate asset name
     * @param name Asset name to validate
     * @return ValidationResult with success status and error message if applicable
     */
    public static ValidationResult validateAssetName(String name) {
        if (TextUtils.isEmpty(name)) {
            return new ValidationResult(false, "Asset name cannot be empty");
        }

        if (name.length() < 2 || name.length() > 100) {
            return new ValidationResult(false, "Asset name must be between 2 and 100 characters");
        }

        return ValidationResult.SUCCESS;
    }

    /**
     * Validate asset quantity
     * @param quantityStr Quantity string to validate
     * @return ValidationResult with success status and error message if applicable
     */
    public static ValidationResult validateQuantity(String quantityStr) {
        if (TextUtils.isEmpty(quantityStr)) {
            return ValidationResult.SUCCESS; // Default to 1
        }

        try {
            int quantity = Integer.parseInt(quantityStr);
            if (quantity <= 0) {
                return new ValidationResult(false, "Quantity must be greater than 0");
            }
        } catch (NumberFormatException e) {
            return new ValidationResult(false, "Invalid quantity format");
        }

        return ValidationResult.SUCCESS;
    }

    /**
     * Validate price
     * @param priceStr Price string to validate
     * @return ValidationResult with success status and error message if applicable
     */
    public static ValidationResult validatePrice(String priceStr) {
        if (TextUtils.isEmpty(priceStr)) {
            return ValidationResult.SUCCESS; // Default to 0
        }

        if (!PRICE_PATTERN.matcher(priceStr).matches()) {
            return new ValidationResult(false, "Invalid price format");
        }

        try {
            double price = Double.parseDouble(priceStr);
            if (price < 0) {
                return new ValidationResult(false, "Price cannot be negative");
            }
        } catch (NumberFormatException e) {
            return new ValidationResult(false, "Invalid price format");
        }

        return ValidationResult.SUCCESS;
    }

    /**
     * Validate URL
     * @param url URL to validate
     * @return ValidationResult with success status and error message if applicable
     */
    public static ValidationResult validateUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return ValidationResult.SUCCESS; // URL is optional
        }

        if (!URL_PATTERN.matcher(url).matches()) {
            return new ValidationResult(false, "Invalid URL format");
        }

        return ValidationResult.SUCCESS;
    }

    /**
     * Validate date
     * @param date Date to validate
     * @param allowFuture Whether future dates are allowed
     * @return ValidationResult with success status and error message if applicable
     */
    public static ValidationResult validateDate(Date date, boolean allowFuture) {
        if (date == null) {
            return ValidationResult.SUCCESS; // Date is optional
        }

        Date now = new Date();
        if (!allowFuture && date.after(now)) {
            return new ValidationResult(false, "Future dates are not allowed");
        }

        return ValidationResult.SUCCESS;
    }

    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean success;
        private final String errorMessage;

        public static final ValidationResult SUCCESS = new ValidationResult(true, null);

        public ValidationResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}