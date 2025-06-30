package com.imaginit.hyperplux.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Manager for secure storage of sensitive data
 */
public class SecureStorageManager {
    private static final String TAG = "SecureStorageManager";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String MASTER_KEY_ALIAS = "HyperPlux_MasterKey";
    private static final String SECURE_PREFS_FILENAME = "secure_prefs";
    private static final String LEGACY_PREFS_FILENAME = "legacy_secure_prefs";
    private static final String KEY_PREFIX = "encrypted_";
    private static final String KEY_IV_PREFIX = "iv_";
    private static final String ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;
    private static final String ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM;
    private static final String ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE;
    private static final String TRANSFORMATION =
            ENCRYPTION_ALGORITHM + "/" + ENCRYPTION_BLOCK_MODE + "/" + ENCRYPTION_PADDING;
    private static final int GCM_TAG_LENGTH = 128;

    private final Context context;
    private SharedPreferences securePrefs;
    private SharedPreferences legacyPrefs;

    // Singleton instance
    private static SecureStorageManager instance;

    /**
     * Get the singleton instance of SecureStorageManager
     */
    public static synchronized SecureStorageManager getInstance(Context context) {
        if (instance == null) {
            instance = new SecureStorageManager(context.getApplicationContext());
        }
        return instance;
    }

    private SecureStorageManager(Context context) {
        this.context = context;
        initializeSecureStorage();
    }

    /**
     * Initialize secure storage based on device capabilities
     */
    private void initializeSecureStorage() {
        // Setup legacy encryption for older devices
        legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_FILENAME, Context.MODE_PRIVATE);

        // Use EncryptedSharedPreferences if available (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MasterKey masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                securePrefs = EncryptedSharedPreferences.create(
                        context,
                        SECURE_PREFS_FILENAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );

                // Migrate any data from legacy storage if needed
                migrateLegacyData();
            } catch (GeneralSecurityException | IOException e) {
                Log.e(TAG, "Error initializing EncryptedSharedPreferences", e);
                // Fall back to legacy encryption
                securePrefs = null;
            }
        } else {
            securePrefs = null;
        }
    }

    /**
     * Store a string securely
     * @param key The key to store the data under
     * @param value The string value to store
     */
    public void secureStore(String key, String value) {
        if (value == null) {
            secureRemove(key);
            return;
        }

        if (securePrefs != null) {
            // Use EncryptedSharedPreferences for Android M and above
            securePrefs.edit().putString(key, value).apply();
        } else {
            // Use manual encryption for older Android versions
            try {
                byte[] iv = generateIV();
                String encrypted = encrypt(value, iv);

                legacyPrefs.edit()
                        .putString(KEY_PREFIX + key, encrypted)
                        .putString(KEY_IV_PREFIX + key, Base64.encodeToString(iv, Base64.DEFAULT))
                        .apply();
            } catch (Exception e) {
                Log.e(TAG, "Error encrypting data", e);
            }
        }
    }

    /**
     * Retrieve a string from secure storage
     * @param key The key to retrieve
     * @param defaultValue The default value if the key doesn't exist
     * @return The decrypted string or defaultValue if not found
     */
    public String secureRetrieve(String key, String defaultValue) {
        if (securePrefs != null) {
            // Use EncryptedSharedPreferences for Android M and above
            return securePrefs.getString(key, defaultValue);
        } else {
            // Use manual decryption for older Android versions
            String encryptedValue = legacyPrefs.getString(KEY_PREFIX + key, null);
            String ivString = legacyPrefs.getString(KEY_IV_PREFIX + key, null);

            if (encryptedValue == null || ivString == null) {
                return defaultValue;
            }

            try {
                byte[] iv = Base64.decode(ivString, Base64.DEFAULT);
                return decrypt(encryptedValue, iv);
            } catch (Exception e) {
                Log.e(TAG, "Error decrypting data", e);
                return defaultValue;
            }
        }
    }

    /**
     * Remove a key from secure storage
     * @param key The key to remove
     */
    public void secureRemove(String key) {
        if (securePrefs != null) {
            securePrefs.edit().remove(key).apply();
        }

        // Always clean up legacy storage too
        legacyPrefs.edit()
                .remove(KEY_PREFIX + key)
                .remove(KEY_IV_PREFIX + key)
                .apply();
    }

    /**
     * Clear all secure storage
     */
    public void secureClear() {
        if (securePrefs != null) {
            securePrefs.edit().clear().apply();
        }

        legacyPrefs.edit().clear().apply();
    }

    /**
     * Check if a key exists in secure storage
     * @param key The key to check
     * @return true if the key exists
     */
    public boolean secureContains(String key) {
        if (securePrefs != null) {
            return securePrefs.contains(key);
        } else {
            return legacyPrefs.contains(KEY_PREFIX + key) &&
                    legacyPrefs.contains(KEY_IV_PREFIX + key);
        }
    }

    /**
     * Migrate data from legacy storage to EncryptedSharedPreferences
     */
    private void migrateLegacyData() {
        for (String key : legacyPrefs.getAll().keySet()) {
            if (key.startsWith(KEY_PREFIX)) {
                String originalKey = key.substring(KEY_PREFIX.length());
                String ivKey = KEY_IV_PREFIX + originalKey;

                if (legacyPrefs.contains(ivKey)) {
                    String encryptedValue = legacyPrefs.getString(key, null);
                    String ivString = legacyPrefs.getString(ivKey, null);

                    if (encryptedValue != null && ivString != null) {
                        try {
                            byte[] iv = Base64.decode(ivString, Base64.DEFAULT);
                            String decryptedValue = decrypt(encryptedValue, iv);

                            // Store in EncryptedSharedPreferences
                            securePrefs.edit().putString(originalKey, decryptedValue).apply();

                            // Remove from legacy storage
                            legacyPrefs.edit()
                                    .remove(key)
                                    .remove(ivKey)
                                    .apply();
                        } catch (Exception e) {
                            Log.e(TAG, "Error migrating data for key: " + originalKey, e);
                        }
                    }
                }
            }
        }
    }

    /**
     * Generate a random initialization vector
     */
    private byte[] generateIV() {
        byte[] iv = new byte[12]; // GCM recommended IV length
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * Encrypt a string
     */
    private String encrypt(String plaintext, byte[] iv) throws GeneralSecurityException {
        SecretKey key = getEncryptionKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
    }

    /**
     * Decrypt a string
     */
    private String decrypt(String ciphertext, byte[] iv) throws GeneralSecurityException {
        SecretKey key = getEncryptionKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        byte[] encryptedBytes = Base64.decode(ciphertext, Base64.DEFAULT);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * Get or create the encryption key
     */
    private SecretKey getEncryptionKey() throws GeneralSecurityException {
        // For API 23+, use Android Keystore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getOrCreateKeyStoreKey();
        } else {
            // For older versions, derive key from a stored secret
            return getLegacyKey();
        }
    }

    /**
     * Get or create a key from Android Keystore
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private SecretKey getOrCreateKeyStoreKey() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        try {
            keyStore.load(null);

            // Check if key exists
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                return (SecretKey) keyStore.getKey(MASTER_KEY_ALIAS, null);
            }

            // Create new key
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

            KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();

            keyGenerator.init(keySpec);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to initialize key", e);
        }
    }

    /**
     * Get or create a key for legacy encryption
     */
    private SecretKey getLegacyKey() throws GeneralSecurityException {
        String keyString = legacyPrefs.getString("encryption_key", null);

        if (keyString == null) {
            // Generate a new key
            byte[] keyBytes = new byte[32]; // 256 bits
            new SecureRandom().nextBytes(keyBytes);
            keyString = Base64.encodeToString(keyBytes, Base64.DEFAULT);

            // Store the key
            legacyPrefs.edit().putString("encryption_key", keyString).apply();
        }

        byte[] keyBytes = Base64.decode(keyString, Base64.DEFAULT);
        return new SecretKeySpec(keyBytes, "AES");
    }
}