package com.imaginit.hyperplux.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.imaginit.hyperplux.models.ReceiptData;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages OCR (Optical Character Recognition) operations for receipts
 */
public class OcrManager {
    private static final String TAG = "OcrManager";

    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?)");
    private static final Pattern DATE_PATTERNS = Pattern.compile(
            // MM/DD/YYYY
            "(0?[1-9]|1[0-2])[/.-](0?[1-9]|[12]\\d|3[01])[/.-](19|20)\\d{2}|" +
                    // DD/MM/YYYY
                    "(0?[1-9]|[12]\\d|3[01])[/.-](0?[1-9]|1[0-2])[/.-](19|20)\\d{2}|" +
                    // YYYY/MM/DD
                    "(19|20)\\d{2}[/.-](0?[1-9]|1[0-2])[/.-](0?[1-9]|[12]\\d|3[01])"
    );

    private static final String[] TOTAL_KEYWORDS = {
            "total", "amount", "grand total", "balance", "sum", "payment", "final"
    };

    private static final String[] DATE_KEYWORDS = {
            "date:", "date", "invoice date", "receipt date", "transaction date", "purchase date"
    };

    private static final String[] BUSINESS_KEYWORDS = {
            "store:", "store", "merchant:", "merchant", "business:", "business", "company:"
    };

    private static final String[] ITEM_KEYWORDS = {
            "item", "product", "description", "qty", "quantity", "article"
    };

    private final Context context;
    private final FirebaseVisionTextRecognizer textRecognizer;
    private final AnalyticsTracker analyticsTracker;

    // Singleton instance
    private static OcrManager instance;

    /**
     * Get the singleton instance of OcrManager
     */
    public static synchronized OcrManager getInstance(Context context) {
        if (instance == null) {
            instance = new OcrManager(context.getApplicationContext());
        }
        return instance;
    }

    private OcrManager(Context context) {
        this.context = context;
        this.textRecognizer = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
        this.analyticsTracker = AnalyticsTracker.getInstance();
    }

    /**
     * Process an image from a URI for OCR
     * @param imageUri The URI of the image
     * @param callback The callback to receive the result
     */
    public void processReceiptImage(Uri imageUri, OcrCallback callback) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), imageUri);
            processReceiptImage(bitmap, callback);
        } catch (IOException e) {
            Log.e(TAG, "Error loading image from URI", e);
            callback.onError("Error loading image: " + e.getMessage());
        }
    }

    /**
     * Process a bitmap image for OCR
     * @param bitmap The bitmap image
     * @param callback The callback to receive the result
     */
    public void processReceiptImage(Bitmap bitmap, OcrCallback callback) {
        if (bitmap == null) {
            callback.onError("Image is null");
            return;
        }

        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);

        textRecognizer.processImage(image)
                .addOnSuccessListener(firebaseVisionText -> {
                    // Parse the recognized text
                    ReceiptData receiptData = parseReceiptText(firebaseVisionText);

                    // Track success
                    Map<String, Object> params = new HashMap<>();
                    params.put("text_blocks", firebaseVisionText.getTextBlocks().size());
                    params.put("found_total", receiptData.getTotal() > 0);
                    params.put("found_date", receiptData.getDate() != null);
                    params.put("found_store", receiptData.getStore() != null);
                    params.put("items_found", receiptData.getItems().size());
                    analyticsTracker.logEvent("ocr_receipt_success", params);

                    callback.onSuccess(receiptData);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error processing OCR", e);

                    // Track failure
                    Map<String, Object> params = new HashMap<>();
                    params.put("error", e.getClass().getSimpleName());
                    analyticsTracker.logEvent("ocr_receipt_failure", params);

                    callback.onError("Error processing image: " + e.getMessage());
                });
    }

    /**
     * Process text asynchronously for OCR
     * @param text The text to process
     * @return Task with the receipt data
     */
    public Task<ReceiptData> processReceiptText(String text) {
        return textRecognizer.processText(FirebaseVisionImage.fromBitmap(textToBitmap(text)))
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        return parseReceiptText(task.getResult());
                    } else {
                        throw new Exception("Text processing failed");
                    }
                });
    }

    /**
     * Parse the receipt text into structured data
     * @param firebaseVisionText The OCR result
     * @return Structured receipt data
     */
    private ReceiptData parseReceiptText(FirebaseVisionText firebaseVisionText) {
        ReceiptData receiptData = new ReceiptData();

        String fullText = firebaseVisionText.getText().toLowerCase();

        // Extract total
        double total = extractTotal(fullText, firebaseVisionText);
        receiptData.setTotal(total);

        // Extract date
        Date date = extractDate(fullText, firebaseVisionText);
        receiptData.setDate(date);

        // Extract store name
        String store = extractStoreName(fullText, firebaseVisionText);
        receiptData.setStore(store);

        // Extract items
        List<String> items = extractItems(fullText, firebaseVisionText);
        receiptData.setItems(items);

        // Set full text for reference
        receiptData.setFullText(firebaseVisionText.getText());

        return receiptData;
    }

    /**
     * Extract the total amount from the receipt
     */
    private double extractTotal(String fullText, FirebaseVisionText visionText) {
        double maxAmount = 0;
        double potentialTotal = 0;

        // First try to find a line with "total" keyword
        for (FirebaseVisionText.TextBlock block : visionText.getTextBlocks()) {
            for (FirebaseVisionText.Line line : block.getLines()) {
                String lineText = line.getText().toLowerCase();

                for (String keyword : TOTAL_KEYWORDS) {
                    if (lineText.contains(keyword)) {
                        Matcher matcher = PRICE_PATTERN.matcher(lineText);
                        if (matcher.find()) {
                            String priceStr = matcher.group(1).replace(",", "");
                            try {
                                double price = Double.parseDouble(priceStr);
                                if (price > potentialTotal) {
                                    potentialTotal = price;
                                }
                            } catch (NumberFormatException e) {
                                Log.w(TAG, "Error parsing price: " + priceStr);
                            }
                        }
                    }
                }
            }
        }

        // If we found a potential total with a keyword, return it
        if (potentialTotal > 0) {
            return potentialTotal;
        }

        // Otherwise, find the largest amount on the receipt
        for (FirebaseVisionText.TextBlock block : visionText.getTextBlocks()) {
            for (FirebaseVisionText.Line line : block.getLines()) {
                Matcher matcher = PRICE_PATTERN.matcher(line.getText());
                while (matcher.find()) {
                    String priceStr = matcher.group(1).replace(",", "");
                    try {
                        double price = Double.parseDouble(priceStr);
                        if (price > maxAmount) {
                            maxAmount = price;
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Error parsing price: " + priceStr);
                    }
                }
            }
        }

        return maxAmount;
    }

    /**
     * Extract the date from the receipt
     */
    private Date extractDate(String fullText, FirebaseVisionText visionText) {
        // First try to find a line with a date keyword
        for (FirebaseVisionText.TextBlock block : visionText.getTextBlocks()) {
            for (FirebaseVisionText.Line line : block.getLines()) {
                String lineText = line.getText().toLowerCase();

                for (String keyword : DATE_KEYWORDS) {
                    if (lineText.contains(keyword)) {
                        Date date = tryParseDate(lineText);
                        if (date != null) {
                            return date;
                        }
                    }
                }
            }
        }

        // If no date found with keywords, scan all text for date patterns
        Matcher matcher = DATE_PATTERNS.matcher(fullText);
        if (matcher.find()) {
            String dateStr = matcher.group(0);
            return tryParseDate(dateStr);
        }

        return null;
    }

    /**
     * Try to parse a date from a string
     */
    private Date tryParseDate(String text) {
        Matcher matcher = DATE_PATTERNS.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String dateStr = matcher.group(0);

        // Try different date formats
        String[] dateFormats = {
                "MM/dd/yyyy", "dd/MM/yyyy", "yyyy/MM/dd",
                "MM-dd-yyyy", "dd-MM-yyyy", "yyyy-MM-dd",
                "MM.dd.yyyy", "dd.MM.yyyy", "yyyy.MM.dd"
        };

        for (String format : dateFormats) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.US);
                dateFormat.setLenient(false);
                return dateFormat.parse(dateStr);
            } catch (ParseException e) {
                // Try next format
            }
        }

        return null;
    }

    /**
     * Extract the store name from the receipt
     */
    private String extractStoreName(String fullText, FirebaseVisionText visionText) {
        // Look for store name in the first few lines
        List<String> firstLines = new ArrayList<>();
        int lineCount = 0;

        for (FirebaseVisionText.TextBlock block : visionText.getTextBlocks()) {
            for (FirebaseVisionText.Line line : block.getLines()) {
                String lineText = line.getText().trim();
                if (!lineText.isEmpty()) {
                    firstLines.add(lineText);
                    lineCount++;
                    if (lineCount >= 5) break;
                }
            }
            if (lineCount >= 5) break;
        }

        // Check if any of the first lines contains a business keyword
        for (String line : firstLines) {
            for (String keyword : BUSINESS_KEYWORDS) {
                if (line.toLowerCase().contains(keyword)) {
                    // Extract the store name after the keyword
                    int index = line.toLowerCase().indexOf(keyword) + keyword.length();
                    if (index < line.length()) {
                        String storeName = line.substring(index).trim();
                        if (!storeName.isEmpty() && !storeName.equals(":")) {
                            return storeName;
                        }
                    }
                }
            }
        }

        // If no keyword found, return the first non-empty line
        for (String line : firstLines) {
            if (!line.isEmpty() && !isDateOrAmount(line)) {
                return line;
            }
        }

        return null;
    }

    /**
     * Check if a string is a date or amount
     */
    private boolean isDateOrAmount(String text) {
        return DATE_PATTERNS.matcher(text).find() || PRICE_PATTERN.matcher(text).find();
    }

    /**
     * Extract items from the receipt
     */
    private List<String> extractItems(String fullText, FirebaseVisionText visionText) {
        List<String> items = new ArrayList<>();
        boolean inItemSection = false;

        // Try to find the item section
        for (FirebaseVisionText.TextBlock block : visionText.getTextBlocks()) {
            for (FirebaseVisionText.Line line : block.getLines()) {
                String lineText = line.getText().toLowerCase();

                // Check if we're entering an item section
                if (!inItemSection) {
                    for (String keyword : ITEM_KEYWORDS) {
                        if (lineText.contains(keyword)) {
                            inItemSection = true;
                            break;
                        }
                    }
                    continue;
                }

                // Skip lines that look like totals
                boolean isTotal = false;
                for (String keyword : TOTAL_KEYWORDS) {
                    if (lineText.contains(keyword)) {
                        isTotal = true;
                        break;
                    }
                }

                if (isTotal) {
                    continue;
                }

                // If the line has a price pattern and doesn't look like a date, it might be an item
                Matcher priceMatcher = PRICE_PATTERN.matcher(lineText);
                if (priceMatcher.find() && !DATE_PATTERNS.matcher(lineText).find()) {
                    // Extract the item name (everything before the price)
                    String itemName = lineText.substring(0, priceMatcher.start()).trim();
                    if (!itemName.isEmpty() && itemName.length() > 2) {
                        items.add(itemName);
                    }
                }
            }
        }

        return items;
    }

    /**
     * Convert text to a bitmap image (for testing)
     */
    private Bitmap textToBitmap(String text) {
        // This is a simplified implementation for testing
        // In a real app, you would render text onto a bitmap with proper formatting
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setTextSize(36);
        paint.setColor(android.graphics.Color.BLACK);
        paint.setStyle(android.graphics.Paint.Style.FILL);
        paint.setAntiAlias(true);

        int width = 1000;
        int height = 500;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        canvas.drawColor(android.graphics.Color.WHITE);

        String[] lines = text.split("\n");
        float y = 50;
        for (String line : lines) {
            canvas.drawText(line, 50, y, paint);
            y += 40;
        }

        return bitmap;
    }

    /**
     * Callback interface for OCR operations
     */
    public interface OcrCallback {
        void onSuccess(ReceiptData receiptData);
        void onError(String errorMessage);
    }
}