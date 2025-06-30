package com.imaginit.hyperplux.models;

import java.util.Date;
import java.util.Locale;

/**
 * Model class for payment transactions
 */
public class PaymentTransaction {
    private String id;
    private String buyerId;
    private String sellerId;
    private String assetId;
    private long amount;
    private String currency;
    private String type;
    private String status;
    private Date createdAt;
    private Date updatedAt;
    private String paymentIntentId;
    private String paymentMethodId;
    private int credits;
    private String subscriptionId;
    private String subscriptionPlan;
    private Date subscriptionStartDate;
    private Date subscriptionEndDate;
    private String failureReason;

    public PaymentTransaction() {
        // Required empty constructor for Firestore
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(String paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getSubscriptionPlan() {
        return subscriptionPlan;
    }

    public void setSubscriptionPlan(String subscriptionPlan) {
        this.subscriptionPlan = subscriptionPlan;
    }

    public Date getSubscriptionStartDate() {
        return subscriptionStartDate;
    }

    public void setSubscriptionStartDate(Date subscriptionStartDate) {
        this.subscriptionStartDate = subscriptionStartDate;
    }

    public Date getSubscriptionEndDate() {
        return subscriptionEndDate;
    }

    public void setSubscriptionEndDate(Date subscriptionEndDate) {
        this.subscriptionEndDate = subscriptionEndDate;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    /**
     * Get formatted amount string with currency symbol
     * @return Formatted amount (e.g., "$49.99")
     */
    public String getFormattedAmount() {
        double amountInUnits = amount / 100.0; // Convert cents to units

        if (currency == null || currency.isEmpty()) {
            return String.format(Locale.getDefault(), "%.2f", amountInUnits);
        }

        switch (currency.toUpperCase()) {
            case "USD":
                return String.format(Locale.US, "$%.2f", amountInUnits);
            case "EUR":
                return String.format(Locale.GERMANY, "€%.2f", amountInUnits);
            case "GBP":
                return String.format(Locale.UK, "£%.2f", amountInUnits);
            default:
                return String.format(Locale.getDefault(), "%.2f %s", amountInUnits, currency.toUpperCase());
        }
    }

    /**
     * Get transaction type display name
     * @return Human-readable transaction type
     */
    public String getTypeDisplayName() {
        if (type == null) return "Unknown";

        switch (type) {
            case "asset_purchase":
                return "Asset Purchase";
            case "subscription":
                return "Subscription";
            case "credit_purchase":
                return "Credits Purchase";
            default:
                return type;
        }
    }

    /**
     * Get transaction status display name
     * @return Human-readable transaction status
     */
    public String getStatusDisplayName() {
        if (status == null) return "Unknown";

        switch (status) {
            case "pending":
                return "Pending";
            case "completed":
                return "Completed";
            case "failed":
                return "Failed";
            case "refunded":
                return "Refunded";
            default:
                return status;
        }
    }
}