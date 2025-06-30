// Create DatabaseMigrationTest.java for testing migrations

package com.imaginit.hyperplux;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

import com.imaginit.hyperplux.database.AppDatabase;

@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {
    private static final String TEST_DB = "migration-test";

    // Helper for testing migrations
    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class,
            new Room.DatabaseConfiguration(
                    ApplicationProvider.getApplicationContext(),
                    null
            ),
            new FrameworkSQLiteOpenHelperFactory());

    @Test
    public void migrate1To2() throws IOException {
        // Create version 1 database with test data
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1);

        // Insert test asset in version 1 format
        db.execSQL("INSERT INTO assets (id, name, quantity, userId, views, likes, dislikes) " +
                "VALUES (1, 'Test Asset', 1, 'testUser', 10, 5, 1)");
        db.close();

        // Migrate to version 2
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2);

        // Verify migration worked
        Cursor cursor = db.query("SELECT * FROM assets WHERE id = 1");
        cursor.moveToFirst();

        // Verify existing data preserved
        assertEquals("Test Asset", cursor.getString(cursor.getColumnIndex("name")));
        assertEquals(1, cursor.getInt(cursor.getColumnIndex("quantity")));
        assertEquals("testUser", cursor.getString(cursor.getColumnIndex("userId")));

        // Verify new columns added with default values
        assertEquals(0.0, cursor.getDouble(cursor.getColumnIndex("engagementScore")), 0.001);

        cursor.close();
    }

    @Test
    public void testAllMigrations() throws IOException {
        // Test all migrations from first version to latest
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, TEST_DB)
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .build();

        // Verify database is successfully created after all migrations
        db.getOpenHelper().getReadableDatabase();
        db.close();
    }
}