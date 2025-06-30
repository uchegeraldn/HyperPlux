package com.imaginit.hyperplux.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.imaginit.hyperplux.models.User;
import com.imaginit.hyperplux.repositories.UserRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class UserViewModel extends ViewModel {
    private static final String TAG = "UserViewModel";

    private UserRepository repository;
    private FirebaseAuth auth;

    // LiveData for user profile
    private LiveData<User> currentUser;
    private MutableLiveData<List<User>> searchResults = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public UserViewModel(UserRepository repository) {
        this.repository = repository;
        this.auth = FirebaseAuth.getInstance();
        refreshCurrentUser();
    }

    // Refresh current user data
    public void refreshCurrentUser() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser != null) {
            currentUser = repository.getCurrentUser();

            // Create user in Room if they don't exist yet
            repository.getUserByIdSync(firebaseUser.getUid(), user -> {
                if (user == null) {
                    // New user - create profile
                    User newUser = new User(firebaseUser.getUid(), firebaseUser.getEmail());
                    if (firebaseUser.getDisplayName() != null) {
                        newUser.setDisplayName(firebaseUser.getDisplayName());
                    } else {
                        newUser.setDisplayName(firebaseUser.getEmail().split("@")[0]);
                    }
                    if (firebaseUser.getPhotoUrl() != null) {
                        newUser.setProfileImageUri(firebaseUser.getPhotoUrl().toString());
                    }
                    repository.createOrUpdateUser(newUser);
                } else {
                    // Existing user - update last login
                    user.setLastLoginDate(new Date());
                    repository.createOrUpdateUser(user);
                }
            });
        } else {
            currentUser = null;
        }
    }

    // Get current user profile
    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    // Get user by ID
    public LiveData<User> getUserById(String userId) {
        return repository.getUserById(userId);
    }

    // Search users
    public void searchUsers(String query) {
        isLoading.setValue(true);
        LiveData<List<User>> results = repository.searchUsers(query);
        results.observeForever(users -> {
            searchResults.setValue(users);
            isLoading.setValue(false);
        });
    }

    // Get search results
    public LiveData<List<User>> getSearchResults() {
        return searchResults;
    }

    // Update user profile
    public void updateUserProfile(String displayName, String bio, String phoneNumber) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            errorMessage.setValue("No user logged in");
            return;
        }

        repository.getUserByIdSync(firebaseUser.getUid(), user -> {
            if (user != null) {
                user.setDisplayName(displayName);
                user.setBio(bio);
                user.setPhoneNumber(phoneNumber);
                repository.createOrUpdateUser(user);
            }
        });
    }

    // Update user preferences
    public void updateUserPreferences(List<String> interests, String currency, String language, String theme) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            errorMessage.setValue("No user logged in");
            return;
        }

        repository.getUserByIdSync(firebaseUser.getUid(), user -> {
            if (user != null) {
                user.setInterests(interests);
                user.setPreferredCurrency(currency);
                user.setPreferredLanguage(language);
                user.setPreferredTheme(theme);
                repository.createOrUpdateUser(user);
            }
        });
    }

    // Update privacy settings
    public void updatePrivacySettings(boolean publicProfile, boolean showAssetsPublicly,
                                      boolean allowMessages, boolean enableNotifications) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            errorMessage.setValue("No user logged in");
            return;
        }

        repository.getUserByIdSync(firebaseUser.getUid(), user -> {
            if (user != null) {
                user.setPublicProfile(publicProfile);
                user.setShowAssetsPublicly(showAssetsPublicly);
                user.setAllowMessages(allowMessages);
                user.setNotificationsEnabled(enableNotifications);
                repository.createOrUpdateUser(user);
            }
        });
    }

    // Update will/inheritance settings
    public void updateWillSettings(String nextOfKinName, String nextOfKinEmail,
                                   String nextOfKinPhone, String nextOfKinId) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            errorMessage.setValue("No user logged in");
            return;
        }

        repository.getUserByIdSync(firebaseUser.getUid(), user -> {
            if (user != null) {
                user.setNextOfKinName(nextOfKinName);
                user.setNextOfKinEmail(nextOfKinEmail);
                user.setNextOfKinPhone(nextOfKinPhone);
                user.setNextOfKinId(nextOfKinId);
                repository.createOrUpdateUser(user);
            }
        });
    }

    // Activate will (would be triggered by external verification)
    public void activateWill(String userId) {
        repository.getUserByIdSync(userId, user -> {
            if (user != null) {
                user.setWillActivated(true);
                repository.createOrUpdateUser(user);
            }
        });
    }

    // Follow a user
    public void followUser(String targetUserId) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            errorMessage.setValue("No user logged in");
            return;
        }

        repository.followUser(firebaseUser.getUid(), targetUserId);
    }

    // Unfollow a user
    public void unfollowUser(String targetUserId) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            errorMessage.setValue("No user logged in");
            return;
        }

        repository.unfollowUser(firebaseUser.getUid(), targetUserId);
    }

    // Get followers for the current user
    public LiveData<List<User>> getFollowers() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            return new MutableLiveData<>(new ArrayList<>());
        }

        final MutableLiveData<List<User>> followersResult = new MutableLiveData<>();

        repository.getUserByIdSync(firebaseUser.getUid(), user -> {
            if (user != null && user.getFollowers() != null && !user.getFollowers().isEmpty()) {
                LiveData<List<User>> followers = repository.getFollowers(user.getFollowers());
                followers.observeForever(followersData -> {
                    followersResult.setValue(followersData);
                });
            } else {
                followersResult.setValue(new ArrayList<>());
            }
        });

        return followersResult;
    }

    // Get users the current user is following
    public LiveData<List<User>> getFollowing() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            return new MutableLiveData<>(new ArrayList<>());
        }

        final MutableLiveData<List<User>> followingResult = new MutableLiveData<>();

        repository.getUserByIdSync(firebaseUser.getUid(), user -> {
            if (user != null && user.getFollowing() != null && !user.getFollowing().isEmpty()) {
                LiveData<List<User>> following = repository.getFollowing(user.getFollowing());
                following.observeForever(followingData -> {
                    followingResult.setValue(followingData);
                });
            } else {
                followingResult.setValue(new ArrayList<>());
            }
        });

        return followingResult;
    }

    // Set profile image
    public void setProfileImage(String imageUri) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            errorMessage.setValue("No user logged in");
            return;
        }

        repository.getUserByIdSync(firebaseUser.getUid(), user -> {
            if (user != null) {
                user.setProfileImageUri(imageUri);
                repository.createOrUpdateUser(user);
            }
        });
    }

    // Check if current user is following another user
    public void isFollowing(String targetUserId, UserRepository.Callback<Boolean> callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onResult(false);
            return;
        }

        repository.getUserByIdSync(firebaseUser.getUid(), user -> {
            if (user != null && user.getFollowing() != null) {
                callback.onResult(user.getFollowing().contains(targetUserId));
            } else {
                callback.onResult(false);
            }
        });
    }

    // Get loading state
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    // Get error messages
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
}