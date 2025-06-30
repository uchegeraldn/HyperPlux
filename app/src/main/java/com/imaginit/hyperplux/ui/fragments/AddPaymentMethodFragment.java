package com.imaginit.hyperplux.ui.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.databinding.FragmentAddPaymentMethodBinding;
import com.imaginit.hyperplux.models.PaymentMethod;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.NetworkMonitor;
import com.imaginit.hyperplux.utils.Validator;
import com.imaginit.hyperplux.viewmodels.PaymentViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import java.util.Calendar;
import java.util.Date;

public class AddPaymentMethodFragment extends Fragment {
    private FragmentAddPaymentMethodBinding binding;
    private PaymentViewModel paymentViewModel;
    private PaymentMethod.Type selectedPaymentMethodType = PaymentMethod.Type.CREDIT_CARD;
    private boolean isProcessing = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAddPaymentMethodBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize ViewModel
        try {
            // Assuming PaymentViewModel extends AndroidViewModel
            paymentViewModel = new ViewModelProvider(this).get(PaymentViewModel.class);
        } catch (Exception e) {
            // Fallback if there's any issue with the ViewModel initialization
            Toast.makeText(requireContext(), "Error initializing payment system", Toast.LENGTH_SHORT).show();
        }

        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(requireView()).navigateUp());

        // Setup payment method type spinner
        setupPaymentMethodTypeSpinner();

        // Setup expiry month and year spinners
        setupExpirySpinners();

        // Add text change listeners for validation
        setupTextWatchers();

        // Save button
        binding.saveButton.setOnClickListener(v -> {
            if (isProcessing) return;

            // Check network connectivity
            if (!NetworkMonitor.getInstance(requireContext()).isNetworkAvailable()) {
                Snackbar.make(binding.getRoot(), R.string.no_internet_connection, Snackbar.LENGTH_SHORT).show();
                return;
            }

            // Validate and save payment method
            validateAndSavePaymentMethod();
        });

        // Observe error messages
        paymentViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG).show();
                paymentViewModel.resetState();
            }
        });

        // Track screen view
        AnalyticsTracker tracker = AnalyticsTracker.getInstance(requireContext());
        if (tracker != null) {
            tracker.trackScreenView("add_payment_method_screen", "Add Payment Method");
        }
    }

    private void setupPaymentMethodTypeSpinner() {
        PaymentMethod.Type[] types = PaymentMethod.Type.values();
        String[] typeNames = new String[types.length];

        for (int i = 0; i < types.length; i++) {
            // Use a custom display name method
            typeNames[i] = getDisplayNameForType(types[i]);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                typeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.paymentTypeSpinner.setAdapter(adapter);

        binding.paymentTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPaymentMethodType = PaymentMethod.Type.values()[position];
                updatePaymentMethodTypeUI();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedPaymentMethodType = PaymentMethod.Type.CREDIT_CARD;
                updatePaymentMethodTypeUI();
            }
        });
    }

    // Helper method to get display name for payment type
    private String getDisplayNameForType(PaymentMethod.Type type) {
        switch (type) {
            case CREDIT_CARD:
                return getString(R.string.credit_card);
            case DEBIT_CARD:
                return getString(R.string.debit_card);
            case BANK_ACCOUNT:
                return getString(R.string.bank_account);
            case PAYPAL:
                return "PayPal";
            case CRYPTO:
                return getString(R.string.cryptocurrency);
            default:
                return getString(R.string.other_payment_method);
        }
    }

    private void updatePaymentMethodTypeUI() {
        switch (selectedPaymentMethodType) {
            case CREDIT_CARD:
            case DEBIT_CARD:
                binding.cardDetailsContainer.setVisibility(View.VISIBLE);
                binding.bankAccountContainer.setVisibility(View.GONE);
                binding.otherPaymentContainer.setVisibility(View.GONE);
                break;

            case BANK_ACCOUNT:
                binding.cardDetailsContainer.setVisibility(View.GONE);
                binding.bankAccountContainer.setVisibility(View.VISIBLE);
                binding.otherPaymentContainer.setVisibility(View.GONE);
                break;

            case PAYPAL:
            case CRYPTO:
            default:
                binding.cardDetailsContainer.setVisibility(View.GONE);
                binding.bankAccountContainer.setVisibility(View.GONE);
                binding.otherPaymentContainer.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void setupExpirySpinners() {
        // Month spinner
        Integer[] months = new Integer[12];
        for (int i = 0; i < 12; i++) {
            months[i] = i + 1; // 1-12
        }

        ArrayAdapter<Integer> monthAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                months);
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.expiryMonthSpinner.setAdapter(monthAdapter);

        // Year spinner
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        Integer[] years = new Integer[10]; // Next 10 years
        for (int i = 0; i < 10; i++) {
            years[i] = currentYear + i;
        }

        ArrayAdapter<Integer> yearAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                years);
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.expiryYearSpinner.setAdapter(yearAdapter);
    }

    private void setupTextWatchers() {
        // Card number validation
        binding.cardNumberEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateCardNumber(s.toString());
            }
        });

        // CVV validation
        binding.cvvEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() < 3 || s.length() > 4) {
                    binding.cvvEditText.setError(getString(R.string.invalid_cvv));
                } else {
                    binding.cvvEditText.setError(null);
                }
            }
        });
    }

    private void validateCardNumber(String cardNumber) {
        // Remove spaces and dashes
        String cleanCardNumber = cardNumber.replaceAll("\\s+|-", "");

        if (cleanCardNumber.length() < 13 || cleanCardNumber.length() > 19) {
            binding.cardNumberEditText.setError(getString(R.string.invalid_card_number));
            return;
        }

        // Simple Luhn algorithm check for card number validation
        if (!Validator.isValidCreditCard(cleanCardNumber)) {
            binding.cardNumberEditText.setError(getString(R.string.invalid_card_number));
        } else {
            binding.cardNumberEditText.setError(null);
        }
    }

    private void validateAndSavePaymentMethod() {
        boolean isValid = true;
        String displayName;
        String identifier = null;
        String lastFourDigits = null;
        int expiryMonth = 0;
        int expiryYear = 0;

        switch (selectedPaymentMethodType) {
            case CREDIT_CARD:
            case DEBIT_CARD:
                // Validate card number
                String cardNumber = binding.cardNumberEditText.getText().toString().trim();
                cardNumber = cardNumber.replaceAll("\\s+|-", "");

                if (cardNumber.isEmpty() || !Validator.isValidCreditCard(cardNumber)) {
                    binding.cardNumberEditText.setError(getString(R.string.invalid_card_number));
                    isValid = false;
                }

                // Validate CVV
                String cvv = binding.cvvEditText.getText().toString().trim();
                if (cvv.isEmpty() || cvv.length() < 3 || cvv.length() > 4) {
                    binding.cvvEditText.setError(getString(R.string.invalid_cvv));
                    isValid = false;
                }

                // Validate cardholder name
                String cardholderName = binding.cardholderNameEditText.getText().toString().trim();
                if (cardholderName.isEmpty()) {
                    binding.cardholderNameEditText.setError(getString(R.string.required_field));
                    isValid = false;
                }

                // Set display name and last 4 digits
                if (cardNumber.length() >= 4) {
                    lastFourDigits = cardNumber.substring(cardNumber.length() - 4);
                }

                // Determine card type for display name
                String cardType = Validator.getCardType(cardNumber);
                displayName = cardType + " " +
                        (selectedPaymentMethodType == PaymentMethod.Type.CREDIT_CARD ?
                                getString(R.string.credit_card) : getString(R.string.debit_card));

                // Get expiry date
                expiryMonth = (int) binding.expiryMonthSpinner.getSelectedItem();
                expiryYear = (int) binding.expiryYearSpinner.getSelectedItem();
                break;

            case BANK_ACCOUNT:
                // Validate account number
                String accountNumber = binding.accountNumberEditText.getText().toString().trim();
                if (accountNumber.isEmpty()) {
                    binding.accountNumberEditText.setError(getString(R.string.required_field));
                    isValid = false;
                }

                // Validate routing number
                String routingNumber = binding.routingNumberEditText.getText().toString().trim();
                if (routingNumber.isEmpty()) {
                    binding.routingNumberEditText.setError(getString(R.string.required_field));
                    isValid = false;
                }

                // Validate bank name
                String bankName = binding.bankNameEditText.getText().toString().trim();
                if (bankName.isEmpty()) {
                    binding.bankNameEditText.setError(getString(R.string.required_field));
                    isValid = false;
                }

                // Set display name and identifier
                displayName = bankName + " " + getString(R.string.bank_account);
                if (accountNumber.length() >= 4) {
                    lastFourDigits = accountNumber.substring(accountNumber.length() - 4);
                }
                break;

            case PAYPAL:
            case CRYPTO:
            default:
                // Validate identifier
                identifier = binding.paymentIdentifierEditText.getText().toString().trim();
                if (identifier.isEmpty()) {
                    binding.paymentIdentifierEditText.setError(getString(R.string.required_field));
                    isValid = false;
                }

                // Set display name
                displayName = getDisplayNameForType(selectedPaymentMethodType);
                break;
        }

        if (!isValid) {
            return;
        }

        try {
            // Create payment method using our updated model
            PaymentMethod paymentMethod = new PaymentMethod();
            paymentMethod.setType(selectedPaymentMethodType);
            paymentMethod.setDisplayName(displayName);
            paymentMethod.setIdentifier(identifier);
            paymentMethod.setLast4(lastFourDigits);
            paymentMethod.setExpMonth(expiryMonth);
            paymentMethod.setExpYear(expiryYear);
            paymentMethod.setCreatedAt(new Date());

            // Save payment method
            isProcessing = true;
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.saveButton.setEnabled(false);

            paymentViewModel.addPaymentMethod(paymentMethod);

            // Navigate back after a short delay
            binding.getRoot().postDelayed(() -> {
                if (isAdded() && getActivity() != null) {
                    Navigation.findNavController(requireView()).navigateUp();
                    Toast.makeText(requireContext(), R.string.payment_method_added_successfully, Toast.LENGTH_SHORT).show();
                }
            }, 1500);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error creating payment method: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            binding.progressBar.setVisibility(View.GONE);
            binding.saveButton.setEnabled(true);
            isProcessing = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}