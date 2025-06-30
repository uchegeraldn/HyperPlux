package com.imaginit.hyperplux.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.imaginit.hyperplux.models.PaymentMethod;
import com.imaginit.hyperplux.models.PaymentTransaction;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.AppExecutors;
import com.imaginit.hyperplux.utils.PaymentManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaymentViewModel extends AndroidViewModel {

    private final MutableLiveData<List<PaymentMethod>> paymentMethods = new MutableLiveData<>();
    private final MutableLiveData<PaymentProcessingState> paymentProcessingState = new MutableLiveData<>(PaymentProcessingState.IDLE);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<PaymentTransaction> lastTransaction = new MutableLiveData<>();
    private final MutableLiveData<List<PaymentTransaction>> transactionHistory = new MutableLiveData<>();

    private final PaymentManager paymentManager;
    private final FirebaseAuth auth;

    public enum PaymentProcessingState {
        IDLE, PROCESSING, SUCCESS, ERROR
    }

    public PaymentViewModel(@NonNull Application application) {
        super(application);
        this.paymentManager = PaymentManager.getInstance(application.getApplicationContext());
        this.auth = FirebaseAuth.getInstance();

        // Initialize with default state
        paymentProcessingState.setValue(PaymentProcessingState.IDLE);
    }

    public LiveData<List<PaymentMethod>> getPaymentMethods() {
        return paymentMethods;
    }

    public LiveData<PaymentProcessingState> getPaymentProcessingState() {
        return paymentProcessingState;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<PaymentTransaction> getLastTransaction() {
        return lastTransaction;
    }

    public LiveData<List<PaymentTransaction>> getTransactionHistory() {
        return transactionHistory;
    }

    public void loadPaymentMethods() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            paymentMethods.setValue(new ArrayList<>());
            return;
        }

        AppExecutors.getInstance().networkIO().execute(() -> {
            paymentManager.getPaymentMethods(currentUser.getUid(), methods -> {
                paymentMethods.postValue(methods);
            }, error -> {
                paymentMethods.postValue(new ArrayList<>());
                errorMessage.postValue(error);
            });
        });
    }

    public void addPaymentMethod(PaymentMethod paymentMethod) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            errorMessage.setValue("User not authenticated");
            return;
        }

        paymentMethod.setUserId(currentUser.getUid());

        AppExecutors.getInstance().networkIO().execute(() -> {
            paymentManager.addPaymentMethod(paymentMethod, success -> {
                // Reload payment methods
                loadPaymentMethods();

                // Track event
                Map<String, Object> eventParams = new HashMap<>();
                PaymentMethod.Type type = paymentMethod.getType();
                eventParams.put("payment_type", type != null ? type.name() : "UNKNOWN");
                AnalyticsTracker.getInstance().trackEvent(
                        "payment_method_added", eventParams);

            }, error -> {
                errorMessage.postValue(error);
            });
        });
    }

    public void removePaymentMethod(String paymentMethodId) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            errorMessage.setValue("User not authenticated");
            return;
        }

        AppExecutors.getInstance().networkIO().execute(() -> {
            paymentManager.removePaymentMethod(paymentMethodId, currentUser.getUid(), success -> {
                // Reload payment methods
                loadPaymentMethods();

                // Track event
                Map<String, Object> eventParams = new HashMap<>();
                eventParams.put("payment_method_id", paymentMethodId);
                AnalyticsTracker.getInstance().trackEvent(
                        "payment_method_removed", eventParams);

            }, error -> {
                errorMessage.postValue(error);
            });
        });
    }

    public void processPayment(PaymentTransaction transaction) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            errorMessage.setValue("User not authenticated");
            return;
        }

        // Set processing state
        paymentProcessingState.setValue(PaymentProcessingState.PROCESSING);

        // Set user ID
        transaction.setUserId(currentUser.getUid());

        AppExecutors.getInstance().networkIO().execute(() -> {
            paymentManager.processPayment(transaction, result -> {
                // Set success state
                paymentProcessingState.postValue(PaymentProcessingState.SUCCESS);

                // Store the transaction
                lastTransaction.postValue(result);

                // Track payment success
                Map<String, Object> eventParams = new HashMap<>();
                eventParams.put("amount", transaction.getAmount());
                eventParams.put("currency", transaction.getCurrency());
                eventParams.put("payment_method_id", transaction.getPaymentMethodId());
                if (transaction.getAssetId() > 0) {
                    eventParams.put("asset_id", transaction.getAssetId());
                }
                AnalyticsTracker.getInstance().trackEvent(
                        "payment_successful", eventParams);

            }, error -> {
                // Set error state
                paymentProcessingState.postValue(PaymentProcessingState.ERROR);
                errorMessage.postValue(error);

                // Track payment failure
                Map<String, Object> eventParams = new HashMap<>();
                eventParams.put("amount", transaction.getAmount());
                eventParams.put("currency", transaction.getCurrency());
                eventParams.put("payment_method_id", transaction.getPaymentMethodId());
                eventParams.put("error", error);
                if (transaction.getAssetId() > 0) {
                    eventParams.put("asset_id", transaction.getAssetId());
                }
                AnalyticsTracker.getInstance().trackEvent(
                        "payment_failed", eventParams);
            });
        });
    }

    public void loadTransactionHistory() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            transactionHistory.setValue(new ArrayList<>());
            return;
        }

        AppExecutors.getInstance().networkIO().execute(() -> {
            paymentManager.getTransactionHistory(currentUser.getUid(), transactions -> {
                transactionHistory.postValue(transactions);
            }, error -> {
                transactionHistory.postValue(new ArrayList<>());
                errorMessage.postValue(error);
            });
        });
    }

    public void resetState() {
        paymentProcessingState.setValue(PaymentProcessingState.IDLE);
        errorMessage.setValue(null);
    }
}