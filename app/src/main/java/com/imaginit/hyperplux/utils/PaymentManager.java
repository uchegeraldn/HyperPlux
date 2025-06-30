package com.imaginit.hyperplux.utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.imaginit.hyperplux.models.PaymentMethod;
import com.imaginit.hyperplux.models.PaymentTransaction;
import com.imaginit.hyperplux.models.Result;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.Stripe;
import com.stripe.android.model.ConfirmPaymentIntentParams;
import com.stripe.android.model.PaymentMethodCreateParams;
import com.stripe.android.view.CardInputWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manager for handling payments using Stripe
 */
public class PaymentManager {
    private static final String TAG = "PaymentManager";
    private static final String PAYMENTS_COLLECTION = "payments";
    private static final String PAYMENT_METHODS_COLLECTION = "paymentMethods";
    private static final String CREATE_PAYMENT_INTENT_FUNCTION = "createPaymentIntent";
    private static final String CONFIRM_ASSET_PURCHASE_FUNCTION = "confirmAssetPurchase";

    private final FirebaseFirestore firestore;
    private final FirebaseFunctions functions;
    private final FirebaseAuth auth;
    private final AnalyticsTracker analyticsTracker;
    private Stripe stripe;

    // Transaction types
    public static final String TRANSACTION_TYPE_ASSET_PURCHASE = "asset_purchase";
    public static final String TRANSACTION_TYPE_SUBSCRIPTION = "subscription";
    public static final String TRANSACTION_TYPE_CREDIT_PURCHASE = "credit_purchase";

    // Transaction statuses
    public static final String TRANSACTION_STATUS_PENDING = "pending";
    public static final String TRANSACTION_STATUS_COMPLETED = "completed";
    public static final String TRANSACTION_STATUS_FAILED = "failed";
    public static final String TRANSACTION_STATUS_REFUNDED = "refunded";

    // Singleton instance
    private static PaymentManager instance;

    /**
     * Get the singleton instance of PaymentManager
     */
    public static synchronized PaymentManager getInstance() {
        if (instance == null) {
            instance = new PaymentManager();
        }
        return instance;
    }

    private PaymentManager() {
        firestore = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance();
        auth = FirebaseAuth.getInstance();
        analyticsTracker = AnalyticsTracker.getInstance();
    }

    /**
     * Initialize Stripe with publishable key
     * @param context Application context
     */
    public void initialize(Context context) {
        // In a real app, you would fetch the publishable key from your server
        // For demo purposes, we'll use a placeholder key
        String publishableKey = "pk_test_YOUR_STRIPE_PUBLISHABLE_KEY";

        PaymentConfiguration.init(context, publishableKey);
        stripe = new Stripe(context, publishableKey);
    }

    /**
     * Add a new payment method for the current user
     * @param activity The current activity
     * @param cardInputWidget The card input widget with card details
     * @return LiveData with the result containing the payment method ID
     */
    public LiveData<Result<String>> addPaymentMethod(Activity activity, CardInputWidget cardInputWidget) {
        MutableLiveData<Result<String>> resultLiveData = new MutableLiveData<>();
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        if (!cardInputWidget.isValid()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("Invalid card details")));
            return resultLiveData;
        }

        // Create params for the payment method
        CardParams cardParams = Objects.requireNonNull(cardInputWidget.getCardParams());
        PaymentMethodCreateParams params = PaymentMethodCreateParams.create(cardParams);

        // Create a payment method
        stripe.createPaymentMethod(params, new com.stripe.android.ApiResultCallback<com.stripe.android.model.PaymentMethod>() {
            @Override
            public void onSuccess(@NonNull com.stripe.android.model.PaymentMethod paymentMethod) {
                // Save payment method ID to Firestore
                PaymentMethod userPaymentMethod = new PaymentMethod();
                userPaymentMethod.setId(paymentMethod.id);
                userPaymentMethod.setUserId(user.getUid());
                userPaymentMethod.setType("card");
                userPaymentMethod.setLast4(paymentMethod.card.getLast4());
                userPaymentMethod.setBrand(paymentMethod.card.getBrand());
                userPaymentMethod.setExpMonth(paymentMethod.card.getExpiryMonth());
                userPaymentMethod.setExpYear(paymentMethod.card.getExpiryYear());
                userPaymentMethod.setCreatedAt(new Date());

                firestore.collection(PAYMENT_METHODS_COLLECTION)
                        .add(userPaymentMethod)
                        .addOnSuccessListener(documentReference -> {
                            resultLiveData.setValue(new Result.Success<>(paymentMethod.id));

                            // Track payment method added
                            Map<String, Object> params = new HashMap<>();
                            params.put("card_brand", paymentMethod.card.getBrand());
                            analyticsTracker.logEvent("payment_method_added", params);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error saving payment method", e);
                            resultLiveData.setValue(new Result.Error<>(e));
                        });
            }

            @Override
            public void onError(@NonNull Exception e) {
                Log.e(TAG, "Error creating payment method", e);
                resultLiveData.setValue(new Result.Error<>(e));
            }
        });

        return resultLiveData;
    }

    /**
     * Process a payment for an asset purchase
     * @param activity The current activity
     * @param assetId The ID of the asset being purchased
     * @param sellerId The ID of the seller
     * @param amount The amount to charge in cents
     * @param currencyCode The currency code (e.g., "USD")
     * @param paymentMethodId The ID of the payment method to use
     * @return LiveData with the result containing the transaction ID
     */
    public LiveData<Result<String>> processAssetPurchase(Activity activity, String assetId,
                                                         String sellerId, long amount,
                                                         String currencyCode, String paymentMethodId) {
        MutableLiveData<Result<String>> resultLiveData = new MutableLiveData<>();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        if (!NetworkMonitor.getInstance().isNetworkAvailable()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("No internet connection")));
            return resultLiveData;
        }

        // Create a new payment transaction
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBuyerId(user.getUid());
        transaction.setSellerId(sellerId);
        transaction.setAssetId(assetId);
        transaction.setAmount(amount);
        transaction.setCurrency(currencyCode);
        transaction.setType(TRANSACTION_TYPE_ASSET_PURCHASE);
        transaction.setStatus(TRANSACTION_STATUS_PENDING);
        transaction.setCreatedAt(new Date());

        // Save transaction to Firestore
        firestore.collection(PAYMENTS_COLLECTION)
                .add(transaction)
                .addOnSuccessListener(documentReference -> {
                    String transactionId = documentReference.getId();
                    transaction.setId(transactionId);

                    // Create a payment intent using Cloud Functions
                    Map<String, Object> data = new HashMap<>();
                    data.put("amount", amount);
                    data.put("currency", currencyCode);
                    data.put("paymentMethodId", paymentMethodId);
                    data.put("transactionId", transactionId);

                    functions.getHttpsCallable(CREATE_PAYMENT_INTENT_FUNCTION)
                            .call(data)
                            .addOnCompleteListener(new OnCompleteListener<HttpsCallableResult>() {
                                @Override
                                public void onComplete(@NonNull Task<HttpsCallableResult> task) {
                                    if (task.isSuccessful()) {
                                        try {
                                            // Parse response and get client secret
                                            Map<String, Object> result = (Map<String, Object>) task.getResult().getData();
                                            String clientSecret = (String) result.get("clientSecret");
                                            String paymentIntentId = (String) result.get("paymentIntentId");

                                            // Update transaction with payment intent ID
                                            DocumentReference transactionRef = firestore.collection(PAYMENTS_COLLECTION)
                                                    .document(transactionId);
                                            transactionRef.update("paymentIntentId", paymentIntentId);

                                            // Confirm the payment intent
                                            ConfirmPaymentIntentParams confirmParams =
                                                    ConfirmPaymentIntentParams.createWithPaymentMethodId(
                                                            paymentMethodId, clientSecret);

                                            stripe.confirmPayment(activity, confirmParams);

                                            // After payment confirmation, we will handle the result in onActivityResult
                                            // For this sample, we'll just return success
                                            resultLiveData

                                            // After payment confirmation, we will handle the result in onActivityResult
                                            // For this sample, we'll just return success
                                            resultLiveData.setValue(new Result.Success<>(transactionId));

                                            // Track payment initiated
                                            Map<String, Object> analyticsParams = new HashMap<>();
                                            analyticsParams.put("amount", amount / 100.0); // Convert cents to dollars
                                            analyticsParams.put("currency", currencyCode);
                                            analyticsParams.put("transaction_type", TRANSACTION_TYPE_ASSET_PURCHASE);
                                            analyticsTracker.logEvent("payment_initiated", analyticsParams);

                                        } catch (Exception e) {
                                            Log.e(TAG, "Error processing payment intent response", e);
                                            resultLiveData.setValue(new Result.Error<>(e));

                                            // Update transaction status
                                            updateTransactionStatus(transactionId, TRANSACTION_STATUS_FAILED);
                                        }
                                    } else {
                                        Exception e = task.getException();
                                        Log.e(TAG, "Error creating payment intent", e);
                                        resultLiveData.setValue(new Result.Error<>(
                                                e != null ? e : new Exception("Unknown error")));

                                        // Update transaction status
                                        updateTransactionStatus(transactionId, TRANSACTION_STATUS_FAILED);
                                    }
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating transaction", e);
                    resultLiveData.setValue(new Result.Error<>(e));
                });

        return resultLiveData;
    }

    /**
     * Process a payment for credits purchase
     * @param activity The current activity
     * @param amount The amount to charge in cents
     * @param currencyCode The currency code (e.g., "USD")
     * @param credits The number of credits being purchased
     * @param paymentMethodId The ID of the payment method to use
     * @return LiveData with the result containing the transaction ID
     */
    public LiveData<Result<String>> purchaseCredits(Activity activity, long amount,
                                                    String currencyCode, int credits,
                                                    String paymentMethodId) {
        MutableLiveData<Result<String>> resultLiveData = new MutableLiveData<>();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        if (!NetworkMonitor.getInstance().isNetworkAvailable()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("No internet connection")));
            return resultLiveData;
        }

        // Create a new payment transaction
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBuyerId(user.getUid());
        transaction.setAmount(amount);
        transaction.setCurrency(currencyCode);
        transaction.setType(TRANSACTION_TYPE_CREDIT_PURCHASE);
        transaction.setCredits(credits);
        transaction.setStatus(TRANSACTION_STATUS_PENDING);
        transaction.setCreatedAt(new Date());

        // Save transaction to Firestore
        firestore.collection(PAYMENTS_COLLECTION)
                .add(transaction)
                .addOnSuccessListener(documentReference -> {
                    String transactionId = documentReference.getId();
                    transaction.setId(transactionId);

                    // Create a payment intent using Cloud Functions
                    Map<String, Object> data = new HashMap<>();
                    data.put("amount", amount);
                    data.put("currency", currencyCode);
                    data.put("paymentMethodId", paymentMethodId);
                    data.put("transactionId", transactionId);
                    data.put("type", TRANSACTION_TYPE_CREDIT_PURCHASE);
                    data.put("credits", credits);

                    functions.getHttpsCallable(CREATE_PAYMENT_INTENT_FUNCTION)
                            .call(data)
                            .addOnCompleteListener(new OnCompleteListener<HttpsCallableResult>() {
                                @Override
                                public void onComplete(@NonNull Task<HttpsCallableResult> task) {
                                    if (task.isSuccessful()) {
                                        try {
                                            // Parse response and get client secret
                                            Map<String, Object> result = (Map<String, Object>) task.getResult().getData();
                                            String clientSecret = (String) result.get("clientSecret");
                                            String paymentIntentId = (String) result.get("paymentIntentId");

                                            // Update transaction with payment intent ID
                                            DocumentReference transactionRef = firestore.collection(PAYMENTS_COLLECTION)
                                                    .document(transactionId);
                                            transactionRef.update("paymentIntentId", paymentIntentId);

                                            // Confirm the payment intent
                                            ConfirmPaymentIntentParams confirmParams =
                                                    ConfirmPaymentIntentParams.createWithPaymentMethodId(
                                                            paymentMethodId, clientSecret);

                                            stripe.confirmPayment(activity, confirmParams);

                                            // After payment confirmation, we will handle the result in onActivityResult
                                            // For this sample, we'll just return success
                                            resultLiveData.setValue(new Result.Success<>(transactionId));

                                            // Track payment initiated
                                            Map<String, Object> analyticsParams = new HashMap<>();
                                            analyticsParams.put("amount", amount / 100.0); // Convert cents to dollars
                                            analyticsParams.put("currency", currencyCode);
                                            analyticsParams.put("transaction_type", TRANSACTION_TYPE_CREDIT_PURCHASE);
                                            analyticsParams.put("credits", credits);
                                            analyticsTracker.logEvent("payment_initiated", analyticsParams);

                                        } catch (Exception e) {
                                            Log.e(TAG, "Error processing payment intent response", e);
                                            resultLiveData.setValue(new Result.Error<>(e));

                                            // Update transaction status
                                            updateTransactionStatus(transactionId, TRANSACTION_STATUS_FAILED);
                                        }
                                    } else {
                                        Exception e = task.getException();
                                        Log.e(TAG, "Error creating payment intent", e);
                                        resultLiveData.setValue(new Result.Error<>(
                                                e != null ? e : new Exception("Unknown error")));

                                        // Update transaction status
                                        updateTransactionStatus(transactionId, TRANSACTION_STATUS_FAILED);
                                    }
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating transaction", e);
                    resultLiveData.setValue(new Result.Error<>(e));
                });

        return resultLiveData;
    }

    /**
     * Update a transaction status
     * @param transactionId The ID of the transaction
     * @param status The new status
     */
    public void updateTransactionStatus(String transactionId, String status) {
        if (transactionId == null || status == null) return;

        firestore.collection(PAYMENTS_COLLECTION)
                .document(transactionId)
                .update("status", status, "updatedAt", new Date())
                .addOnFailureListener(e -> Log.e(TAG, "Error updating transaction status", e));
    }

    /**
     * Confirm an asset purchase transaction
     * @param transactionId The ID of the transaction
     * @return LiveData with the result of the operation
     */
    public LiveData<Result<Boolean>> confirmAssetPurchase(String transactionId) {
        MutableLiveData<Result<Boolean>> resultLiveData = new MutableLiveData<>();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        if (!NetworkMonitor.getInstance().isNetworkAvailable()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("No internet connection")));
            return resultLiveData;
        }

        // Call Cloud Function to confirm the asset purchase
        Map<String, Object> data = new HashMap<>();
        data.put("transactionId", transactionId);
        data.put("userId", user.getUid());

        functions.getHttpsCallable(CONFIRM_ASSET_PURCHASE_FUNCTION)
                .call(data)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        resultLiveData.setValue(new Result.Success<>(true));

                        // Track purchase completed
                        Map<String, Object> params = new HashMap<>();
                        params.put("transaction_id", transactionId);
                        analyticsTracker.logEvent("asset_purchase_completed", params);
                    } else {
                        Exception e = task.getException();
                        Log.e(TAG, "Error confirming asset purchase", e);
                        resultLiveData.setValue(new Result.Error<>(
                                e != null ? e : new Exception("Unknown error")));
                    }
                });

        return resultLiveData;
    }

    /**
     * Get a list of payment methods for the current user
     * @return LiveData with the result containing a list of payment methods
     */
    public LiveData<Result<List<PaymentMethod>>> getPaymentMethods() {
        MutableLiveData<Result<List<PaymentMethod>>> resultLiveData = new MutableLiveData<>();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        firestore.collection(PAYMENT_METHODS_COLLECTION)
                .whereEqualTo("userId", user.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<PaymentMethod> paymentMethods = new ArrayList<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        PaymentMethod paymentMethod = document.toObject(PaymentMethod.class);
                        if (paymentMethod != null) {
                            paymentMethod.setId(document.getId());
                            paymentMethods.add(paymentMethod);
                        }
                    }
                    resultLiveData.setValue(new Result.Success<>(paymentMethods));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting payment methods", e);
                    resultLiveData.setValue(new Result.Error<>(e));
                });

        return resultLiveData;
    }

    /**
     * Delete a payment method
     * @param paymentMethodId The ID of the payment method to delete
     * @return LiveData with the result of the operation
     */
    public LiveData<Result<Boolean>> deletePaymentMethod(String paymentMethodId) {
        MutableLiveData<Result<Boolean>> resultLiveData = new MutableLiveData<>();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        firestore.collection(PAYMENT_METHODS_COLLECTION)
                .whereEqualTo("id", paymentMethodId)
                .whereEqualTo("userId", user.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot document = queryDocumentSnapshots.getDocuments().get(0);
                        document.getReference().delete()
                                .addOnSuccessListener(aVoid -> {
                                    resultLiveData.setValue(new Result.Success<>(true));

                                    // Track payment method deleted
                                    analyticsTracker.logEvent("payment_method_deleted", null);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error deleting payment method", e);
                                    resultLiveData.setValue(new Result.Error<>(e));
                                });
                    } else {
                        resultLiveData.setValue(new Result.Error<>(
                                new Exception("Payment method not found")));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error finding payment method", e);
                    resultLiveData.setValue(new Result.Error<>(e));
                });

        return resultLiveData;
    }

    /**
     * Get transaction history for the current user
     * @return LiveData with the result containing a list of transactions
     */
    public LiveData<Result<List<PaymentTransaction>>> getTransactionHistory() {
        MutableLiveData<Result<List<PaymentTransaction>>> resultLiveData = new MutableLiveData<>();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        firestore.collection(PAYMENTS_COLLECTION)
                .whereEqualTo("buyerId", user.getUid())
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<PaymentTransaction> transactions = new ArrayList<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        PaymentTransaction transaction = document.toObject(PaymentTransaction.class);
                        if (transaction != null) {
                            transaction.setId(document.getId());
                            transactions.add(transaction);
                        }
                    }

                    // Also get transactions where user is the seller
                    firestore.collection(PAYMENTS_COLLECTION)
                            .whereEqualTo("sellerId", user.getUid())
                            .orderBy("createdAt", Query.Direction.DESCENDING)
                            .get()
                            .addOnSuccessListener(sellerSnapshots -> {
                                for (DocumentSnapshot document : sellerSnapshots.getDocuments()) {
                                    PaymentTransaction transaction = document.toObject(PaymentTransaction.class);
                                    if (transaction != null) {
                                        transaction.setId(document.getId());
                                        transactions.add(transaction);
                                    }
                                }

                                // Sort all transactions by date
                                Collections.sort(transactions, (t1, t2) ->
                                        t2.getCreatedAt().compareTo(t1.getCreatedAt()));

                                resultLiveData.setValue(new Result.Success<>(transactions));
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error getting seller transactions", e);
                                resultLiveData.setValue(new Result.Success<>(transactions)); // Still return buyer transactions
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting transaction history", e);
                    resultLiveData.setValue(new Result.Error<>(e));
                });

        return resultLiveData;
    }
}