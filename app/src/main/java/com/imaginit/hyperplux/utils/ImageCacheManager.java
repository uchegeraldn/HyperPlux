package com.imaginit.hyperplux.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;
import com.imaginit.hyperplux.R;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Custom Glide module for enhanced image caching and loading
 */
@GlideModule
public class ImageCacheManager extends AppGlideModule {
    private static final String TAG = "ImageCacheManager";
    private static final int DISK_CACHE_SIZE = 250 * 1024 * 1024; // 250 MB
    private static final int TIMEOUT_SECONDS = 30;

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // Set up memory cache
        MemorySizeCalculator calculator = new MemorySizeCalculator.Builder(context)
                .setMemoryCacheScreens(3) // Cache images for 3 screens worth of content
                .build();
        builder.setMemoryCache(new LruResourceCache(calculator.getMemoryCacheSize()));

        // Set up disk cache
        builder.setDiskCache(new ExternalPreferredCacheDiskCacheFactory(
                context, "image_cache", DISK_CACHE_SIZE));

        // Set up default options
        RequestOptions requestOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache all versions of the image
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .centerCrop();

        builder.setDefaultRequestOptions(requestOptions);

        // Set up logging for debug builds
        if (BuildConfig.DEBUG) {
            builder.setLogLevel(Log.VERBOSE);
        }
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        // Use OkHttp for network requests with longer timeouts
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Handle certificate pinning or other security configurations here

        OkHttpClient okHttpClient = clientBuilder.build();
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(okHttpClient));
    }

    /**
     * Utility method to preload asset images
     * @param context Application context
     * @param imageUrls Array of image URLs to preload
     */
    public static void preloadAssetImages(Context context, String[] imageUrls) {
        if (context == null || imageUrls == null) return;

        for (String url : imageUrls) {
            if (url != null && !url.isEmpty()) {
                Glide.with(context)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .preload();
            }
        }
    }

    /**
     * Clears the image cache
     * @param context Application context
     * @param clearDiskCache Whether to clear disk cache as well
     */
    public static void clearCache(Context context, boolean clearDiskCache) {
        if (context == null) return;

        if (clearDiskCache) {
            // Clear disk cache in a background thread
            new Thread(() -> {
                try {
                    Glide.get(context).clearDiskCache();
                } catch (Exception e) {
                    Log.e(TAG, "Error clearing disk cache", e);
                }
            }).start();
        }

        // Clear memory cache immediately on main thread
        try {
            Glide.get(context).clearMemory();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing memory cache", e);
        }
    }
}