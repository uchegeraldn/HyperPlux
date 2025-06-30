package com.imaginit.hyperplux.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

import com.imaginit.hyperplux.models.User;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(User user);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertAndGetId(User user);

    @Update
    void update(User user);

    @Delete
    void delete(User user);

    @Query("SELECT * FROM users WHERE uid = :uid")
    LiveData<User> getUserById(String uid);

    @Query("SELECT * FROM users WHERE uid = :uid")
    User getUserByIdSync(String uid);

    @Query("SELECT * FROM users")
    LiveData<List<User>> getAllUsers();

    @Query("SELECT * FROM users WHERE displayName LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%'")
    LiveData<List<User>> searchUsers(String query);

    @Query("SELECT * FROM users WHERE uid IN (:userIds)")
    LiveData<List<User>> getUsersByIds(List<String> userIds);

    @Query("SELECT * FROM users WHERE uid IN (:userIds)")
    List<User> getUsersByIdsSync(List<String> userIds);

    @Query("SELECT COUNT(*) FROM users")
    int getUserCount();

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE uid = :userId)")
    boolean userExists(String userId);

    @Query("UPDATE users SET totalAssets = totalAssets + 1 WHERE uid = :userId")
    void incrementAssetCount(String userId);

    @Query("UPDATE users SET totalAssets = totalAssets - 1 WHERE uid = :userId")
    void decrementAssetCount(String userId);

    @Query("UPDATE users SET totalLikes = totalLikes + :amount WHERE uid = :userId")
    void incrementLikes(String userId, int amount);

    @Query("UPDATE users SET totalViews = totalViews + :amount WHERE uid = :userId")
    void incrementViews(String userId, int amount);

    @Query("UPDATE users SET overallEngagementScore = :score WHERE uid = :userId")
    void updateEngagementScore(String userId, double score);

    @Query("UPDATE users SET lastLoginDate = :timestamp WHERE uid = :userId")
    void updateLastLogin(String userId, long timestamp);
}