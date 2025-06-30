package com.imaginit.hyperplux.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.FragmentPaymentBinding;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.models.PaymentMethod;
import com.imaginit.hyperplux.repositories.AssetRepository;
import com.imaginit.hyperplux.ui.adapters.PaymentMethodAdapter;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.NetworkMonitor;
import com.imaginit.hyperplux.viewmodels.PaymentViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fragment for handling payments
 */
public class PaymentFragment extends Fragment {
    private static final String TAG = "PaymentFragment";
    private static final int REQUEST_CODE_ADD_PAYMENT_METHOD = 1001;

    private FragmentPaymentBinding binding;
    private PaymentViewModel viewModel;
    private PaymentMethodAdapter adapter;
    private AnalyticsTracker analyticsTracker;

    private Asset asset;
    private String selectedPaymentMethodId;
    private boolean isProcessingPayment = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPaymentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize ViewModel properly
            initializeViewModel();

            // Initialize analytics tracker
            analyticsTracker = AnalyticsTracker.getInstance(requireContext());

            // Set up toolbar
            binding.toolbar.setNavigationOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    Navigation.findNavController(v).popBackStack();
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating back: " + e.getMessage());
                    // Try alternate navigation
                    if (getActivity() != null) {
                        getActivity().onBackPressed();
                    }
                }
            });

            // Get asset from arguments
            if (getArguments() != null && getArguments().containsKey("asset")) {
                try {
                    asset = (Asset) getArguments().getSerializable("asset");

                    if (asset != null) {
                        setupAssetDetails();
                        loadPaymentMethods();
                    } else {
                        showError(getString(R.string.asset_not_found));
                        Navigation.findNavController(view).popBackStack();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error getting asset from arguments: " + e.getMessage());
                    showError(getString(R.string.asset_not_found));
                    Navigation.findNavController(view).popBackStack();
                }
            } else {
                showError(getString(R.string.asset_not_found));
                Navigation.findNavController(view).popBackStack();
            }

            // Set up add payment method button
            binding.addPaymentMethodButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    Navigation.findNavController(v)
                            .navigate(R.id.action_paymentFragment_to_addPaymentMethodFragment);
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to add payment method: " + e.getMessage());
                    showError("Error opening payment method screen");
                }
            });

            // Set up payment button
            binding.payButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);

                    NetworkMonitor networkMonitor = NetworkMonitor.getInstance(requireContext());
                    if (networkMonitor != null && networkMonitor.isNetworkAvailable()) {
                        if (selectedPaymentMethodId != null) {
                            if (!isProcessingPayment) {
                                processPayment();
                            }
                        } else {
                            showError(getString(R.string.select_payment_method));
                        }
                    } else {
                        showError(getString(R.string.no_internet_connection));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing payment click: " + e.getMessage());
                    showError("Error processing payment request");
                }
            });

            // Track screen view
            if (analyticsTracker != null) {
                Map<String, Object> params = new HashMap<>();
                if (asset != null) {
                    params.put("asset_id", asset.getId());
                    params.put("asset_name", asset.getName());
                    params.put("price", asset.getAskingPrice());
                }
                analyticsTracker.trackScreenView("Payment", "PaymentFragment");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
            showError("Error initializing payment screen");
            try {
                Navigation.findNavController(view).popBackStack();
            } catch (Exception ne) {
                Log.e(TAG, "Error navigating back after error: " + ne.getMessage());
            }
        }
    }

    private void initializeViewModel() {
        try {
            AppDatabase database = AppDatabase.getDatabase(requireContext());
            AssetRepository assetRepository = new AssetRepository(database.assetDao());
            // Note: Ideally we should create a PaymentRepository, but we'll use null for now
            ViewModelFactory factory = new ViewModelFactory(
                    requireActivity().getApplication(),
                    assetRepository);

            viewModel = new ViewModelProvider(this, factory).get(PaymentViewModel.class);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ViewModel: " + e.getMessage());
            throw e; // Rethrow to be caught by the outer try-catch
        }
    }

    private void setupAssetDetails() {
        try {
            // Set asset image
            String imageUri = asset.getImageUri();
            if (imageUri != null && !imageUri.isEmpty()) {
                // Use Glide to load image into imageView
                Glide.with(requireContext())
                        .load(imageUri)
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.ic_image_placeholder)
                                .error(R.drawable.ic_image_placeholder))
                        .centerCrop()
                        .into(binding.assetImage);
            } else {
                binding.assetImage.setImageResource(R.drawable.ic_image_placeholder);
            }

            // Set asset details
            binding.assetName.setText(asset.getName() != null ? asset.getName() : "");

            // Get seller name
            String sellerName = "Unknown Seller";
            if (asset.getUserId() != null) {
                // In a real implementation, we would load the user's name from a UserRepository
                // For now, just use a placeholder
                sellerName = "Seller #" + asset.getUserId().substring(0, 4);
            }
            binding.sellerName.setText(getString(R.string.sold_by, sellerName));

            // Format and display price
            String currencyCode = asset.getCurrency() != null ? asset.getCurrency() : "USD";
            try {
                double askingPrice = asset.getAskingPrice();
                NumberFormat format = NumberFormat.getCurrencyInstance(Locale.getDefault());
                format.setCurrency(Currency.getInstance(currencyCode));
                binding.assetPrice.setText(format.format(askingPrice));
                binding.totalAmount.setText(format.format(askingPrice));
            } catch (Exception e) {
                Log.e(TAG, "Error formatting price: " + e.getMessage());
                // Fallback if currency code is invalid or other error
                double askingPrice = asset.getAskingPrice();
                binding.assetPrice.setText(String.format("%s %.2f", currencyCode, askingPrice));
                binding.totalAmount.setText(String.format("%s %.2f", currencyCode, askingPrice));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up asset details: " + e.getMessage());
            showError("Error loading asset details");
        }
    }

    private void loadPaymentMethods() {
        try {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.emptyStateContainer.setVisibility(View.GONE);

            // Mock payment methods data since original used Result wrapper
            viewModel.getPaymentMethods().observe(getViewLifecycleOwner(), paymentMethods -> {
                binding.progressBar.setVisibility(View.GONE);

                if (paymentMethods != null) {
                    setupPaymentMethodsList(paymentMethods);
                } else {
                    Log.e(TAG, "Payment methods list is null");
                    binding.paymentMethodsRecyclerView.setVisibility(View.GONE);
                    binding.emptyStateContainer.setVisibility(View.VISIBLE);
                    binding.payButton.setEnabled(false);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error loading payment methods: " + e.getMessage());
            binding.progressBar.setVisibility(View.GONE);
            binding.emptyStateContainer.setVisibility(View.VISIBLE);
            binding.payButton.setEnabled(false);
        }
    }

    private void setupPaymentMethodsList(List<PaymentMethod> paymentMethods) {
        try {
            if (paymentMethods == null || paymentMethods.isEmpty()) {
                binding.paymentMethodsRecyclerView.setVisibility(View.GONE);
                binding.emptyStateContainer.setVisibility(View.VISIBLE);
                binding.payButton.setEnabled(false);
                return;
            }

            binding.paymentMethodsRecyclerView.setVisibility(View.VISIBLE);
            binding.emptyStateContainer.setVisibility(View.GONE);
            binding.payButton.setEnabled(true);

            adapter = new PaymentMethodAdapter(this::onPaymentMethodSelected);
            adapter.submitList(new ArrayList<>(paymentMethods));
            binding.paymentMethodsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            binding.paymentMethodsRecyclerView.setAdapter(adapter);

            // Select the first payment method by default
            if (!paymentMethods.isEmpty() && paymentMethods.get(0).getId() != null) {
                selectedPaymentMethodId = paymentMethods.get(0).getId();
                adapter.setSelectedPaymentMethodId(selectedPaymentMethodId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up payment methods list: " + e.getMessage());
            binding.paymentMethodsRecyclerView.setVisibility(View.GONE);
            binding.emptyStateContainer.setVisibility(View.VISIBLE);
            binding.payButton.setEnabled(false);
        }
    }

    private void onPaymentMethodSelected(PaymentMethod paymentMethod) {
        try {
            if (paymentMethod != null && paymentMethod.getId() != null) {
                selectedPaymentMethodId = paymentMethod.getId();
                performHapticFeedback(binding.getRoot());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error selecting payment method: " + e.getMessage());
        }
    }

    private void processPayment() {
        try {
            if (asset == null || selectedPaymentMethodId == null) {
                return;
            }

            isProcessingPayment = true;
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.payButton.setEnabled(false);

            // Convert to cents (long)
            long amountInCents = (long)(asset.getAskingPrice() * 100);
            String currencyCode = asset.getCurrency() != null ? asset.getCurrency() : "USD";

            // Process the purchase - modified to work without Result wrapper
            viewModel.processPayment(
                    asset.getId(),
                    asset.getUserId(),
                    amountInCents,
                    currencyCode,
                    selectedPaymentMethodId
            ).observe(getViewLifecycleOwner(), transactionResult -> {
                try {
                    isProcessingPayment = false;

                    if (transactionResult.isSuccessful) {
                        String transactionId = transactionResult.transactionId;
                        // Navigate to success screen
                        Bundle args = new Bundle();
                        args.putString("transactionId", transactionId);
                        args.putSerializable("asset", asset);
                        Navigation.findNavController(requireView())
                                .navigate(R.id.action_paymentFragment_to_paymentSuccessFragment, args);

                        // Track successful payment
                        if (analyticsTracker != null) {
                            Map<String, Object> params = new HashMap<>();
                            params.put("asset_id", asset.getId());
                            params.put("amount", asset.getAskingPrice());
                            params.put("currency", currencyCode);
                            params.put("transaction_id", transactionId);
                            analyticsTracker.trackEvent("payment_successful", params);
                        }
                    } else {
                        String errorMessage = transactionResult.errorMessage != null ?
                                transactionResult.errorMessage : getString(R.string.payment_error);
                        Log.e(TAG, "Error processing payment: " + errorMessage);
                        showError(errorMessage);
                        binding.progressBar.setVisibility(View.GONE);
                        binding.payButton.setEnabled(true);

                        // Track payment failure
                        if (analyticsTracker != null) {
                            Map<String, Object> params = new HashMap<>();
                            params.put("asset_id", asset.getId());
                            params.put("error", errorMessage);
                            analyticsTracker.trackEvent("payment_failed", params);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling payment result: " + e.getMessage());
                    showError("Error processing payment");
                    binding.progressBar.setVisibility(View.GONE);
                    binding.payButton.setEnabled(true);
                    isProcessingPayment = false;
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error processing payment: " + e.getMessage());
            showError("Error processing payment");
            binding.progressBar.setVisibility(View.GONE);
            binding.payButton.setEnabled(true);
            isProcessingPayment = false;
        }
    }

    private void performHapticFeedback(View view) {
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Exception e) {
            // Ignore haptic feedback errors
        }
    }

    private void showError(String message) {
        try {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing toast: " + e.getMessage());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * Simple class to handle payment transaction results without using Result wrapper
     */
    public static class TransactionResult {
        public boolean isSuccessful;
        public String transactionId;
        public String errorMessage;

        public static TransactionResult success(String transactionId) {
            TransactionResult result = new TransactionResult();
            result.isSuccessful = true;
            result.transactionId = transactionId;
            return result;
        }

        public static TransactionResult error(String errorMessage) {
            TransactionResult result = new TransactionResult();
            result.isSuccessful = false;
            result.errorMessage = errorMessage;
            return result;
        }
    }
}