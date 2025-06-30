package com.imaginit.hyperplux.models;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.imaginit.hyperplux.database.DateConverter;

import java.util.Date;

@Entity(tableName = "asset_transactions",
        indices = {@Index("assetId"), @Index("fromUserId"), @Index("toUserId")})
@TypeConverters(DateConverter.class)
public class AssetTransaction {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int assetId;
    private String fromUserId;
    private String toUserId;
    private Date transactionDate;
    private String transactionType; // "SALE", "TRANSFER", "LOAN", "RETURN", "GIFT", "WILL"
    private double transactionAmount;
    private String currency;
    private String status; // "PENDING", "COMPLETED", "REJECTED", "CANCELLED"
    private String notes;

    // Loan-specific fields
    private Date loanDueDate;
    private boolean isReturned;
    private Date returnDate;

    // Constructor
    public AssetTransaction(int assetId, String fromUserId, String toUserId,
                            String transactionType, double transactionAmount, String currency) {
        this.assetId = assetId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.transactionDate = new Date();
        this.transactionType = transactionType;
        this.transactionAmount = transactionAmount;
        this.currency = currency;
        this.status = "PENDING";
        this.isReturned = false;
    }

    // Default constructor for Room
    public AssetTransaction() {
        // Required empty constructor
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAssetId() { return assetId; }
    public void setAssetId(int assetId) { this.assetId = assetId; }

    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }

    public String getToUserId() { return toUserId; }
    public void setToUserId(String toUserId) { this.toUserId = toUserId; }

    public Date getTransactionDate() { return transactionDate; }
    public void setTransactionDate(Date transactionDate) { this.transactionDate = transactionDate; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public double getTransactionAmount() { return transactionAmount; }
    public void setTransactionAmount(double transactionAmount) { this.transactionAmount = transactionAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Date getLoanDueDate() { return loanDueDate; }
    public void setLoanDueDate(Date loanDueDate) { this.loanDueDate = loanDueDate; }

    public boolean isReturned() { return isReturned; }
    public void setReturned(boolean returned) { isReturned = returned; }

    public Date getReturnDate() { return returnDate; }
    public void setReturnDate(Date returnDate) { this.returnDate = returnDate; }

    // Helper method to complete a transaction
    public void completeTransaction() {
        this.status = "COMPLETED";
        this.transactionDate = new Date();
    }

    // Helper method to reject a transaction
    public void rejectTransaction(String notes) {
        this.status = "REJECTED";
        this.notes = notes != null ? notes : "";
    }

    // Helper method to cancel a transaction
    public void cancelTransaction(String notes) {
        this.status = "CANCELLED";
        this.notes = notes;
    }

    // Helper method to mark a loan as returned
    public void markAsReturned() {
        this.isReturned = true;
        this.returnDate = new Date();
        if (this.transactionType.equals("LOAN")) {
            this.status = "COMPLETED";
        }
    }
}