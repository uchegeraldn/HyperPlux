package com.imaginit.hyperplux.database;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import android.content.Context;
import android.util.Log;

import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.models.AssetTransaction;
import com.imaginit.hyperplux.models.User;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                Asset.class,
                User.class,
                AssetTransaction.class
        },
        version = 2,
        exportSchema = true
)
@TypeConverters({DateConverter.class, StringListConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private static final String TAG = "AppDatabase";

    // Number of threads for database operations
    private static final int NUMBER_OF_THREADS = 4;

    // Executor service for background operations
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    // Define DAOs
    public abstract AssetDao assetDao();
    public abstract UserDao userDao();
    public abstract AssetTransactionDao assetTransactionDao();

    // Database singleton instance
    private static volatile AppDatabase INSTANCE;

    // Define migrations
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            try {
                // Add asset engagementScore column
                database.execSQL("ALTER TABLE assets ADD COLUMN engagementScore REAL NOT NULL DEFAULT 0.0");

                // Add additionalImageUris column as JSON array string
                database.execSQL("ALTER TABLE assets ADD COLUMN additionalImageUris TEXT");

                Log.d(TAG, "Migration from version 1 to 2 completed");
            } catch (Exception e) {
                Log.e(TAG, "Error during migration 1-2: " + e.getMessage(), e);
                // Migration failures are critical - we should inform a monitoring service
                // or handle appropriately instead of just swallowing the exception
            }
        }
    };

    // Array of all migrations for easier management
    private static final Migration[] ALL_MIGRATIONS = new Migration[]{
            MIGRATION_1_2
    };

    public static AppDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "hyperplux_database")
                            // Add all migrations
                            .addMigrations(ALL_MIGRATIONS)
                            // Fallback to destructive migration as last resort
                            .fallbackToDestructiveMigration()
                            // Add callback for database creation/opening
                            .addCallback(new Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    Log.d(TAG, "Database created");

                                    // You could prepopulate the database here if needed
                                    // databaseWriteExecutor.execute(() -> {
                                    //     // Add initial data
                                    // });
                                }

                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    Log.d(TAG, "Database opened");
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Close database and clear instance
     * (useful for testing and account switching)
     */
    public static void closeDatabase() {
        if (INSTANCE != null) {
            // Only try to close if it's open to avoid exceptions
            if (INSTANCE.isOpen()) {
                INSTANCE.close();
            }
            INSTANCE = null;
            Log.d(TAG, "Database closed and instance cleared");
        }
    }

    /**
     * Shutdown the executor service
     * Call this method when the application is shutting down
     */
    public static void shutdownExecutor() {
        if (!databaseWriteExecutor.isShutdown()) {
            databaseWriteExecutor.shutdown();
            Log.d(TAG, "Database executor service shutdown");
        }
    }
}