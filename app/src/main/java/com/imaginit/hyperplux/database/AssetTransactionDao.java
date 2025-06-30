package com.imaginit.hyperplux.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.imaginit.hyperplux.models.AssetTransaction;

import java.util.List;
import java.util.Date;

@Dao
public interface AssetTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AssetTransaction transaction);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<AssetTransaction> transactions);

    @Update
    void update(AssetTransaction transaction);

    @Delete
    void delete(AssetTransaction transaction);

    @Query("SELECT * FROM asset_transactions WHERE id = :id")
    LiveData<AssetTransaction> getTransactionById(int id);

    @Query("SELECT * FROM asset_transactions WHERE id = :id")
    AssetTransaction getTransactionByIdSync(int id);

    // Outgoing transactions (user is sender)
    @Query("SELECT * FROM asset_transactions WHERE fromUserId = :userId ORDER BY transactionDate DESC")
    LiveData<List<AssetTransaction>> getOutgoingTransactions(String userId);

    @Query("SELECT * FROM asset_transactions WHERE fromUserId = :userId ORDER BY transactionDate DESC")
    List<AssetTransaction> getOutgoingTransactionsSync(String userId);

    // Incoming transactions (user is receiver)
    @Query("SELECT * FROM asset_transactions WHERE toUserId = :userId ORDER BY transactionDate DESC")
    LiveData<List<AssetTransaction>> getIncomingTransactions(String userId);

    @Query("SELECT * FROM asset_transactions WHERE toUserId = :userId ORDER BY transactionDate DESC")
    List<AssetTransaction> getIncomingTransactionsSync(String userId);

    // All transactions for a user (either sender or receiver)
    @Query("SELECT * FROM asset_transactions WHERE fromUserId = :userId OR toUserId = :userId ORDER BY transactionDate DESC")
    LiveData<List<AssetTransaction>> getAllTransactionsForUser(String userId);

    @Query("SELECT * FROM asset_transactions WHERE fromUserId = :userId OR toUserId = :userId ORDER BY transactionDate DESC")
    List<AssetTransaction> getAllTransactionsForUserSync(String userId);

    // Transactions by status
    @Query("SELECT * FROM asset_transactions WHERE (fromUserId = :userId OR toUserId = :userId) AND status = :status ORDER BY transactionDate DESC")
    LiveData<List<AssetTransaction>> getTransactionsByStatus(String userId, String status);

    @Query("SELECT * FROM asset_transactions WHERE (fromUserId = :userId OR toUserId = :userId) AND status = :status ORDER BY transactionDate DESC")
    List<AssetTransaction> getTransactionsByStatusSync(String userId, String status);

    // Transactions for a specific asset
    @Query("SELECT * FROM asset_transactions WHERE assetId = :assetId ORDER BY transactionDate DESC")
    LiveData<List<AssetTransaction>> getTransactionsForAsset(int assetId);

    @Query("SELECT * FROM asset_transactions WHERE assetId = :assetId ORDER BY transactionDate DESC")
    List<AssetTransaction> getTransactionsForAssetSync(int assetId);

    // Active loans (not returned)
    @Query("SELECT * FROM asset_transactions WHERE transactionType = 'LOAN' AND isReturned = 0 AND (fromUserId = :userId OR toUserId = :userId)")
    LiveData<List<AssetTransaction>> getActiveLoans(String userId);

    @Query("SELECT * FROM asset_transactions WHERE transactionType = 'LOAN' AND isReturned = 0 AND (fromUserId = :userId OR toUserId = :userId)")
    List<AssetTransaction> getActiveLoansSync(String userId);

    // Transactions by type
    @Query("SELECT * FROM asset_transactions WHERE (fromUserId = :userId OR toUserId = :userId) AND transactionType = :type ORDER BY transactionDate DESC")
    LiveData<List<AssetTransaction>> getTransactionsByType(String userId, String type);

    @Query("SELECT * FROM asset_transactions WHERE (fromUserId = :userId OR toUserId = :userId) AND transactionType = :type ORDER BY transactionDate DESC")
    List<AssetTransaction> getTransactionsByTypeSync(String userId, String type);

    // Pending transactions that require user action
    @Query("SELECT * FROM asset_transactions WHERE toUserId = :userId AND status = 'PENDING'")
    LiveData<List<AssetTransaction>> getPendingIncomingTransactions(String userId);

    @Query("SELECT * FROM asset_transactions WHERE toUserId = :userId AND status = 'PENDING'")
    List<AssetTransaction> getPendingIncomingTransactionsSync(String userId);

    @Query("SELECT * FROM asset_transactions WHERE fromUserId = :userId AND status = 'PENDING'")
    LiveData<List<AssetTransaction>> getPendingOutgoingTransactions(String userId);

    @Query("SELECT * FROM asset_transactions WHERE fromUserId = :userId AND status = 'PENDING'")
    List<AssetTransaction> getPendingOutgoingTransactionsSync(String userId);

    // Will-related transactions
    @Query("SELECT * FROM asset_transactions WHERE transactionType = 'WILL' AND toUserId = :userId")
    LiveData<List<AssetTransaction>> getWillTransactions(String userId);

    @Query("SELECT * FROM asset_transactions WHERE transactionType = 'WILL' AND toUserId = :userId")
    List<AssetTransaction> getWillTransactionsSync(String userId);

    // Transaction metrics
    @Query("SELECT COUNT(*) FROM asset_transactions WHERE (fromUserId = :userId OR toUserId = :userId) AND status = 'COMPLETED'")
    int getCompletedTransactionCount(String userId);

    @Query("SELECT SUM(transactionAmount) FROM asset_transactions WHERE fromUserId = :userId AND status = 'COMPLETED' AND transactionType = 'SALE'")
    double getTotalSalesValue(String userId);

    // Additional useful methods
    @Query("UPDATE asset_transactions SET status = :newStatus WHERE id = :transactionId")
    void updateTransactionStatus(int transactionId, String newStatus);

    @Query("UPDATE asset_transactions SET isReturned = 1, returnDate = :returnDate WHERE id = :transactionId AND transactionType = 'LOAN'")
    void markLoanReturned(int transactionId, Date returnDate);

    @Transaction
    @Query("DELETE FROM asset_transactions WHERE (fromUserId = :userId OR toUserId = :userId) AND status = :status")
    void deleteTransactionsByStatus(String userId, String status);

    @Query("SELECT EXISTS(SELECT 1 FROM asset_transactions WHERE assetId = :assetId AND status != 'COMPLETED' AND status != 'CANCELLED')")
    boolean hasPendingTransactions(int assetId);

    @Query("SELECT COUNT(*) FROM asset_transactions WHERE (fromUserId = :userId OR toUserId = :userId) AND transactionDate > :startDate")
    int getTransactionCountSince(String userId, Date startDate);
}