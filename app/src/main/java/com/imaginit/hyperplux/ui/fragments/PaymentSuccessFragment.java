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
import androidx.navigation.Navigation;

import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.databinding.FragmentPaymentSuccessBinding;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.utils.AnalyticsTracker;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PaymentSuccessFragment extends Fragment {
    private static final String TAG = "PaymentSuccessFragment";

    private FragmentPaymentSuccessBinding binding;
    private Asset asset;
    private double amount;
    private String currency = "USD";
    private AnalyticsTracker analyticsTracker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPaymentSuccessBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize analytics tracker
            analyticsTracker = AnalyticsTracker.getInstance(requireContext());

            // Get arguments
            extractArguments();

            // Update UI with payment details
            setupUI();

            // Set up click listeners
            setupClickListeners();

            // Track the payment success event
            trackPaymentSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing payment success screen: " + e.getMessage());
            Toast.makeText(requireContext(), "Error displaying payment details", Toast.LENGTH_SHORT).show();
        }
    }

    private void extractArguments() {
        try {
            if (getArguments() != null) {
                amount = getArguments().getDouble("amount", 0);
                currency = getArguments().getString("currency", "USD");

                // Use getSerializable instead of getParcelable for Asset
                if (getArguments().containsKey("asset")) {
                    asset = (Asset) getArguments().getSerializable("asset");
                }

                // If asset is null but we have assetId and amount wasn't specified
                if (asset != null && amount == 0) {
                    amount = asset.getAskingPrice();
                    currency = asset.getCurrency() != null ? asset.getCurrency() : "USD";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting arguments: " + e.getMessage());
        }
    }

    private void setupUI() {
        try {
            // Format and display amount
            NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.getDefault());
            try {
                formatter.setCurrency(Currency.getInstance(currency));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid currency code: " + currency + ", using USD instead");
                formatter.setCurrency(Currency.getInstance("USD"));
            }
            binding.paymentAmount.setText(formatter.format(amount));

            // Set asset info if available
            if (asset != null) {
                binding.assetCard.setVisibility(View.VISIBLE);
                binding.assetName.setText(asset.getName() != null ? asset.getName() : "Unnamed Asset");
            } else {
                binding.assetCard.setVisibility(View.GONE);
            }

            // Set current date
            binding.paymentDate.setText(android.text.format.DateFormat.getDateFormat(requireContext())
                    .format(System.currentTimeMillis()));
        } catch (Exception e) {
            Log.e(TAG, "Error setting up UI: " + e.getMessage());
        }
    }

    private void setupClickListeners() {
        try {
            // Done button
            binding.doneButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);

                    // Navigate to the home screen
                    Navigation.findNavController(requireView())
                            .navigate(R.id.action_paymentSuccessFragment_to_homeFragment);
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to home: " + e.getMessage());
                    navigateToHome();
                }
            });

            // View transaction button
            binding.viewTransactionButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);

                    // Navigate to transaction details or history
                    Navigation.findNavController(requireView())
                            .navigate(R.id.action_paymentSuccessFragment_to_transactionHistoryFragment);
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to transaction history: " + e.getMessage());
                    Toast.makeText(requireContext(), "Transaction history is not available", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up click listeners: " + e.getMessage());
        }
    }

    private void trackPaymentSuccess() {
        try {
            if (analyticsTracker != null) {
                Map<String, Object> eventParams = new HashMap<>();
                eventParams.put("amount", amount);
                eventParams.put("currency", currency);
                if (asset != null) {
                    eventParams.put("asset_id", asset.getId());
                    if (asset.getName() != null) {
                        eventParams.put("asset_name", asset.getName());
                    }
                }
                analyticsTracker.trackEvent("payment_success_viewed", eventParams);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error tracking payment success: " + e.getMessage());
        }
    }

    private void performHapticFeedback(View view) {
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Exception e) {
            // Ignore haptic feedback errors
        }
    }

    private void navigateToHome() {
        try {
            if (getActivity() != null) {
                // Try to navigate to the main activity if direct navigation fails
                getActivity().finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in fallback navigation: " + e.getMessage());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}