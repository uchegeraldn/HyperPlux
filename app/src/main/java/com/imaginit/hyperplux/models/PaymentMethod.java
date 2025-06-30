package com.imaginit.hyperplux.models;

import java.util.Date;
import java.util.Objects;

/**
 * Model class for payment methods
 */
public class PaymentMethod {
    // Define the Type enum
    public enum Type {
        CREDIT_CARD,
        DEBIT_CARD,
        PAYPAL,
        BANK_ACCOUNT,
        CRYPTO,
        OTHER
    }

    private String id;
    private String userId;
    private Type type;  // Using the enum instead of String
    private String last4;
    private String identifier; // Additional identifier for non-card payment methods
    private String brand; // visa, mastercard, etc.
    private int expMonth;
    private int expYear;
    private Date createdAt;
    private boolean isDefault;

    public PaymentMethod() {
        // Required empty constructor for Firestore
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    // Legacy support for string type
    public void setTypeFromString(String typeString) {
        if (typeString == null) {
            this.type = Type.OTHER;
            return;
        }

        switch (typeString.toLowerCase()) {
            case "credit_card":
                this.type = Type.CREDIT_CARD;
                break;
            case "debit_card":
                this.type = Type.DEBIT_CARD;
                break;
            case "paypal":
                this.type = Type.PAYPAL;
                break;
            case "bank_account":
                this.type = Type.BANK_ACCOUNT;
                break;
            case "crypto":
                this.type = Type.CRYPTO;
                break;
            default:
                this.type = Type.OTHER;
        }
    }

    public String getLast4() {
        return last4;
    }

    // Alias for adapter compatibility
    public String getLastFourDigits() {
        return last4;
    }

    public void setLast4(String last4) {
        this.last4 = last4;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public int getExpMonth() {
        return expMonth;
    }

    // Alias for adapter compatibility
    public int getExpiryMonth() {
        return expMonth;
    }

    public void setExpMonth(int expMonth) {
        this.expMonth = expMonth;
    }

    public int getExpYear() {
        return expYear;
    }

    // Alias for adapter compatibility
    public int getExpiryYear() {
        return expYear;
    }

    public void setExpYear(int expYear) {
        this.expYear = expYear;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * Get a formatted display string for this payment method
     * @return Formatted string like "Visa •••• 4242"
     */
    public String getDisplayString() {
        return (brand != null ? brand : "Card") + " •••• " + (last4 != null ? last4 : "****");
    }

    /**
     * Get display name for adapter compatibility
     * @return Formatted display name
     */
    public String getDisplayName() {
        return getDisplayString();
    }

    /**
     * Get a formatted expiration date string
     * @return Formatted string like "09/2025"
     */
    public String getExpirationString() {
        return String.format("%02d/%d", expMonth, expYear);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentMethod that = (PaymentMethod) o;
        return expMonth == that.expMonth &&
                expYear == that.expYear &&
                isDefault == that.isDefault &&
                Objects.equals(id, that.id) &&
                Objects.equals(userId, that.userId) &&
                type == that.type &&
                Objects.equals(last4, that.last4) &&
                Objects.equals(identifier, that.identifier) &&
                Objects.equals(brand, that.brand) &&
                Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, type, last4, identifier, brand, expMonth, expYear,
                createdAt, isDefault);
    }
}