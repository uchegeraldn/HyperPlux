package com.imaginit.hyperplux.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.storage.StorageException;
import com.imaginit.hyperplux.R;

/**
 * Utility class for handling Firebase errors and network issues
 */
public class FirebaseErrorHandler {
    private static final String TAG = "FirebaseErrorHandler";

    // Error codes
    public static final int ERROR_NONE = 0;
    public static final int ERROR_NETWORK = 1;
    public static final int ERROR_AUTH = 2;
    public static final int ERROR_FIRESTORE = 3;
    public static final int ERROR_STORAGE = 4;
    public static final int ERROR_UNKNOWN = 99;

    // Error message resource IDs
    public static int getErrorMessageRes(int errorCode) {
        switch (errorCode) {
            case ERROR_NETWORK:
                return R.string.error_network_connection;
            case ERROR_AUTH:
                return R.string.error_authentication;
            case ERROR_FIRESTORE:
                return R.string.error_database;
            case ERROR_STORAGE:
                return R.string.error_storage;
            case ERROR_UNKNOWN:
            default:
                return R.string.error_unknown;
        }
    }

    /**
     * Check if network is available
     * @param context Application context
     * @return true if network is available
     */
    public static boolean isNetworkAvailable(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    /**
     * Handle Firebase exception and update error message LiveData
     * @param e The exception to handle
     * @param errorMessageLiveData LiveData to update with error message
     * @return Error code
     */
    public static int handleException(Exception e, MutableLiveData<String> errorMessageLiveData) {
        int errorCode = ERROR_UNKNOWN;
        String errorMessage = "Unknown error occurred";

        if (e instanceof FirebaseNetworkException) {
            errorCode = ERROR_NETWORK;
            errorMessage = "Network error: Check your internet connection";
        } else if (e instanceof FirebaseAuthException) {
            errorCode = ERROR_AUTH;
            FirebaseAuthException authError = (FirebaseAuthException) e;
            errorMessage = "Authentication error: " + authError.getMessage();
        } else if (e instanceof FirebaseFirestoreException) {
            errorCode = ERROR_FIRESTORE;
            FirebaseFirestoreException firestoreError = (FirebaseFirestoreException) e;
            errorMessage = "Database error: " + firestoreError.getMessage();
        } else if (e instanceof StorageException) {
            errorCode = ERROR_STORAGE;
            StorageException storageError = (StorageException) e;
            errorMessage = "Storage error: " + storageError.getMessage();
        }

        // Log the error
        Log.e(TAG, errorMessage, e);

        // Update LiveData if provided
        if (errorMessageLiveData != null) {
            errorMessageLiveData.postValue(errorMessage);
        }

        return errorCode;
    }

    /**
     * Handle Firebase operation with retry logic
     * @param operation Operation to execute
     * @param retryCount Number of retry attempts
     * @param errorMessageLiveData LiveData to update with error message
     */
    public static void executeWithRetry(Runnable operation, int retryCount,
                                        MutableLiveData<String> errorMessageLiveData) {
        int attempts = 0;
        boolean success = false;

        while (!success && attempts < retryCount) {
            try {
                operation.run();
                success = true;
            } catch (Exception e) {
                attempts++;
                int errorCode = handleException(e, attempts == retryCount ? errorMessageLiveData : null);

                // Don't retry for auth errors
                if (errorCode == ERROR_AUTH) {
                    break;
                }

                // Add delay before retry
                try {
                    Thread.sleep(1000 * attempts); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}