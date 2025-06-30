package com.imaginit.hyperplux.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Monitor network connectivity changes
 */
public class NetworkMonitor {
    private static final String TAG = "NetworkMonitor";

    private staatic NetworkMonitor instance;
    private final MutableLiveData<Boolean> isNetworkAvailable = new MutableLiveData<>();
    private final ConnectivityManager connectivityManager;

    private NetworkMonitor(Context context) {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } else {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(request, networkCallback);
        }

        // Initial state
        isNetworkAvailable.postValue(isNetworkCurrentlyAvailable());
    }

    /**
     * Get singleton instance
     * @param context Application context
     * @return NetworkMonitor instance
     */
    public static synchronized NetworkMonitor getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkMonitor(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Get network availability as LiveData
     * @return LiveData of network state
     */
    public LiveData<Boolean> getNetworkAvailability() {
        return isNetworkAvailable;
    }

    /**
     * Check if network is currently available
     * @return true if network is available
     */
    public boolean isNetworkCurrentlyAvailable() {
        boolean result = false;

        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
                if (capabilities != null) {
                    result = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                }
            } else {
                result = connectivityManager.getActiveNetworkInfo() != null &&
                        connectivityManager.getActiveNetworkInfo().isConnected();
            }
        }

        return result;
    }

    /**
     * Network callback for monitoring changes
     */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            isNetworkAvailable.postValue(true);
            Log.d(TAG, "Network available");
        }

        @Override
        public void onLost(@NonNull Network network) {
            isNetworkAvailable.postValue(false);
            Log.d(TAG, "Network lost");
        }
    };
}