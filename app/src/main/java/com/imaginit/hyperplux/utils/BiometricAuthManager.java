package com.imaginit.hyperplux.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.imaginit.hyperplux.R;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Manages biometric authentication for sensitive operations
 */
public class BiometricAuthManager {
    private static final String TAG = "BiometricAuthManager";
    private static final String PREFS_NAME = "biometric_prefs";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_IV = "encryption_iv";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_NAME = "HyperPlux_Key";
    private static final String ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;
    private static final String ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM;
    private static final String ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE;
    private static final String ENCRYPTION_TRANSFORMATION =
            ENCRYPTION_ALGORITHM + "/" + ENCRYPTION_BLOCK_MODE + "/" + ENCRYPTION_PADDING;
    private static final int GCM_TAG_LENGTH = 128;

    private final Context context;
    private final SharedPreferences preferences;
    private final AnalyticsTracker analyticsTracker;

    // Singleton instance
    private static volatile BiometricAuthManager instance;

    /**
     * Get the singleton instance of BiometricAuthManager
     * @param context Application context
     * @return BiometricAuthManager instance or null if context is null
     */
    public static synchronized BiometricAuthManager getInstance(Context context) {
        if (context == null) {
            Log.e(TAG, "Cannot initialize BiometricAuthManager with null context");
            return null;
        }

        if (instance == null) {
            instance = new BiometricAuthManager(context.getApplicationContext());
        }
        return instance;
    }

    private BiometricAuthManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.analyticsTracker = AnalyticsTracker.getInstance(context);
    }

    /**
     * Check if the device supports biometric authentication
     * @return true if biometric authentication is supported
     */
    public boolean isBiometricSupported() {
        try {
            BiometricManager biometricManager = BiometricManager.from(context);
            int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
            return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) {
            Log.e(TAG, "Error checking biometric support: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if biometric authentication is enabled in settings
     * @return true if biometric authentication is enabled
     */
    public boolean isBiometricEnabled() {
        try {
            return isBiometricSupported() && preferences.getBoolean(KEY_BIOMETRIC_ENABLED, false);
        } catch (Exception e) {
            Log.e(TAG, "Error checking if biometric is enabled: " + e.getMessage());
            return false;
        }
    }

    /**
     * Enable or disable biometric authentication
     * @param enabled true to enable, false to disable
     */
    public void setBiometricEnabled(boolean enabled) {
        try {
            preferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply();

            // Track biometric setting change
            if (analyticsTracker != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("enabled", enabled);
                analyticsTracker.trackEvent("biometric_settings_changed", params);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting biometric enabled: " + e.getMessage());
        }
    }

    /**
     * Authenticate the user with biometrics for a sensitive operation
     *
     * @param activity The current activity
     * @param title The title to show in the biometric prompt
     * @param subtitle The subtitle to show in the biometric prompt
     * @param description The description to show in the biometric prompt
     * @param callback The callback to handle authentication result
     */
    public void authenticate(FragmentActivity activity, String title, String subtitle,
                             String description, AuthenticationCallback callback) {
        try {
            if (activity == null || callback == null) {
                Log.e(TAG, "Activity or callback is null");
                return;
            }

            if (!isBiometricEnabled()) {
                callback.onAuthenticationError(BiometricPrompt.ERROR_NO_BIOMETRICS,
                        "Biometric authentication is not enabled");
                return;
            }

            Executor executor = ContextCompat.getMainExecutor(activity);

            BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            try {
                                callback.onAuthenticationSucceeded(result);

                                // Track successful authentication
                                if (analyticsTracker != null) {
                                    analyticsTracker.trackEvent("biometric_auth_success", null);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error in authentication success callback: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            try {
                                callback.onAuthenticationError(errorCode, errString.toString());

                                // Track authentication error
                                if (analyticsTracker != null) {
                                    Map<String, Object> params = new HashMap<>();
                                    params.put("error_code", errorCode);
                                    params.put("error_message", errString.toString());
                                    analyticsTracker.trackEvent("biometric_auth_error", params);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error in authentication error callback: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            try {
                                callback.onAuthenticationFailed();

                                // Track authentication failure
                                if (analyticsTracker != null) {
                                    analyticsTracker.trackEvent("biometric_auth_failed", null);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error in authentication failed callback: " + e.getMessage());
                            }
                        }
                    });

            // Safe string handling
            String safeTitle = (title != null) ? title : "Authentication Required";
            String safeSubtitle = (subtitle != null) ? subtitle : "";
            String safeDescription = (description != null) ? description : "";

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(safeTitle)
                    .setSubtitle(safeSubtitle)
                    .setDescription(safeDescription)
                    .setNegativeButtonText(context.getString(R.string.cancel))
                    .setConfirmationRequired(false)
                    .build();

            biometricPrompt.authenticate(promptInfo);
        } catch (Exception e) {
            Log.e(TAG, "Error during authentication: " + e.getMessage());
            if (callback != null) {
                callback.onAuthenticationError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
                        "Authentication failed: " + e.getMessage());
            }
        }
    }

    /**
     * Encrypt data using the biometric-protected key
     * @param data The data to encrypt
     * @return The encrypted data as a Base64 string
     * @throws SecurityException If encryption fails
     */
    public String encrypt(String data) throws SecurityException {
        if (data == null) {
            throw new SecurityException("Cannot encrypt null data");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return encryptM(data);
        } else {
            // Fallback for older devices
            throw new SecurityException("Encryption requires Android M (API 23) or higher");
        }
    }

    /**
     * Implementation of encrypt for Android M and above
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private String encryptM(String data) throws SecurityException {
        try {
            Cipher cipher = getCipher();
            SecretKey secretKey = getOrCreateSecretKey();

            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Save IV for decryption later
            preferences.edit().putString(KEY_IV, Base64.encodeToString(iv, Base64.DEFAULT)).apply();

            return Base64.encodeToString(encryptedData, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error encrypting data", e);
            throw new SecurityException("Could not encrypt data: " + e.getMessage());
        }
    }

    /**
     * Decrypt data using the biometric-protected key
     * @param encryptedData The encrypted data as a Base64 string
     * @return The decrypted data
     * @throws SecurityException If decryption fails
     */
    public String decrypt(String encryptedData) throws SecurityException {
        if (encryptedData == null) {
            throw new SecurityException("Cannot decrypt null data");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return decryptM(encryptedData);
        } else {
            // Fallback for older devices
            throw new SecurityException("Decryption requires Android M (API 23) or higher");
        }
    }

    /**
     * Implementation of decrypt for Android M and above
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private String decryptM(String encryptedData) throws SecurityException {
        try {
            Cipher cipher = getCipher();
            SecretKey secretKey = getOrCreateSecretKey();

            String ivString = preferences.getString(KEY_IV, null);
            if (ivString == null) {
                throw new SecurityException("IV not found, cannot decrypt data");
            }

            byte[] iv = Base64.decode(ivString, Base64.DEFAULT);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            byte[] decodedData = Base64.decode(encryptedData, Base64.DEFAULT);
            byte[] decryptedData = cipher.doFinal(decodedData);

            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting data", e);
            throw new SecurityException("Could not decrypt data: " + e.getMessage());
        }
    }

    /**
     * Gets or creates the secret key for encryption/decryption
     * @return The secret key
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private SecretKey getOrCreateSecretKey() throws KeyStoreException, CertificateException,
            NoSuchAlgorithmException, IOException, NoSuchProviderException,
            InvalidAlgorithmParameterException, UnrecoverableKeyException {

        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (!keyStore.containsAlias(KEY_NAME)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

            KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                    KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setRandomizedEncryptionRequired(true)
                    .build();

            keyGenerator.init(keyGenParameterSpec);
            return keyGenerator.generateKey();
        }

        return (SecretKey) keyStore.getKey(KEY_NAME, null);
    }

    /**
     * Get a cipher instance for encryption/decryption
     * @return The cipher instance
     */
    private Cipher getCipher() throws NoSuchPaddingException, NoSuchAlgorithmException {
        return Cipher.getInstance(ENCRYPTION_TRANSFORMATION);
    }

    /**
     * Clear any saved biometric data - useful for logout
     */
    public void clearBiometricData() {
        try {
            preferences.edit()
                    .remove(KEY_BIOMETRIC_ENABLED)
                    .remove(KEY_IV)
                    .apply();

            // Try to delete the key if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
                    keyStore.load(null);
                    if (keyStore.containsAlias(KEY_NAME)) {
                        keyStore.deleteEntry(KEY_NAME);
                        Log.d(TAG, "Successfully deleted biometric key");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error clearing biometric key: " + e.getMessage());
                }
            }

            Log.d(TAG, "Cleared biometric data");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing biometric data: " + e.getMessage());
        }
    }

    /**
     * Callback interface for authentication results
     */
    public interface AuthenticationCallback {
        /**
         * Called when authentication succeeds
         * @param result The authentication result
         */
        void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result);

        /**
         * Called when an authentication error occurs
         * @param errorCode The error code
         * @param errorMessage The error message
         */
        void onAuthenticationError(int errorCode, String errorMessage);

        /**
         * Called when authentication fails
         */
        void onAuthenticationFailed();
    }
}