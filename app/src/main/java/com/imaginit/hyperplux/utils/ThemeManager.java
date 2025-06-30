package com.imaginit.hyperplux.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Utility class to manage application themes
 */
public class ThemeManager {
    private static final String PREF_NAME = "theme_preferences";
    private static final String KEY_THEME_MODE = "theme_mode";

    public static final int MODE_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    public static final int MODE_LIGHT = AppCompatDelegate.MODE_NIGHT_NO;
    public static final int MODE_DARK = AppCompatDelegate.MODE_NIGHT_YES;

    private static SharedPreferences preferences;

    /**
     * Initialize the ThemeManager
     * @param context Application context
     */
    public static void init(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int savedMode = getSavedThemeMode();

        // Apply the saved theme mode
        AppCompatDelegate.setDefaultNightMode(savedMode);
    }

    /**
     * Set theme mode and save preference
     * @param mode The desired theme mode (MODE_SYSTEM, MODE_LIGHT, MODE_DARK)
     */
    public static void setThemeMode(int mode) {
        // Only apply valid modes
        if (mode == MODE_SYSTEM || mode == MODE_LIGHT || mode == MODE_DARK) {
            // Save preference
            preferences.edit().putInt(KEY_THEME_MODE, mode).apply();

            // Apply the theme
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }

    /**
     * Get the currently saved theme mode
     * @return The saved theme mode
     */
    public static int getSavedThemeMode() {
        // Default to system mode if not set
        return preferences.getInt(KEY_THEME_MODE, getDefaultThemeMode());
    }

    /**
     * Get default theme mode based on Android version
     * @return The default theme mode
     */
    private static int getDefaultThemeMode() {
        // For Android 10+ (API 29+), use system default
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MODE_SYSTEM;
        }
        // For older versions, default to light mode
        return MODE_LIGHT;
    }

    /**
     * Check if dark mode is currently active
     * @param context Application context
     * @return true if dark mode is active
     */
    public static boolean isDarkModeActive(Context context) {
        int currentNightMode = context.getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Toggle between light and dark mode
     */
    public static void toggleDarkMode() {
        int currentMode = getSavedThemeMode();
        if (currentMode == MODE_DARK) {
            setThemeMode(MODE_LIGHT);
        } else {
            setThemeMode(MODE_DARK);
        }
    }
}