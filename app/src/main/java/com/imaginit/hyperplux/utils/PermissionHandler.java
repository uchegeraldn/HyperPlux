package com.imaginit.hyperplux.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.imaginit.hyperplux.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for handling runtime permissions
 */
public class PermissionHandler {
    private static final String TAG = "PermissionHandler";

    // Permission types
    public static final int PERMISSION_STORAGE = 1;
    public static final int PERMISSION_LOCATION = 2;
    public static final int PERMISSION_NOTIFICATION = 3;
    public static final int PERMISSION_CAMERA = 4;

    /**
     * Permission callback interface
     */
    public interface PermissionCallback {
        void onPermissionGranted(int permissionType);
        void onPermissionDenied(int permissionType);
    }

    /**
     * Check if a specific permission is granted
     * @param context Context
     * @param permission Permission to check
     * @return true if permission is granted
     */
    public static boolean isPermissionGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Get required permissions based on permission type
     * @param permissionType Type of permission
     * @return Array of required permissions
     */
    public static String[] getRequiredPermissions(int permissionType) {
        switch (permissionType) {
            case PERMISSION_STORAGE:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return new String[]{Manifest.permission.READ_MEDIA_IMAGES};
                } else {
                    return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
                }
            case PERMISSION_LOCATION:
                return new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                };
            case PERMISSION_NOTIFICATION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return new String[]{Manifest.permission.POST_NOTIFICATIONS};
                } else {
                    return new String[]{};
                }
            case PERMISSION_CAMERA:
                return new String[]{Manifest.permission.CAMERA};
            default:
                return new String[]{};
        }
    }

    /**
     * Check if all permissions of a specific type are granted
     * @param context Context
     * @param permissionType Type of permission
     * @return true if all permissions are granted
     */
    public static boolean checkPermission(Context context, int permissionType) {
        String[] permissions = getRequiredPermissions(permissionType);

        // Special case for notifications on lower API levels
        if (permissionType == PERMISSION_NOTIFICATION && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        for (String permission : permissions) {
            if (!isPermissionGranted(context, permission)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Request permission with rationale dialog if needed
     * @param activity Activity
     * @param permissionType Type of permission
     * @param callback Permission callback
     */
    public static void requestPermissionWithRationale(Activity activity, int permissionType, PermissionCallback callback) {
        String[] permissions = getRequiredPermissions(permissionType);

        // Check if we need to show rationale for any permission
        boolean shouldShowRationale = false;
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                shouldShowRationale = true;
                break;
            }
        }

        if (shouldShowRationale) {
            // Show rationale dialog
            new AlertDialog.Builder(activity)
                    .setTitle(getPermissionRationaleTitle(permissionType))
                    .setMessage(getPermissionRationaleMessage(permissionType))
                    .setPositiveButton(android.R.string.ok, (dialog, which) ->
                            requestPermission(activity, permissionType, callback))
                    .setNegativeButton(android.R.string.cancel, (dialog, which) ->
                            callback.onPermissionDenied(permissionType))
                    .create()
                    .show();
        } else {
            // Request permission directly
            requestPermission(activity, permissionType, callback);
        }
    }

    /**
     * Request permission directly
     * @param activity Activity
     * @param permissionType Type of permission
     * @param callback Permission callback
     */
    public static void requestPermission(Activity activity, int permissionType, PermissionCallback callback) {
        String[] permissions = getRequiredPermissions(permissionType);

        // Special case for notifications on lower API levels
        if (permissionType == PERMISSION_NOTIFICATION && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            callback.onPermissionGranted(permissionType);
            return;
        }

        // Check if all permissions are already granted
        boolean allGranted = true;
        for (String permission : permissions) {
            if (!isPermissionGranted(activity, permission)) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            callback.onPermissionGranted(permissionType);
            return;
        }

        // Request permissions
        ActivityCompat.requestPermissions(activity, permissions, permissionType);

        // Store callback for later use
        permissionCallbacks.put(permissionType, callback);
    }

    // Store callbacks for permission requests
    private static Map<Integer, PermissionCallback> permissionCallbacks = new HashMap<>();

    /**
     * Handle permission request result
     * @param requestCode Request code (permission type)
     * @param permissions Requested permissions
     * @param grantResults Grant results
     */
    public static void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                  @NonNull int[] grantResults) {
        PermissionCallback callback = permissionCallbacks.get(requestCode);
        if (callback == null) {
            return;
        }

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            callback.onPermissionGranted(requestCode);
        } else {
            callback.onPermissionDenied(requestCode);
        }

        // Remove callback
        permissionCallbacks.remove(requestCode);
    }

    /**
     * Show settings dialog when permission is permanently denied
     * @param activity Activity
     * @param permissionType Type of permission
     */
    public static void showSettingsDialog(Activity activity, int permissionType) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.permission_required)
                .setMessage(getPermissionSettingsMessage(permissionType))
                .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                    intent.setData(uri);
                    activity.startActivity(intent);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    /**
     * Get permission rationale title
     * @param permissionType Type of permission
     * @return Resource ID for title
     */
    private static int getPermissionRationaleTitle(int permissionType) {
        switch (permissionType) {
            case PERMISSION_STORAGE:
                return R.string.storage_permission_title;
            case PERMISSION_LOCATION:
                return R.string.location_permission_title;
            case PERMISSION_NOTIFICATION:
                return R.string.notification_permission_title;
            case PERMISSION_CAMERA:
                return R.string.camera_permission_title;
            default:
                return R.string.permission_required;
        }
    }

    /**
     * Get permission rationale message
     * @param permissionType Type of permission
     * @return Resource ID for message
     */
    private static int getPermissionRationaleMessage(int permissionType) {
        switch (permissionType) {
            case PERMISSION_STORAGE:
                return R.string.storage_permission_rationale;
            case PERMISSION_LOCATION:
                return R.string.location_permission_rationale;
            case PERMISSION_NOTIFICATION:
                return R.string.notification_permission_rationale;
            case PERMISSION_CAMERA:
                return R.string.camera_permission_rationale;
            default:
                return R.string.permission_rationale;
        }
    }

    /**
     * Get permission settings message
     * @param permissionType Type of permission
     * @return Resource ID for message
     */
    private static int getPermissionSettingsMessage(int permissionType) {
        switch (permissionType) {
            case PERMISSION_STORAGE:
                return R.string.storage_permission_settings;
            case PERMISSION_LOCATION:
                return R.string.location_permission_settings;
            case PERMISSION_NOTIFICATION:
                return R.string.notification_permission_settings;
            case PERMISSION_CAMERA:
                return R.string.camera_permission_settings;
            default:
                return R.string.permission_settings;
        }
    }

    /**
     * Register permission launcher for a fragment
     * @param fragment Fragment
     * @param permissionType Type of permission
     * @param callback Permission callback
     * @return ActivityResultLauncher for permission request
     */
    public static ActivityResultLauncher<String[]> registerPermissionLauncher(
            Fragment fragment, int permissionType, PermissionCallback callback) {
        return fragment.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (allGranted) {
                        callback.onPermissionGranted(permissionType);
                    } else {
                        callback.onPermissionDenied(permissionType);
                    }
                });
    }
}