package com.imaginit.hyperplux.repositories;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.imaginit.hyperplux.database.UserDao;
import com.imaginit.hyperplux.models.User;

public class UserRepository {
    private static final String TAG = "UserRepository";

    private UserDao userDao;
    private ExecutorService executor;
    private FirebaseFirestore firestore;

    public UserRepository(UserDao userDao) {
        this.userDao = userDao;
        this.executor = Executors.newFixedThreadPool(4);
        this.firestore = FirebaseFirestore.getInstance();
    }

    /**
     * Get current user from Room database
     * @return LiveData object containing the current user or null
     */
    public LiveData<User> getCurrentUser() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            return userDao.getUserById(currentUser.getUid());
        } else {
            return new MutableLiveData<>(null);
        }
    }

    /**
     * Refresh current user data from Firebase and update local database
     */
    public void refreshCurrentUser() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Log.d(TAG, "Cannot refresh user: No authenticated user");
            return;
        }

        String uid = firebaseUser.getUid();

        // First check if we have the user in the local database
        executor.execute(() -> {
            try {
                User localUser = userDao.getUserByIdSync(uid);

                // If local user doesn't exist, create a new one
                if (localUser == null) {
                    Log.d(TAG, "Creating new user in local database");
                    User newUser = new User(uid, firebaseUser.getEmail());

                    if (firebaseUser.getDisplayName() != null) {
                        newUser.setDisplayName(firebaseUser.getDisplayName());
                    }

                    if (firebaseUser.getPhoneNumber() != null) {
                        newUser.setPhoneNumber(firebaseUser.getPhoneNumber());
                    }

                    userDao.insert(newUser);

                    // Also save to Firestore
                    FirebaseErrorHandler.executeWithRetry(() -> {
                        firestore.collection("users")
                                .document(uid)
                                .set(newUser, SetOptions.merge());
                    }, 3, null);
                } else {
                    // Update last login date
                    localUser.setLastLoginDate(new java.util.Date());
                    userDao.update(localUser);

                    // Sync with Firestore
                    FirebaseErrorHandler.executeWithRetry(() -> {
                        firestore.collection("users")
                                .document(uid)
                                .update("lastLoginDate", localUser.getLastLoginDate());
                    }, 3, null);
                }

                // Now check if we need to sync from Firestore (full refresh)
                FirebaseErrorHandler.executeWithRetry(() -> {
                    firestore.collection("users").document(uid).get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    User firestoreUser = documentSnapshot.toObject(User.class);
                                    if (firestoreUser != null) {
                                        executor.execute(() -> {
                                            // Update local database with Firestore data
                                            userDao.update(firestoreUser);
                                            Log.d(TAG, "User synced from Firestore");
                                        });
                                    }
                                }
                            })
                            .addOnFailureListener(e ->
                                    Log.e(TAG, "Failed to fetch user from Firestore", e)
                            );
                }, 3, null);

            } catch (Exception e) {
                Log.e(TAG, "Error refreshing current user", e);
            }
        });
    }

    /**
     * Get user by ID from Room
     * @param userId ID of the user to fetch
     * @return LiveData object containing the user
     */
    public LiveData<User> getUserById(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "getUserById: Invalid user ID");
            return new MutableLiveData<>(null);
        }
        return userDao.getUserById(userId);
    }

    /**
     * Get user synchronously with callback
     * @param userId ID of the user to fetch
     * @param callback Callback to return the result
     */
    public void getUserByIdSync(String userId, final Callback<User> callback) {
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "getUserByIdSync: Invalid user ID");
            if (callback != null) {
                callback.onResult(null);
            }
            return;
        }

        executor.execute(() -> {
            try {
                User user = userDao.getUserByIdSync(userId);
                if (callback != null) {
                    callback.onResult(user);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting user by ID", e);
                if (callback != null) {
                    callback.onResult(null);
                }
            }
        });
    }

    /**
     * Search users by query string
     * @param query The search query
     * @return LiveData list of matching users
     */
    public LiveData<List<User>> searchUsers(String query) {
        if (query == null) {
            Log.e(TAG, "searchUsers: Query is null");
            return new MutableLiveData<>(new ArrayList<>());
        }
        return userDao.searchUsers(query);
    }

    /**
     * Get user's followers
     * @param followerIds List of follower user IDs
     * @return LiveData list of follower users
     */
    public LiveData<List<User>> getFollowers(List<String> followerIds) {
        if (followerIds == null || followerIds.isEmpty()) {
            Log.d(TAG, "getFollowers: No follower IDs provided");
            return new MutableLiveData<>(new ArrayList<>());
        }
        return userDao.getUsersByIds(followerIds);
    }

    /**
     * Get users that the current user is following
     * @param followingIds List of following user IDs
     * @return LiveData list of following users
     */
    public LiveData<List<User>> getFollowing(List<String> followingIds) {
        if (followingIds == null || followingIds.isEmpty()) {
            Log.d(TAG, "getFollowing: No following IDs provided");
            return new MutableLiveData<>(new ArrayList<>());
        }
        return userDao.getUsersByIds(followingIds);
    }

    /**
     * Create or update user in both local database and Firestore
     * @param user The user to create or update
     */
    public void createOrUpdateUser(User user) {
        if (user == null) {
            Log.e(TAG, "createOrUpdateUser: User is null");
            return;
        }

        executor.execute(() -> {
            try {
                // Save to Room
                userDao.insert(user);

                // Save to Firestore with retry logic
                FirebaseErrorHandler.executeWithRetry(() -> {
                    firestore.collection("users")
                            .document(user.getUid())
                            .set(user, SetOptions.merge());
                }, 3, new FirebaseErrorHandler.Callback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "User document saved to Firestore");
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Error saving user document to Firestore", e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error creating or updating user", e);
            }
        });
    }

    /**
     * Follow a user
     * @param currentUserId ID of the current user
     * @param targetUserId ID of the user to follow
     */
    public void followUser(String currentUserId, String targetUserId) {
        if (currentUserId == null || targetUserId == null ||
                currentUserId.isEmpty() || targetUserId.isEmpty() ||
                currentUserId.equals(targetUserId)) {
            Log.e(TAG, "followUser: Invalid user IDs");
            return;
        }

        executor.execute(() -> {
            try {
                // Get both users
                User currentUser = userDao.getUserByIdSync(currentUserId);
                User targetUser = userDao.getUserByIdSync(targetUserId);

                if (currentUser != null && targetUser != null) {
                    // Update following list for current user
                    List<String> following = currentUser.getFollowing();
                    if (following == null) {
                        following = new ArrayList<>();
                    }

                    if (!following.contains(targetUserId)) {
                        following.add(targetUserId);
                        currentUser.setFollowing(following);
                        userDao.update(currentUser);
                    }

                    // Update followers list for target user
                    List<String> followers = targetUser.getFollowers();
                    if (followers == null) {
                        followers = new ArrayList<>();
                    }

                    if (!followers.contains(currentUserId)) {
                        followers.add(currentUserId);
                        targetUser.setFollowers(followers);
                        targetUser.recalculateEngagementScore();
                        userDao.update(targetUser);
                    }

                    // Update in Firestore with retry logic
                    final List<String> finalFollowing = following;
                    final List<String> finalFollowers = followers;
                    final double engagementScore = targetUser.getOverallEngagementScore();

                    FirebaseErrorHandler.executeWithRetry(() -> {
                        firestore.collection("users").document(currentUserId)
                                .update("following", finalFollowing);
                    }, 3, null);

                    FirebaseErrorHandler.executeWithRetry(() -> {
                        firestore.collection("users").document(targetUserId)
                                .update("followers", finalFollowers,
                                        "overallEngagementScore", engagementScore);
                    }, 3, null);
                } else {
                    Log.e(TAG, "followUser: One or both users not found");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in followUser", e);
            }
        });
    }

    /**
     * Unfollow a user
     * @param currentUserId ID of the current user
     * @param targetUserId ID of the user to unfollow
     */
    public void unfollowUser(String currentUserId, String targetUserId) {
        if (currentUserId == null || targetUserId == null ||
                currentUserId.isEmpty() || targetUserId.isEmpty()) {
            Log.e(TAG, "unfollowUser: Invalid user IDs");
            return;
        }

        executor.execute(() -> {
            try {
                // Get both users
                User currentUser = userDao.getUserByIdSync(currentUserId);
                User targetUser = userDao.getUserByIdSync(targetUserId);

                if (currentUser != null && targetUser != null) {
                    // Update following list for current user
                    List<String> following = currentUser.getFollowing();
                    if (following != null && following.contains(targetUserId)) {
                        following.remove(targetUserId);
                        currentUser.setFollowing(following);
                        userDao.update(currentUser);
                    }

                    // Update followers list for target user
                    List<String> followers = targetUser.getFollowers();
                    if (followers != null && followers.contains(currentUserId)) {
                        followers.remove(currentUserId);
                        targetUser.setFollowers(followers);
                        targetUser.recalculateEngagementScore();
                        userDao.update(targetUser);
                    }

                    // Update in Firestore with retry logic
                    final List<String> finalFollowing = following != null ? following : new ArrayList<>();
                    final List<String> finalFollowers = followers != null ? followers : new ArrayList<>();
                    final double engagementScore = targetUser.getOverallEngagementScore();

                    FirebaseErrorHandler.executeWithRetry(() -> {
                        firestore.collection("users").document(currentUserId)
                                .update("following", finalFollowing);
                    }, 3, null);

                    FirebaseErrorHandler.executeWithRetry(() -> {
                        firestore.collection("users").document(targetUserId)
                                .update("followers", finalFollowers,
                                        "overallEngagementScore", engagementScore);
                    }, 3, null);
                } else {
                    Log.e(TAG, "unfollowUser: One or both users not found");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in unfollowUser", e);
            }
        });
    }

    /**
     * Delete a user from both local database and Firestore
     * @param user The user to delete
     */
    public void deleteUser(User user) {
        if (user == null) {
            Log.e(TAG, "deleteUser: User is null");
            return;
        }

        executor.execute(() -> {
            try {
                userDao.delete(user);

                // Delete from Firestore with retry logic
                FirebaseErrorHandler.executeWithRetry(() -> {
                    firestore.collection("users")
                            .document(user.getUid())
                            .delete();
                }, 3, new FirebaseErrorHandler.Callback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "User document deleted from Firestore");
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Error deleting user document from Firestore", e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error deleting user", e);
            }
        });
    }

    /**
     * Increment likes count for a user
     * @param userId ID of the user
     * @param amount Amount to increment
     */
    public void incrementLikes(String userId, int amount) {
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "incrementLikes: Invalid user ID");
            return;
        }

        executor.execute(() -> {
            try {
                userDao.incrementLikes(userId, amount);

                // Update in Firestore
                User user = userDao.getUserByIdSync(userId);
                if (user != null) {
                    final int totalLikes = user.getTotalLikes();
                    final double engagementScore = user.getOverallEngagementScore();

                    FirebaseErrorHandler.executeWithRetry(() -> {
                        firestore.collection("users").document(userId)
                                .update("totalLikes", totalLikes,
                                        "overallEngagementScore", engagementScore);
                    }, 3, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error incrementing likes", e);
            }
        });
    }

    /**
     * Increment views count for a user
     * @param userId ID of the user
     * @param amount Amount to increment
     */
    public void incrementViews(String userId, int amount) {
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "incrementViews: Invalid user ID");
            return;
        }

        executor.execute(() -> {
            try {
                userDao.incrementViews(userId, amount);

                // Update in Firestore
                User user = userDao.getUserByIdSync(userId);
                if (user != null) {
                    final int totalViews = user.getTotalViews();
                    final double engagementScore = user.getOverallEngagementScore();

                    FirebaseErrorHandler.executeWithRetry(() -> {
                        firestore.collection("users").document(userId)
                                .update("totalViews", totalViews,
                                        "overallEngagementScore", engagementScore);
                    }, 3, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error incrementing views", e);
            }
        });
    }

    /**
     * Increment asset count for a user
     * @param userId ID of the user
     */
    public void incrementAssetCount(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "incrementAssetCount: Invalid user ID");
            return;
        }

        executor.execute(() -> {
            try {
                // Get current user
                User user = userDao.getUserByIdSync(userId);
                if (user != null) {
                    // Update asset count
                    user.setTotalAssets(user.getTotalAssets() + 1);
                    userDao.update(user);

                    // Update in Firestore
                    final int totalAssets = user.getTotalAssets();
                    FirebaseErrorHandler.executeWithRetry(() -> {
                        firestore.collection("users").document(userId)
                                .update("totalAssets", totalAssets);
                    }, 3, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error incrementing asset count", e);
            }
        });
    }

    /**
     * Decrement asset count for a user
     * @param userId ID of the user
     */
    public void decrementAssetCount(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "decrementAssetCount: Invalid user ID");
            return;
        }

        executor.execute(() -> {
            try {
                // Get current user
                User user = userDao.getUserByIdSync(userId);
                if (user != null && user.getTotalAssets() > 0) {
                    // Update asset count
                    user.setTotalAssets(user.getTotalAssets() - 1);
                    userDao.update(user);

                    // Update in Firestore
                    final int totalAssets = user.getTotalAssets();
                    FirebaseErrorHandler.executeWithRetry(() -> {
                        firestore.collection("users").document(userId)
                                .update("totalAssets", totalAssets);
                    }, 3, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error decrementing asset count", e);
            }
        });
    }

    /**
     * Cleanup resources
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    /**
     * Callback interface
     * @param <T> Type of result
     */
    public interface Callback<T> {
        void onResult(T result);
    }
}