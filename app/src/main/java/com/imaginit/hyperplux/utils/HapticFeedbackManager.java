package com.imaginit.hyperplux.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.RequiresApi;

/**
 * Manages haptic feedback throughout the app
 */
public class HapticFeedbackManager {
    private static final String PREFS_NAME = "haptic_prefs";
    private static final String KEY_HAPTIC_ENABLED = "haptic_enabled";

    private final Context context;
    private final SharedPreferences preferences;
    private final Vibrator vibrator;
    private final boolean hasVibrator;

    // Singleton instance
    private static HapticFeedbackManager instance;

    /**
     * Get the singleton instance of HapticFeedbackManager
     */
    public static synchronized HapticFeedbackManager getInstance(Context context) {
        if (instance == null) {
            instance = new HapticFeedbackManager(context.getApplicationContext());
        }
        return instance;
    }

    private HapticFeedbackManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.hasVibrator = vibrator != null && vibrator.hasVibrator();
    }

    /**
     * Check if haptic feedback is enabled in settings
     */
    public boolean isHapticFeedbackEnabled() {
        return preferences.getBoolean(KEY_HAPTIC_ENABLED, true);
    }

    /**
     * Enable or disable haptic feedback
     */
    public void setHapticFeedbackEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply();
    }

    /**
     * Perform a light click feedback
     */
    public void performLightClick(View view) {
        if (!isHapticFeedbackEnabled() || view == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    /**
     * Perform a normal click feedback
     */
    public void performClick(View view) {
        if (!isHapticFeedbackEnabled() || view == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    /**
     * Perform a long press feedback
     */
    public void performLongPress(View view) {
        if (!isHapticFeedbackEnabled() || view == null) return;

        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    /**
     * Perform a double tap feedback
     */
    public void performDoubleTap(View view) {
        if (!isHapticFeedbackEnabled() || view == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    /**
     * Perform a success feedback (such as when an operation completes successfully)
     */
    public void performSuccess() {
        if (!isHapticFeedbackEnabled() || !hasVibrator) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            performSuccessPatternApi26();
        } else {
            vibrator.vibrate(new long[]{0, 50, 50, 50}, -1);
        }
    }

    /**
     * Perform an error feedback (such as when validation fails)
     */
    public void performError() {
        if (!isHapticFeedbackEnabled() || !hasVibrator) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            performErrorPatternApi26();
        } else {
            vibrator.vibrate(new long[]{0, 100, 50, 100}, -1);
        }
    }

    /**
     * Perform a warning feedback
     */
    public void performWarning() {
        if (!isHapticFeedbackEnabled() || !hasVibrator) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            performWarningPatternApi26();
        } else {
            vibrator.vibrate(new long[]{0, 75, 50, 75}, -1);
        }
    }

    /**
     * Perform a custom vibration pattern
     */
    public void performCustomPattern(long[] pattern, int repeat) {
        if (!isHapticFeedbackEnabled() || !hasVibrator) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat));
        } else {
            vibrator.vibrate(pattern, repeat);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void performSuccessPatternApi26() {
        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{0, 50, 50, 50},
                new int[]{0, 80, 0, 120},
                -1);
        vibrator.vibrate(effect);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void performErrorPatternApi26() {
        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{0, 100, 50, 100},
                new int[]{0, 150, 0, 150},
                -1);
        vibrator.vibrate(effect);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void performWarningPatternApi26() {
        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{0, 75, 50, 75},
                new int[]{0, 120, 0, 120},
                -1);
        vibrator.vibrate(effect);
    }
}