package com.imaginit.hyperplux.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manager for two-factor authentication
 */
public class TwoFactorAuthManager {
    private static final String TAG = "TwoFactorAuthManager";
    private static final String PREFS_NAME = "two_factor_prefs";
    private static final String KEY_2FA_ENABLED = "two_factor_enabled";
    private static final String KEY_PHONE_NUMBER = "phone_number";
    private static final int OTP_TIMEOUT = 60; // seconds

    private final Context context;
    private final SharedPreferences preferences;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final AnalyticsTracker analyticsTracker;

    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;

    // Singleton instance
    private static TwoFactorAuthManager instance;

    /**
     * Get the singleton instance of TwoFactorAuthManager
     */
    public static synchronized TwoFactorAuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new TwoFactorAuthManager(context.getApplicationContext());
        }
        return instance;
    }

    private TwoFactorAuthManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
        this.analyticsTracker = AnalyticsTracker.getInstance();
    }

    /**
     * Check if two-factor authentication is enabled for the current user
     */
    public boolean isTwoFactorEnabled() {
        return preferences.getBoolean(KEY_2FA_ENABLED, false);
    }

    /**
     * Enable two-factor authentication for the current user
     * @param phoneNumber The user's phone number in E.164 format
     * @param callback The callback to handle the result
     */
    public void enableTwoFactor(String phoneNumber, TwoFactorCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("User not authenticated");
            return;
        }

        // Start phone verification
        sendVerificationCode(phoneNumber, new VerificationCallback() {
            @Override
            public void onCodeSent(String vId, PhoneAuthProvider.ForceResendingToken token) {
                verificationId = vId;
                resendToken = token;
                callback.onVerificationCodeSent();
            }

            @Override
            public void onVerificationCompleted(PhoneAuthCredential credential) {
                // This will not be triggered normally since we want manual verification
                // But handle automatic verification if it happens
                linkPhoneAuthCredential(credential, phoneNumber, callback);
            }

            @Override
            public void onVerificationFailed(FirebaseException e) {
                callback.onError("Verification failed: " + e.getMessage());
                Log.e(TAG, "Phone verification failed", e);

                // Track verification failure
                Map<String, Object> params = new HashMap<>();
                params.put("error", e.getClass().getSimpleName());
                analyticsTracker.logEvent("two_factor_verification_failed", params);
            }
        });
    }

    /**
     * Verify the code entered by the user
     * @param code The verification code
     * @param phoneNumber The user's phone number
     * @param callback The callback to handle the result
     */
    public void verifyCode(String code, String phoneNumber, TwoFactorCallback callback) {
        if (verificationId == null) {
            callback.onError("No verification code was sent");
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        linkPhoneAuthCredential(credential, phoneNumber, callback);
    }

    /**
     * Resend the verification code
     * @param phoneNumber The user's phone number
     * @param callback The callback to handle the result
     */
    public void resendVerificationCode(String phoneNumber, TwoFactorCallback callback) {
        if (resendToken == null) {
            // No resend token, so start fresh
            sendVerificationCode(phoneNumber, new VerificationCallback() {
                            @Override
                            public void onCodeSent(String vId, PhoneAuthProvider.ForceResendingToken token) {
                                verificationId = vId;
                                resendToken = token;
                                callback.onVerificationCodeSent();
                            }

                            @Override
                            public void onVerificationCompleted(PhoneAuthCredential credential) {
                                linkPhoneAuthCredential(credential, phoneNumber, callback);
                            }

                            @Override
                            public void onVerificationFailed(FirebaseException e) {
                                callback.onError("Verification failed: " + e.getMessage());
                            }
                        });
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        callback.onError("User not authenticated");
                        return;
                    }

                    PhoneAuthOptions options = PhoneAuthOptions.newBuilder()
                            .setPhoneNumber(phoneNumber)
                            .setTimeout((long) OTP_TIMEOUT, TimeUnit.SECONDS)
                            .setActivity(null)
                            .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                @Override
                                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                                    linkPhoneAuthCredential(credential, phoneNumber, callback);
                                }

                                @Override
                                public void onVerificationFailed(@NonNull FirebaseException e) {
                                    callback.onError("Verification failed: " + e.getMessage());
                                    Log.e(TAG, "Phone verification failed on resend", e);
                                }

                                @Override
                                public void onCodeSent(@NonNull String newVerificationId,
                                                       @NonNull PhoneAuthProvider.ForceResendingToken newToken) {
                                    verificationId = newVerificationId;
                                    resendToken = newToken;
                                    callback.onVerificationCodeSent();
                                }
                            })
                            .setForceResendingToken(resendToken)
                            .build();

                    PhoneAuthProvider.verifyPhoneNumber(options);
                }

                /**
                 * Disable two-factor authentication for the current user
                 * @param callback The callback to handle the result
                 */
                public void disableTwoFactor(TwoFactorCallback callback) {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        callback.onError("User not authenticated");
                        return;
                    }

                    String userId = user.getUid();
                    DocumentReference userRef = firestore.collection("users").document(userId);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("twoFactorEnabled", false);
                    updates.put("phoneNumber", null);

                    userRef.update(updates)
                            .addOnSuccessListener(aVoid -> {
                                preferences.edit()
                                        .putBoolean(KEY_2FA_ENABLED, false)
                                        .putString(KEY_PHONE_NUMBER, null)
                                        .apply();

                                callback.onTwoFactorDisabled();

                                // Track 2FA disable
                                analyticsTracker.logEvent("two_factor_disabled", null);
                            })
                            .addOnFailureListener(e -> {
                                callback.onError("Failed to disable two-factor authentication: " + e.getMessage());
                                Log.e(TAG, "Failed to disable two-factor authentication", e);
                            });
                }

                /**
                 * Get the phone number associated with two-factor authentication
                 */
                public String getPhoneNumber() {
                    return preferences.getString(KEY_PHONE_NUMBER, null);
                }

                /**
                 * Send verification code to phone number
                 */
                private void sendVerificationCode(String phoneNumber, VerificationCallback callback) {
                    PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                            .setPhoneNumber(phoneNumber)
                            .setTimeout((long) OTP_TIMEOUT, TimeUnit.SECONDS)
                            .setActivity(null)
                            .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                @Override
                                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                                    callback.onVerificationCompleted(credential);
                                }

                                @Override
                                public void onVerificationFailed(@NonNull FirebaseException e) {
                                    callback.onVerificationFailed(e);
                                }

                                @Override
                                public void onCodeSent(@NonNull String vId,
                                                       @NonNull PhoneAuthProvider.ForceResendingToken token) {
                                    callback.onCodeSent(vId, token);
                                }
                            })
                            .build();

                    PhoneAuthProvider.verifyPhoneNumber(options);

                    // Track verification code sent
                    analyticsTracker.logEvent("two_factor_code_sent", null);
                }

                /**
                 * Link the phone auth credential to the user's account
                 */
                private void linkPhoneAuthCredential(PhoneAuthCredential credential, String phoneNumber,
                                                     TwoFactorCallback callback) {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        callback.onError("User not authenticated");
                        return;
                    }

                    // Link the credential to the user's account
                    user.linkWithCredential(credential)
                            .addOnSuccessListener(authResult -> {
                                // Update the user's profile in Firestore
                                String userId = user.getUid();
                                DocumentReference userRef = firestore.collection("users").document(userId);

                                Map<String, Object> updates = new HashMap<>();
                                updates.put("twoFactorEnabled", true);
                                updates.put("phoneNumber", phoneNumber);

                                userRef.update(updates)
                                        .addOnSuccessListener(aVoid -> {
                                            preferences.edit()
                                                    .putBoolean(KEY_2FA_ENABLED, true)
                                                    .putString(KEY_PHONE_NUMBER, phoneNumber)
                                                    .apply();

                                            callback.onTwoFactorEnabled();

                                            // Track 2FA enable
                                            analyticsTracker.logEvent("two_factor_enabled", null);
                                        })
                                        .addOnFailureListener(e -> {
                                            callback.onError("Failed to update user profile: " + e.getMessage());
                                            Log.e(TAG, "Failed to update user profile", e);
                                        });
                            })
                            .addOnFailureListener(e -> {
                                callback.onError("Failed to link phone auth: " + e.getMessage());
                                Log.e(TAG, "Failed to link phone auth", e);
                            });
                }

                /**
                 * Callback interface for two-factor authentication operations
                 */
                public interface TwoFactorCallback {
                    void onVerificationCodeSent();
                    void onTwoFactorEnabled();
                    void onTwoFactorDisabled();
                    void onError(String errorMessage);
                }

                /**
                 * Callback interface for verification operations
                 */
                private interface VerificationCallback {
                    void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token);
                    void onVerificationCompleted(PhoneAuthCredential credential);
                    void onVerificationFailed(FirebaseException e);
                }
            }