package com.imaginit.hyperplux.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class for image compression and processing
 */
public class ImageCompressor {
    private static final String TAG = "ImageCompressor";
    private static final int MAX_WIDTH = 1024;
    private static final int MAX_HEIGHT = 1024;
    private static final int QUALITY = 85;

    /**
     * Interface for compression callback
     */
    public interface CompressionCallback {
        void onCompressed(Uri compressedImageUri);
        void onError(Exception e);
    }

    /**
     * Compress image from Uri asynchronously
     *
     * @param context Application context
     * @param imageUri Original image Uri
     * @param callback Callback for result
     */
    public static void compressImage(Context context, Uri imageUri, CompressionCallback callback) {
        new AsyncTask<Void, Void, Uri>() {
            private Exception exception;

            @Override
            protected Uri doInBackground(Void... voids) {
                try {
                    return compressImageSync(context, imageUri);
                } catch (Exception e) {
                    exception = e;
                    Log.e(TAG, "Error compressing image", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Uri result) {
                if (result != null) {
                    callback.onCompressed(result);
                } else {
                    callback.onError(exception);
                }
            }
        }.execute();
    }

    /**
     * Synchronous image compression
     *
     * @param context Application context
     * @param imageUri Original image Uri
     * @return Uri of compressed image
     * @throws IOException if compression fails
     */
    public static Uri compressImageSync(Context context, Uri imageUri) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
        if (inputStream == null) {
            throw new IOException("Cannot open input stream for Uri: " + imageUri);
        }

        // Decode image dimensions
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, options);
        inputStream.close();

        // Calculate sample size for downsampling
        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight);
        options.inJustDecodeBounds = false;

        // Decode with sample size
        inputStream = context.getContentResolver().openInputStream(imageUri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
        inputStream.close();

        if (bitmap == null) {
            throw new IOException("Failed to decode bitmap from Uri: " + imageUri);
        }

        // Fix orientation if needed
        bitmap = fixOrientation(context, bitmap, imageUri);

        // Create temp file for compressed image
        File compressedFile = new File(context.getCacheDir(), "compressed_" + System.currentTimeMillis() + ".jpg");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Compress to JPEG
        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, outputStream);

        // Write to file
        FileOutputStream fileOutputStream = new FileOutputStream(compressedFile);
        fileOutputStream.write(outputStream.toByteArray());
        fileOutputStream.close();

        // Recycle bitmap to free memory
        bitmap.recycle();

        // Return Uri for the compressed file
        return Uri.fromFile(compressedFile);
    }

    /**
     * Calculate appropriate sample size for downsampling
     */
    private static int calculateSampleSize(int width, int height) {
        int sampleSize = 1;

        while (width / sampleSize > MAX_WIDTH || height / sampleSize > MAX_HEIGHT) {
            sampleSize *= 2;
        }

        return sampleSize;
    }

    /**
     * Fix image orientation based on EXIF data
     */
    private static Bitmap fixOrientation(Context context, Bitmap bitmap, Uri imageUri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                return bitmap;
            }

            ExifInterface exif = new ExifInterface(inputStream);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.preScale(-1.0f, 1.0f);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.preScale(1.0f, -1.0f);
                    break;
                default:
                    return bitmap;
            }

            inputStream.close();

            // Create a new bitmap with the rotation applied
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (IOException e) {
            Log.e(TAG, "Error fixing image orientation", e);
            return bitmap;
        }
    }
}