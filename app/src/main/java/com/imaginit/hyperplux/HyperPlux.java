package com.imaginit.hyperplux;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FirebaseStorage;
import com.imaginit.hyperplux.database.AppDatabase;

/**
 * Main Application class for initializing app-wide components and configurations
 */
public class HyperPlux extends Application {
    private static final String TAG = "HyperPlux";

    // Notification channels
    public static final String CHANNEL_TRANSACTIONS = "transactions";
    public static final String CHANNEL_SOCIAL = "social";
    public static final String CHANNEL_GENERAL = "general";

    // Instance for singleton access
    private static HyperPlux instance;

    // Firebase components
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;
    private FirebaseAuth auth;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Initialize Firebase components
        initializeFirebase();

        // Configure Firebase for offline support
        configureFirebaseOfflineSupport();

        // Create notification channels for Android O and above
        createNotificationChannels();

        // Initialize FCM
        initializeCloudMessaging();

        // Initialize theme manager
        ThemeManager.init(this);

        // Initialize Room database
        AppDatabase.getDatabase(this);

        Log.d(TAG, "HyperPlux Application initialized successfully");
    }

    /**
     * Get application instance
     * @return The singleton application instance
     */
    public static HyperPlux getInstance() {
        return instance;
    }

    /**
     * Initialize all Firebase components
     */
    private void initializeFirebase() {
        try {
            // Initialize core Firebase
            FirebaseApp.initializeApp(this);

            // Get Firebase instances
            auth = FirebaseAuth.getInstance();
            storage = FirebaseStorage.getInstance();
            firestore = FirebaseFirestore.getInstance();

            // Enable Firebase Crashlytics
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);

            Log.d(TAG, "Firebase initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase: " + e.getMessage());
            FirebaseCrashlytics.getInstance().recordException(e);
        }
    }

    /**
     * Configure Firebase for offline support
     */
    private void configureFirebaseOfflineSupport() {
        try {
            // Enable Firestore offline persistence
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();

            firestore.setFirestoreSettings(settings);

            // Configure Storage for offline support
            storage.setMaxDownloadRetryTimeMillis(60000); // 1 minute
            storage.setMaxOperationRetryTimeMillis(120000); // 2 minutes
            storage.setMaxUploadRetryTimeMillis(60000); // 1 minute

            Log.d(TAG, "Firebase offline support configured");
        } catch (Exception e) {
            Log.e(TAG, "Error configuring Firebase offline support: " + e.getMessage());
            FirebaseCrashlytics.getInstance().recordException(e);
        }
    }

    /**
     * Initialize Firebase Cloud Messaging
     */
    private void initializeCloudMessaging() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "FCM token retrieval failed", task.getException());
                            return;
                        }

                        // Get FCM token
                        String token = task.getResult();

                        // Log token for testing purposes
                        Log.d(TAG, "FCM Token: " + token);

                        // Here you would typically send this token to your server
                    }
                });

        // Subscribe to topics for relevant notifications
        FirebaseMessaging.getInstance().subscribeToTopic("all_users");

        Log.d(TAG, "Firebase Cloud Messaging initialized");
    }

    /**
     * Create notification channels for Android O+
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            // Transaction notification channel
            NotificationChannel transactionsChannel = new NotificationChannel(
                    CHANNEL_TRANSACTIONS,
                    "Asset Transactions",
                    NotificationManager.IMPORTANCE_HIGH);
            transactionsChannel.setDescription("Notifications for asset sales, transfers, and other transactions");

            // Social notification channel
            NotificationChannel socialChannel = new NotificationChannel(
                    CHANNEL_SOCIAL,
                    "Social Interactions",
                    NotificationManager.IMPORTANCE_DEFAULT);
            socialChannel.setDescription("Notifications for likes, comments, follows, and other social interactions");

            // General notification channel
            NotificationChannel generalChannel = new NotificationChannel(
                    CHANNEL_GENERAL,
                    "General Notifications",
                    NotificationManager.IMPORTANCE_LOW);
            generalChannel.setDescription("General app notifications and updates");

            // Register all channels
            notificationManager.createNotificationChannel(transactionsChannel);
            notificationManager.createNotificationChannel(socialChannel);
            notificationManager.createNotificationChannel(generalChannel);

            Log.d(TAG, "Notification channels created");
        }
    }

    /**
     * Get Firestore instance
     * @return The Firebase Firestore instance
     */
    public FirebaseFirestore getFirestore() {
        return firestore;
    }

    /**
     * Get Firebase Storage instance
     * @return The Firebase Storage instance
     */
    public FirebaseStorage getStorage() {
        return storage;
    }

    /**
     * Get Firebase Auth instance
     * @return The Firebase Authentication instance
     */
    public FirebaseAuth getAuth() {
        return auth;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Low memory condition detected");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_MODERATE) {
            Log.w(TAG, "Trimming memory to level: " + level);
        }
    }
}