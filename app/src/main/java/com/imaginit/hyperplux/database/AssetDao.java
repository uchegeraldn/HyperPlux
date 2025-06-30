package com.imaginit.hyperplux.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.imaginit.hyperplux.models.Asset;

import java.util.List;

@Dao
public interface AssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Asset asset);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<Asset> assets);

    @Update
    void update(Asset asset);

    @Update
    void updateAll(List<Asset> assets);

    @Delete
    void delete(Asset asset);

    @Delete
    void deleteAll(List<Asset> assets);

    @Query("SELECT * FROM assets WHERE id = :id")
    LiveData<Asset> getAssetById(int id);

    @Query("SELECT * FROM assets WHERE id = :id")
    Asset getAssetByIdSync(int id);

    @Query("SELECT EXISTS(SELECT 1 FROM assets WHERE id = :id)")
    boolean assetExists(int id);

    // Basic user asset queries
    @Query("SELECT * FROM assets WHERE userId = :userId AND isHidden = 0 ORDER BY lastInteractionDate DESC")
    LiveData<List<Asset>> getAssetsByUser(String userId);

    @Query("SELECT * FROM assets WHERE userId = :userId")
    LiveData<List<Asset>> getAllAssetsByUser(String userId);

    @Query("SELECT * FROM assets WHERE userId = :userId")
    List<Asset> getAllAssetsByUserSync(String userId);

    @Query("SELECT * FROM assets WHERE userId = :userId AND isHidden = 1")
    LiveData<List<Asset>> getHiddenAssetsByUser(String userId);

    // Social feed queries
    @Query("SELECT * FROM assets WHERE isShared = 1 AND isHidden = 0 ORDER BY engagementScore DESC LIMIT 50")
    LiveData<List<Asset>> getTopAssets();

    @Query("SELECT * FROM assets WHERE userId IN (:userIds) AND isHidden = 0 AND isShared = 1 ORDER BY lastInteractionDate DESC LIMIT 100")
    LiveData<List<Asset>> getAssetsFromFollowing(List<String> userIds);

    @Query("SELECT * FROM assets WHERE userId = :userId AND isForSale = 1 AND isHidden = 0")
    LiveData<List<Asset>> getAssetsForSale(String userId);

    @Query("SELECT * FROM assets WHERE isForSale = 1 AND isHidden = 0 ORDER BY lastInteractionDate DESC")
    LiveData<List<Asset>> getAllAssetsForSale();

    // Search queries with improved indexing
    @Query("SELECT * FROM assets WHERE name LIKE '%' || :query || '%' AND isHidden = 0 AND (isShared = 1 OR userId = :currentUserId) ORDER BY lastInteractionDate DESC")
    LiveData<List<Asset>> searchAssets(String query, String currentUserId);

    @Query("SELECT * FROM assets WHERE category = :category AND isHidden = 0 AND (isShared = 1 OR userId = :currentUserId) ORDER BY lastInteractionDate DESC")
    LiveData<List<Asset>> getAssetsByCategory(String category, String currentUserId);

    // Will-related queries
    @Query("SELECT * FROM assets WHERE heirId = :heirId AND isBequest = 1")
    LiveData<List<Asset>> getBequeathedAssets(String heirId);

    // Stats and metrics
    @Query("SELECT COUNT(*) FROM assets WHERE userId = :userId")
    int getAssetCount(String userId);

    @Query("SELECT SUM(currentValue) FROM assets WHERE userId = :userId")
    double getTotalAssetValue(String userId);

    @Query("SELECT * FROM assets WHERE userId = :userId ORDER BY engagementScore DESC LIMIT 5")
    LiveData<List<Asset>> getTopEngagingAssets(String userId);

    // Location-based queries
    @Query("SELECT * FROM assets WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLong AND :maxLong AND isHidden = 0 AND isShared = 1 ORDER BY lastInteractionDate DESC")
    LiveData<List<Asset>> getAssetsInArea(double minLat, double maxLat, double minLong, double maxLong);

    // Increment metrics
    @Query("UPDATE assets SET views = views + 1, lastInteractionDate = CURRENT_TIMESTAMP WHERE id = :assetId")
    void incrementViews(int assetId);

    @Query("UPDATE assets SET likes = likes + 1, lastInteractionDate = CURRENT_TIMESTAMP WHERE id = :assetId")
    void incrementLikes(int assetId);

    @Query("UPDATE assets SET dislikes = dislikes + 1, lastInteractionDate = CURRENT_TIMESTAMP WHERE id = :assetId")
    void incrementDislikes(int assetId);

    @Query("UPDATE assets SET shares = shares + 1, lastInteractionDate = CURRENT_TIMESTAMP WHERE id = :assetId")
    void incrementShares(int assetId);

    @Query("UPDATE assets SET comments = comments + 1, lastInteractionDate = CURRENT_TIMESTAMP WHERE id = :assetId")
    void incrementComments(int assetId);

    // Bulk operations
    @Transaction
    @Query("UPDATE assets SET engagementScore = (views * 0.5 + likes * 2 + comments * 3 + shares * 5 - dislikes) WHERE userId = :userId")
    void recalculateAllEngagementScores(String userId);

    // Delete operations
    @Query("DELETE FROM assets WHERE userId = :userId")
    void deleteAllUserAssets(String userId);

    // Sale status operations
    @Query("UPDATE assets SET isForSale = :isForSale, price = :price, currency = :currency, lastInteractionDate = CURRENT_TIMESTAMP WHERE id = :assetId")
    void updateSaleStatus(int assetId, boolean isForSale, double price, String currency);

    // Location updates
    @Query("UPDATE assets SET latitude = :latitude, longitude = :longitude, locationName = :locationName WHERE id = :assetId")
    void updateLocation(int assetId, double latitude, double longitude, String locationName);

    // Return count for pagination
    @Query("SELECT COUNT(*) FROM assets WHERE userId = :userId AND isHidden = 0")
    int getVisibleAssetCount(String userId);
}