package com.imaginit.hyperplux.utils;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.models.AssetTransaction;

import java.util.Map;

/**
 * Utility class for tracking analytics events
 */
public class AnalyticsTracker {
    private static final String TAG = "AnalyticsTracker";

    private FirebaseAnalytics firebaseAnalytics;
    private static AnalyticsTracker instance;

    // Event names
    public static final String EVENT_ASSET_CREATE = "asset_create";
    public static final String EVENT_ASSET_EDIT = "asset_edit";
    public static final String EVENT_ASSET_DELETE = "asset_delete";
    public static final String EVENT_ASSET_VIEW = "asset_view";
    public static final String EVENT_ASSET_LIKE = "asset_like";
    public static final String EVENT_ASSET_DISLIKE = "asset_dislike";
    public static final String EVENT_ASSET_SHARE = "asset_share";
    public static final String EVENT_ASSET_COMMENT = "asset_comment";
    public static final String EVENT_TRANSACTION_CREATE = "transaction_create";
    public static final String EVENT_TRANSACTION_COMPLETE = "transaction_complete";
    public static final String EVENT_USER_FOLLOW = "user_follow";
    public static final String EVENT_USER_UNFOLLOW = "user_unfollow";
    public static final String EVENT_SEARCH = "search";
    public static final String EVENT_WILL_UPDATE = "will_update";

    // Parameter names
    public static final String PARAM_ASSET_ID = "asset_id";
    public static final String PARAM_ASSET_NAME = "asset_name";
    public static final String PARAM_ASSET_CATEGORY = "asset_category";
    public static final String PARAM_USER_ID = "user_id";
    public static final String PARAM_TRANSACTION_TYPE = "transaction_type";
    public static final String PARAM_TRANSACTION_AMOUNT = "transaction_amount";
    public static final String PARAM_QUERY = "search_query";

    private AnalyticsTracker(Context context) {
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase Analytics: " + e.getMessage());
        }
    }

    /**
     * Get singleton instance
     * @param context Application context
     * @return AnalyticsTracker instance
     */
    public static synchronized AnalyticsTracker getInstance(Context context) {
        if (context == null) {
            Log.e(TAG, "Cannot initialize AnalyticsTracker with null context");
            return null;
        }

        if (instance == null) {
            instance = new AnalyticsTracker(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Set user ID for analytics
     */
    public void setUserIdForAnalytics() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && firebaseAnalytics != null) {
                firebaseAnalytics.setUserId(user.getUid());

                Bundle userProperties = new Bundle();
                if (user.getEmail() != null) {
                    String email = user.getEmail();
                    int atIndex = email.indexOf('@');
                    if (atIndex > 0) {
                        userProperties.putString("user_email_domain", email.substring(atIndex + 1));
                    } else {
                        userProperties.putString("user_email_domain", "unknown");
                    }
                } else {
                    userProperties.putString("user_email_domain", "unknown");
                }

                if (user.getMetadata() != null) {
                    userProperties.putLong("user_creation_time", user.getMetadata().getCreationTimestamp());
                }

                firebaseAnalytics.setDefaultEventParameters(userProperties);

                Log.d(TAG, "User ID set for analytics: " + user.getUid());
            } else {
                if (firebaseAnalytics != null) {
                    firebaseAnalytics.setUserId(null);
                    firebaseAnalytics.setDefaultEventParameters(null);
                }
                Log.d(TAG, "No user logged in, analytics ID cleared");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting user ID for analytics: " + e.getMessage());
        }
    }

    /**
     * Track asset creation event
     * @param asset Asset created
     */
    public void trackAssetCreate(Asset asset) {
        if (asset == null || firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            params.putString(PARAM_ASSET_ID, String.valueOf(asset.getId()));
            if (asset.getName() != null) {
                params.putString(PARAM_ASSET_NAME, asset.getName());
            }
            if (asset.getCategory() != null) {
                params.putString(PARAM_ASSET_CATEGORY, asset.getCategory());
            }

            firebaseAnalytics.logEvent(EVENT_ASSET_CREATE, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking asset create: " + e.getMessage());
        }
    }

    /**
     * Track asset edit event
     * @param asset Asset edited
     */
    public void trackAssetEdit(Asset asset) {
        if (asset == null || firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            params.putString(PARAM_ASSET_ID, String.valueOf(asset.getId()));
            if (asset.getName() != null) {
                params.putString(PARAM_ASSET_NAME, asset.getName());
            }

            firebaseAnalytics.logEvent(EVENT_ASSET_EDIT, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking asset edit: " + e.getMessage());
        }
    }

    /**
     * Track asset delete event
     * @param asset Asset deleted
     */
    public void trackAssetDelete(Asset asset) {
        if (asset == null || firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            params.putString(PARAM_ASSET_ID, String.valueOf(asset.getId()));
            if (asset.getCategory() != null) {
                params.putString(PARAM_ASSET_CATEGORY, asset.getCategory());
            }

            firebaseAnalytics.logEvent(EVENT_ASSET_DELETE, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking asset delete: " + e.getMessage());
        }
    }

    /**
     * Track asset view event
     * @param asset Asset viewed
     */
    public void trackAssetView(Asset asset) {
        if (asset == null || firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            params.putString(PARAM_ASSET_ID, String.valueOf(asset.getId()));
            if (asset.getName() != null) {
                params.putString(PARAM_ASSET_NAME, asset.getName());
            }
            if (asset.getCategory() != null) {
                params.putString(PARAM_ASSET_CATEGORY, asset.getCategory());
            }
            if (asset.getUserId() != null) {
                params.putString(PARAM_USER_ID, asset.getUserId());
            }

            firebaseAnalytics.logEvent(EVENT_ASSET_VIEW, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking asset view: " + e.getMessage());
        }
    }

    /**
     * Track asset like event
     * @param asset Asset liked
     */
    public void trackAssetLike(Asset asset) {
        if (asset == null || firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            params.putString(PARAM_ASSET_ID, String.valueOf(asset.getId()));
            if (asset.getUserId() != null) {
                params.putString(PARAM_USER_ID, asset.getUserId());
            }

            firebaseAnalytics.logEvent(EVENT_ASSET_LIKE, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking asset like: " + e.getMessage());
        }
    }

    /**
     * Track asset dislike event
     * @param asset Asset disliked
     */
    public void trackAssetDislike(Asset asset) {
        if (asset == null || firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            params.putString(PARAM_ASSET_ID, String.valueOf(asset.getId()));
            if (asset.getUserId() != null) {
                params.putString(PARAM_USER_ID, asset.getUserId());
            }

            firebaseAnalytics.logEvent(EVENT_ASSET_DISLIKE, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking asset dislike: " + e.getMessage());
        }
    }

    /**
     * Track asset share event
     * @param asset Asset shared
     */
    public void trackAssetShare(Asset asset) {
        if (asset == null || firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            params.putString(PARAM_ASSET_ID, String.valueOf(asset.getId()));
            if (asset.getName() != null) {
                params.putString(PARAM_ASSET_NAME, asset.getName());
            }

            firebaseAnalytics.logEvent(EVENT_ASSET_SHARE, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking asset share: " + e.getMessage());
        }
    }

    /**
     * Track transaction create event
     * @param transaction Transaction created
     */
    public void trackTransactionCreate(AssetTransaction transaction) {
        if (transaction == null || firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            params.putString(PARAM_ASSET_ID, String.valueOf(transaction.getAssetId()));
            if (transaction.getTransactionType() != null) {
                params.putString(PARAM_TRANSACTION_TYPE, transaction.getTransactionType());
            }
            params.putDouble(PARAM_TRANSACTION_AMOUNT, transaction.getTransactionAmount());
            if (transaction.getFromUserId() != null) {
                params.putString("from_user", transaction.getFromUserId());
            }
            if (transaction.getToUserId() != null) {
                params.putString("to_user", transaction.getToUserId());
            }

            firebaseAnalytics.logEvent(EVENT_TRANSACTION_CREATE, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking transaction create: " + e.getMessage());
        }
    }

    /**
     * Track transaction complete event
     * @param transaction Transaction completed
     */
    public void trackTransactionComplete(AssetTransaction transaction) {
        if (transaction == null || firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            params.putString(PARAM_ASSET_ID, String.valueOf(transaction.getAssetId()));
            if (transaction.getTransactionType() != null) {
                params.putString(PARAM_TRANSACTION_TYPE, transaction.getTransactionType());
            }
            params.putDouble(PARAM_TRANSACTION_AMOUNT, transaction.getTransactionAmount());

            firebaseAnalytics.logEvent(EVENT_TRANSACTION_COMPLETE, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking transaction complete: " + e.getMessage());
        }
    }

    /**
     * Track user follow event
     * @param followerId User doing the following
     * @param followedId User being followed
     */
    public void trackUserFollow(String followerId, String followedId) {
        if (firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            if (followerId != null) {
                params.putString("follower_id", followerId);
            }
            if (followedId != null) {
                params.putString("followed_id", followedId);
            }

            firebaseAnalytics.logEvent(EVENT_USER_FOLLOW, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking user follow: " + e.getMessage());
        }
    }

    /**
     * Track user unfollow event
     * @param followerId User doing the unfollowing
     * @param unfollowedId User being unfollowed
     */
    public void trackUserUnfollow(String followerId, String unfollowedId) {
        if (firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            if (followerId != null) {
                params.putString("follower_id", followerId);
            }
            if (unfollowedId != null) {
                params.putString("unfollowed_id", unfollowedId);
            }

            firebaseAnalytics.logEvent(EVENT_USER_UNFOLLOW, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking user unfollow: " + e.getMessage());
        }
    }

    /**
     * Track search event
     * @param query Search query
     * @param resultCount Number of results
     */
    public void trackSearch(String query, int resultCount) {
        if (firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            if (query != null) {
                params.putString(PARAM_QUERY, query);
            }
            params.putInt("result_count", resultCount);

            firebaseAnalytics.logEvent(EVENT_SEARCH, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking search: " + e.getMessage());
        }
    }

    /**
     * Track will update event
     * @param userId User ID
     * @param nextOfKinSet Whether next of kin is set
     * @param assetCount Number of assets in will
     */
    public void trackWillUpdate(String userId, boolean nextOfKinSet, int assetCount) {
        if (firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            if (userId != null) {
                params.putString(PARAM_USER_ID, userId);
            }
            params.putBoolean("next_of_kin_set", nextOfKinSet);
            params.putInt("asset_count", assetCount);

            firebaseAnalytics.logEvent(EVENT_WILL_UPDATE, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking will update: " + e.getMessage());
        }
    }

    /**
     * Track screen view event
     * @param screenName Screen name
     * @param screenClass Screen class name
     */
    public void trackScreenView(String screenName, String screenClass) {
        if (firebaseAnalytics == null) return;

        try {
            Bundle params = new Bundle();
            if (screenName != null) {
                params.putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName);
            }
            if (screenClass != null) {
                params.putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass);
            }

            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking screen view: " + e.getMessage());
        }
    }

    /**
     * Track a custom event
     * @param eventName Name of the event
     * @param params Map of parameters for the event
     */
    public void trackEvent(String eventName, Map<String, Object> params) {
        if (firebaseAnalytics == null || eventName == null) return;

        try {
            Bundle bundle = new Bundle();
            if (params != null) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    if (value instanceof String) {
                        bundle.putString(key, (String) value);
                    } else if (value instanceof Integer) {
                        bundle.putInt(key, (Integer) value);
                    } else if (value instanceof Long) {
                        bundle.putLong(key, (Long) value);
                    } else if (value instanceof Double) {
                        bundle.putDouble(key, (Double) value);
                    } else if (value instanceof Boolean) {
                        bundle.putBoolean(key, (Boolean) value);
                    } else if (value != null) {
                        bundle.putString(key, value.toString());
                    }
                }
            }

            firebaseAnalytics.logEvent(eventName, bundle);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking event " + eventName + ": " + e.getMessage());
        }
    }

    /**
     * Track a custom event with a single key/value parameter
     * @param eventName Name of the event
     * @param paramKey Parameter key
     * @param paramValue Parameter value
     */
    public void trackEvent(String eventName, String paramKey, String paramValue) {
        if (firebaseAnalytics == null || eventName == null) return;

        try {
            Bundle bundle = new Bundle();
            if (paramKey != null && paramValue != null) {
                bundle.putString(paramKey, paramValue);
            }

            firebaseAnalytics.logEvent(eventName, bundle);
        } catch (Exception e) {
            Log.e(TAG, "Error tracking event " + eventName + ": " + e.getMessage());
        }
    }
}