package com.imaginit.hyperplux.repositories;

import android.util.Log;
import androidx.lifecycle.LiveData;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AssetRepository {
    private static final String TAG = "AssetRepository";

    private AssetDao assetDao;
    private UserDao userDao;
    private AssetTransactionDao transactionDao;
    private ExecutorService executor;
    private FirebaseFirestore firestore;

    // Constructor with only AssetDao (for backward compatibility)
    public AssetRepository(AssetDao assetDao) {
        this.assetDao = assetDao;
        this.executor = Executors.newFixedThreadPool(4);
        this.firestore = FirebaseFirestore.getInstance();
    }

    // Full constructor with all DAOs
    public AssetRepository(AssetDao assetDao, UserDao userDao, AssetTransactionDao transactionDao) {
        this.assetDao = assetDao;
        this.userDao = userDao;
        this.transactionDao = transactionDao;
        this.executor = Executors.newFixedThreadPool(4);
        this.firestore = FirebaseFirestore.getInstance();
    }

    // Get assets for current user
    public LiveData<List<Asset>> getAssetsByUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return assetDao.getAssetsByUser(user.getUid());
        }
        return null;
    }

    // Get all assets including hidden ones
    public LiveData<List<Asset>> getAllAssetsByUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return assetDao.getAllAssetsByUser(user.getUid());
        }
        return null;
    }

    // Get hidden assets
    public LiveData<List<Asset>> getHiddenAssetsByUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return assetDao.getHiddenAssetsByUser(user.getUid());
        }
        return null;
    }

    // Get assets for sale by current user
    public LiveData<List<Asset>> getAssetsForSale() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return assetDao.getAssetsForSale(user.getUid());
        }
        return null;
    }

    // Get all assets for sale on the marketplace
    public LiveData<List<Asset>> getAllAssetsForSale() {
        return assetDao.getAllAssetsForSale();
    }

    // Get top engaging assets feed
    public LiveData<List<Asset>> getTopAssets() {
        return assetDao.getTopAssets();
    }

    // Get feed from users being followed
    public LiveData<List<Asset>> getAssetsFromFollowing(List<String> followingIds) {
        return assetDao.getAssetsFromFollowing(followingIds);
    }

    // Search assets
    public LiveData<List<Asset>> searchAssets(String query) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return assetDao.searchAssets(query, user.getUid());
        }
        return null;
    }

    // Get assets by category
    public LiveData<List<Asset>> getAssetsByCategory(String category) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return assetDao.getAssetsByCategory(category, user.getUid());
        }
        return null;
    }

    // Get assets bequeathed to user
    public LiveData<List<Asset>> getBequeathedAssets() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return assetDao.getBequeathedAssets(user.getUid());
        }
        return null;
    }

    // Get assets by location
    public LiveData<List<Asset>> getAssetsInArea(double minLat, double maxLat, double minLong, double maxLong) {
        return assetDao.getAssetsInArea(minLat, maxLat, minLong, maxLong);
    }

    // Get top engaging assets for user
    public LiveData<List<Asset>> getTopEngagingAssets() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return assetDao.getTopEngagingAssets(user.getUid());
        }
        return null;
    }

    // Add new asset
    public void addAsset(Asset asset) {
        executor.execute(() -> {
            // Save to Room
            long id = assetDao.insert(asset);
            asset.setId((int) id);

            // Increment user's asset count
            if (userDao != null) {
                userDao.incrementAssetCount(asset.getUserId());
            }

            // Save to Firestore for cloud backup
            firestore.collection("assets")
                    .document(String.valueOf(id))
                    .set(asset)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Asset document saved to Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error saving asset document", e));
        });
    }

    // Update asset
    public void updateAsset(Asset asset) {
        executor.execute(() -> {
            // Update engagement score before saving
            asset.recalculateEngagementScore();

            // Save to Room
            assetDao.update(asset);

            // Save to Firestore
            firestore.collection("assets")
                    .document(String.valueOf(asset.getId()))
                    .set(asset)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Asset document updated in Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error updating asset document", e));
        });
    }

    // Delete asset
    public void deleteAsset(Asset asset) {
        executor.execute(() -> {
            // Delete from Room
            assetDao.delete(asset);

            // Decrement user's asset count
            if (userDao != null) {
                userDao.decrementAssetCount(asset.getUserId());
            }

            // Delete from Firestore
            firestore.collection("assets")
                    .document(String.valueOf(asset.getId()))
                    .delete()
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Asset document deleted from Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error deleting asset document", e));
        });
    }

    // Increment view count
    public void incrementViews(Asset asset) {
        executor.execute(() -> {
            // Increment in Room
            assetDao.incrementViews(asset.getId());

            // Update asset object
            asset.setViews(asset.getViews() + 1);

            // Increment owner's view count
            if (userDao != null) {
                userDao.incrementViews(asset.getUserId(), 1);
            }

            // Update in Firestore
            firestore.collection("assets")
                    .document(String.valueOf(asset.getId()))
                    .update("views", asset.getViews(),
                            "engagementScore", asset.getEngagementScore(),
                            "lastInteractionDate", asset.getLastInteractionDate());
        });
    }

    // Increment like count
    public void incrementLikes(Asset asset) {
        executor.execute(() -> {
            // Increment in Room
            assetDao.incrementLikes(asset.getId());

            // Update asset object
            asset.setLikes(asset.getLikes() + 1);

            // Increment owner's like count
            if (userDao != null) {
                userDao.incrementLikes(asset.getUserId(), 1);
            }

            // Update in Firestore
            firestore.collection("assets")
                    .document(String.valueOf(asset.getId()))
                    .update("likes", asset.getLikes(),
                            "engagementScore", asset.getEngagementScore(),
                            "lastInteractionDate", asset.getLastInteractionDate());
        });
    }

    // Increment dislike count
    public void incrementDislikes(Asset asset) {
        executor.execute(() -> {
            // Increment in Room
            assetDao.incrementDislikes(asset.getId());

            // Update asset object
            asset.setDislikes(asset.getDislikes() + 1);

            // Update in Firestore
            firestore.collection("assets")
                    .document(String.valueOf(asset.getId()))
                    .update("dislikes", asset.getDislikes(),
                            "engagementScore", asset.getEngagementScore(),
                            "lastInteractionDate", asset.getLastInteractionDate());
        });
    }

    // Increment share count
    public void incrementShares(Asset asset) {
        executor.execute(() -> {
            // Increment in Room
            assetDao.incrementShares(asset.getId());

            // Update asset object
            asset.setShares(asset.getShares() + 1);

            // Update in Firestore
            firestore.collection("assets")
                    .document(String.valueOf(asset.getId()))
                    .update("shares", asset.getShares(),
                            "engagementScore", asset.getEngagementScore(),
                            "lastInteractionDate", asset.getLastInteractionDate());
        });
    }

    // Increment comment count
    public void incrementComments(Asset asset) {
        executor.execute(() -> {
            // Increment in Room
            assetDao.incrementComments(asset.getId());

            // Update asset object
            asset.setComments(asset.getComments() + 1);

            // Update in Firestore
            firestore.collection("assets")
                    .document(String.valueOf(asset.getId()))
                    .update("comments", asset.getComments(),
                            "engagementScore", asset.getEngagementScore(),
                            "lastInteractionDate", asset.getLastInteractionDate());
        });
    }

    // Create a transaction between users
    public void createTransaction(int assetId, String fromUserId, String toUserId,
                                  String transactionType, double amount, String currency) {
        if (transactionDao == null) {
            Log.e(TAG, "Transaction DAO is null, cannot create transaction");
            return;
        }

        executor.execute(() -> {
            AssetTransaction transaction = new AssetTransaction(
                    assetId, fromUserId, toUserId, transactionType, amount, currency);

            // Save to Room
            long id = transactionDao.insert(transaction);
            transaction.setId((int) id);

            // Save to Firestore
            firestore.collection("transactions")
                    .document(String.valueOf(id))
                    .set(transaction)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Transaction saved to Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error saving transaction", e));
        });
    }

    // Complete a transaction (transfer ownership)
    public void completeTransaction(AssetTransaction transaction, Callback<Boolean> callback) {
        if (transactionDao == null) {
            Log.e(TAG, "Transaction DAO is null, cannot complete transaction");
            callback.onResult(false);
            return;
        }

        executor.execute(() -> {
            try {
                // Get the asset
                Asset asset = assetDao.getAssetByIdSync(transaction.getAssetId());
                if (asset == null) {
                    callback.onResult(false);
                    return;
                }

                // Update transaction status
                transaction.completeTransaction();
                transactionDao.update(transaction);

                // Transfer ownership based on transaction type
                if (transaction.getTransactionType().equals("SALE") ||
                        transaction.getTransactionType().equals("TRANSFER") ||
                        transaction.getTransactionType().equals("GIFT") ||
                        transaction.getTransactionType().equals("WILL")) {

                    // Change asset owner
                    asset.setUserId(transaction.getToUserId());
                    assetDao.update(asset);

                    // Update user asset counts
                    if (userDao != null) {
                        userDao.decrementAssetCount(transaction.getFromUserId());
                        userDao.incrementAssetCount(transaction.getToUserId());
                    }

                } else if (transaction.getTransactionType().equals("LOAN")) {
                    // Mark asset as loaned
                    asset.setLoanedOut(true);
                    asset.setLoanedTo(transaction.getToUserId());
                    asset.setLoanDate(transaction.getTransactionDate());
                    asset.setReturnDate(transaction.getLoanDueDate());
                    assetDao.update(asset);
                }

                // Update in Firestore
                firestore.collection("transactions")
                        .document(String.valueOf(transaction.getId()))
                        .set(transaction);
                firestore.collection("assets")
                        .document(String.valueOf(asset.getId()))
                        .set(asset);

                callback.onResult(true);
            } catch (Exception e) {
                Log.e(TAG, "Error completing transaction", e);
                callback.onResult(false);
            }
        });
    }

    // Get transactions for current user
    public LiveData<List<AssetTransaction>> getAllTransactionsForUser() {
        if (transactionDao == null) {
            Log.e(TAG, "Transaction DAO is null, cannot get transactions");
            return null;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return transactionDao.getAllTransactionsForUser(user.getUid());
        }
        return null;
    }

    // Get pending transactions
    public LiveData<List<AssetTransaction>> getPendingIncomingTransactions() {
        if (transactionDao == null) {
            Log.e(TAG, "Transaction DAO is null, cannot get transactions");
            return null;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return transactionDao.getPendingIncomingTransactions(user.getUid());
        }
        return null;
    }

    // Callback interface
    public interface Callback<T> {
        void onResult(T result);
    }
}
