package com.imaginit.hyperplux.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Global executor pools for the whole application.
 * <p>
 * Grouping tasks like this avoids the effects of task starvation (e.g. disk reads don't wait behind
 * webservice requests).
 */
public class AppExecutors {
    private static final String TAG = "AppExecutors";
    private static final int THREAD_COUNT = 3;

    private final ExecutorService diskIO;
    private final ExecutorService networkIO;
    private final Executor mainThread;

    private static volatile AppExecutors instance;

    public static AppExecutors getInstance() {
        if (instance == null) {
            synchronized (AppExecutors.class) {
                if (instance == null) {
                    instance = new AppExecutors();
                }
            }
        }
        return instance;
    }

    private AppExecutors() {
        this(Executors.newSingleThreadExecutor(),
                Executors.newFixedThreadPool(THREAD_COUNT),
                new MainThreadExecutor());
    }

    private AppExecutors(ExecutorService diskIO, ExecutorService networkIO, Executor mainThread) {
        this.diskIO = diskIO;
        this.networkIO = networkIO;
        this.mainThread = mainThread;
    }

    public Executor diskIO() {
        return diskIO;
    }

    public Executor networkIO() {
        return networkIO;
    }

    public Executor mainThread() {
        return mainThread;
    }

    /**
     * Shutdown executors properly when application is ending
     * Should be called in Application.onTerminate() method
     */
    public void shutdown() {
        try {
            diskIO.shutdown();
            networkIO.shutdown();

            // Wait up to 5 seconds for tasks to complete
            boolean diskTasksCompleted = diskIO.awaitTermination(5, TimeUnit.SECONDS);
            boolean networkTasksCompleted = networkIO.awaitTermination(5, TimeUnit.SECONDS);

            if (!diskTasksCompleted) {
                Log.w(TAG, "Disk IO tasks didn't complete in time during shutdown");
            }

            if (!networkTasksCompleted) {
                Log.w(TAG, "Network IO tasks didn't complete in time during shutdown");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Error shutting down executor services: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.e(TAG, "Error during executor shutdown: " + e.getMessage());
        }
    }

    /**
     * Force shutdown executors immediately
     * Use only in emergency situations
     */
    public void shutdownNow() {
        try {
            diskIO.shutdownNow();
            networkIO.shutdownNow();
            Log.d(TAG, "Forced immediate executor shutdown");
        } catch (Exception e) {
            Log.e(TAG, "Error during forced executor shutdown: " + e.getMessage());
        }
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            try {
                mainThreadHandler.post(command);
            } catch (Exception e) {
                Log.e(TAG, "Error posting to main thread: " + e.getMessage());
                // If there's an error, try to run on the current thread as a fallback
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    command.run();
                } else {
                    Log.e(TAG, "Could not execute command on main thread");
                }
            }
        }
    }
}