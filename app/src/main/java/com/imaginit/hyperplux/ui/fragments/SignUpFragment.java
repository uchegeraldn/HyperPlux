package com.imaginit.hyperplux.ui.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.FragmentSignUpBinding;
import com.imaginit.hyperplux.models.User;
import com.imaginit.hyperplux.repositories.UserRepository;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.NetworkMonitor;
import com.imaginit.hyperplux.viewmodels.UserViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Fragment for handling user registration
 */
public class SignUpFragment extends Fragment {
    private static final String TAG = "SignUpFragment";

    private FragmentSignUpBinding binding;
    private UserViewModel viewModel;
    private AnalyticsTracker analyticsTracker;
    private NetworkMonitor networkMonitor;
    private boolean isProcessing = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSignUpBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize ViewModel properly
            initializeViewModel();

            // Initialize utilities
            analyticsTracker = AnalyticsTracker.getInstance(requireContext());
            networkMonitor = NetworkMonitor.getInstance(requireContext());

            // Set up UI components
            setupPasswordStrengthIndicator();
            setupPasswordVisibilityToggle();
            setupClickListeners();

            // Monitor network state
            if (networkMonitor != null) {
                networkMonitor.getNetworkAvailability().observe(getViewLifecycleOwner(), isAvailable -> {
                    if (!isAvailable) {
                        Snackbar.make(binding.getRoot(), R.string.no_internet_connection, Snackbar.LENGTH_LONG).show();
                    }
                });
            }

            // Track screen view
            if (analyticsTracker != null) {
                analyticsTracker.trackScreenView("SignUp", "SignUpFragment");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
            Snackbar.make(binding.getRoot(), "Error initializing sign up screen", Snackbar.LENGTH_LONG).show();
        }
    }

    private void initializeViewModel() {
        try {
            AppDatabase database = AppDatabase.getDatabase(requireContext());
            UserRepository userRepository = new UserRepository(database.userDao());
            ViewModelFactory factory = new ViewModelFactory(
                    requireActivity().getApplication(),
                    null,
                    userRepository);

            viewModel = new ViewModelProvider(this, factory).get(UserViewModel.class);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ViewModel: " + e.getMessage());
            throw e; // Rethrow to be caught by the outer try-catch
        }
    }

    private void setupClickListeners() {
        try {
            binding.toolbar.setNavigationOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    Navigation.findNavController(v).popBackStack();
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating back: " + e.getMessage());
                }
            });

            binding.signUpButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    if (!isProcessing && validateInput()) {
                        attemptSignUp();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error on sign up button click: " + e.getMessage());
                }
            });

            binding.loginText.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    Navigation.findNavController(v).navigate(R.id.action_signUpFragment_to_loginFragment);
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to login: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up click listeners: " + e.getMessage());
        }
    }

    private void setupPasswordStrengthIndicator() {
        try {
            binding.passwordEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    updatePasswordStrengthIndicator(s.toString());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up password strength indicator: " + e.getMessage());
        }
    }

    private void updatePasswordStrengthIndicator(String password) {
        try {
            int strength = getPasswordStrength(password);

            // Update strength bar
            binding.passwordStrengthBar.setProgress(strength);

            // Update strength text
            if (strength < 25) {
                binding.passwordStrengthText.setText(R.string.password_strength_weak);
                binding.passwordStrengthText.setTextColor(requireContext().getColor(R.color.error_color));
                binding.passwordStrengthBar.setProgressTintList(requireContext().getColorStateList(R.color.error_color));
            } else if (strength < 50) {
                binding.passwordStrengthText.setText(R.string.password_strength_fair);
                binding.passwordStrengthText.setTextColor(requireContext().getColor(R.color.warning_color));
                binding.passwordStrengthBar.setProgressTintList(requireContext().getColorStateList(R.color.warning_color));
            } else if (strength < 75) {
                binding.passwordStrengthText.setText(R.string.password_strength_good);
                binding.passwordStrengthText.setTextColor(requireContext().getColor(R.color.success_color));
                binding.passwordStrengthBar.setProgressTintList(requireContext().getColorStateList(R.color.success_color));
            } else {
                binding.passwordStrengthText.setText(R.string.password_strength_strong);
                binding.passwordStrengthText.setTextColor(requireContext().getColor(R.color.primary));
                binding.passwordStrengthBar.setProgressTintList(requireContext().getColorStateList(R.color.primary));
            }

            // Show strength indicator if password field is not empty
            if (password.isEmpty()) {
                binding.passwordStrengthContainer.setVisibility(View.GONE);
            } else {
                binding.passwordStrengthContainer.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating password strength: " + e.getMessage());
        }
    }

    private int getPasswordStrength(String password) {
        // Basic password strength calculation
        if (password.isEmpty()) return 0;

        int strength = 0;

        // Length contribution (up to 40%)
        strength += Math.min(40, password.length() * 5);

        // Character variety contribution (up to 60%)
        boolean hasLower = false, hasUpper = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }

        if (hasLower) strength += 15;
        if (hasUpper) strength += 15;
        if (hasDigit) strength += 15;
        if (hasSpecial) strength += 15;

        return Math.min(100, strength);
    }

    private void setupPasswordVisibilityToggle() {
        try {
            binding.passwordLayout.setEndIconOnClickListener(v -> {
                // Toggle password visibility - handled automatically by TextInputLayout
                performHapticFeedback(v);
            });

            binding.confirmPasswordLayout.setEndIconOnClickListener(v -> {
                // Toggle password visibility - handled automatically by TextInputLayout
                performHapticFeedback(v);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up password visibility toggle: " + e.getMessage());
        }
    }

    private boolean validateInput() {
        try {
            boolean isValid = true;
            String displayName = binding.nameEditText.getText().toString().trim();
            String email = binding.emailEditText.getText().toString().trim();
            String password = binding.passwordEditText.getText().toString();
            String confirmPassword = binding.confirmPasswordEditText.getText().toString();

            // Validate display name
            if (displayName.isEmpty()) {
                binding.nameLayout.setError(getString(R.string.name_required));
                isValid = false;
            } else {
                binding.nameLayout.setError(null);
            }

            // Validate email
            if (email.isEmpty()) {
                binding.emailLayout.setError(getString(R.string.email_required));
                isValid = false;
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailLayout.setError(getString(R.string.invalid_email));
                isValid = false;
            } else {
                binding.emailLayout.setError(null);
            }

            // Validate password
            if (password.isEmpty()) {
                binding.passwordLayout.setError(getString(R.string.password_required));
                isValid = false;
            } else if (password.length() < 8) {
                binding.passwordLayout.setError(getString(R.string.password_too_short));
                isValid = false;
            } else {
                binding.passwordLayout.setError(null);
            }

            // Validate confirm password
            if (confirmPassword.isEmpty()) {
                binding.confirmPasswordLayout.setError(getString(R.string.confirm_password_required));
                isValid = false;
            } else if (!confirmPassword.equals(password)) {
                binding.confirmPasswordLayout.setError(getString(R.string.passwords_do_not_match));
                isValid = false;
            } else {
                binding.confirmPasswordLayout.setError(null);
            }

            // Validate terms acceptance
            if (!binding.termsCheckbox.isChecked()) {
                Snackbar.make(binding.getRoot(), R.string.please_accept_terms, Snackbar.LENGTH_LONG).show();
                isValid = false;
            }

            return isValid;
        } catch (Exception e) {
            Log.e(TAG, "Error validating input: " + e.getMessage());
            return false;
        }
    }

    private void attemptSignUp() {
        try {
            if (networkMonitor != null && !networkMonitor.isNetworkAvailable()) {
                Snackbar.make(binding.getRoot(), R.string.no_internet_connection, Snackbar.LENGTH_LONG).show();
                return;
            }

            String displayName = binding.nameEditText.getText().toString().trim();
            String email = binding.emailEditText.getText().toString().trim();
            String password = binding.passwordEditText.getText().toString();

            // Show loading
            isProcessing = true;
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.signUpButton.setEnabled(false);

            // Use Firebase Auth directly instead of ViewModel
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(requireActivity(), task -> {
                        isProcessing = false;
                        binding.progressBar.setVisibility(View.GONE);
                        binding.signUpButton.setEnabled(true);

                        if (task.isSuccessful()) {
                            // User creation successful
                            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                            if (firebaseUser != null) {
                                // Update display name
                                firebaseUser.updateProfile(new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                        .setDisplayName(displayName)
                                        .build()).addOnCompleteListener(profileTask -> {
                                    // Create a user object
                                    User user = new User(firebaseUser.getUid(), email);
                                    user.setDisplayName(displayName);

                                    // Update user in repository
                                    if (viewModel != null) {
                                        viewModel.refreshCurrentUser();
                                    }

                                    handleSignUpSuccess(user);
                                });
                            } else {
                                handleSignUpError(new Exception("User creation failed"));
                            }
                        } else {
                            // User creation failed
                            handleSignUpError(task.getException());
                        }
                    });
        } catch (Exception e) {
            isProcessing = false;
            binding.progressBar.setVisibility(View.GONE);
            binding.signUpButton.setEnabled(true);
            Log.e(TAG, "Error during sign up: " + e.getMessage());
            handleSignUpError(e);
        }
    }

    private void handleSignUpSuccess(User user) {
        try {
            // Play success haptic feedback
            performHapticFeedback(binding.getRoot(), true);

            // Show success message
            Snackbar.make(binding.getRoot(), R.string.sign_up_success, Snackbar.LENGTH_SHORT).show();

            // Navigate to home screen
            Navigation.findNavController(requireView()).navigate(R.id.action_signUpFragment_to_homeFragment);

            // Log successful sign up
            if (analyticsTracker != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("user_id", user.getUid());
                analyticsTracker.trackEvent("sign_up_success", params);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling sign up success: " + e.getMessage());
            // Still try to navigate even if analytics fails
            try {
                Navigation.findNavController(requireView()).navigate(R.id.action_signUpFragment_to_homeFragment);
            } catch (Exception ne) {
                Log.e(TAG, "Error navigating after sign up: " + ne.getMessage());
            }
        }
    }

    private void handleSignUpError(Exception error) {
        try {
            Log.e(TAG, "Sign up failed", error);

            // Play error haptic feedback
            performHapticFeedback(binding.getRoot(), false);

            String errorMessage;
            if (error != null && error.getMessage() != null) {
                if (error.getMessage().contains("email address is already in use")) {
                    errorMessage = getString(R.string.email_already_in_use);
                } else if (error.getMessage().contains("password is invalid")) {
                    errorMessage = getString(R.string.invalid_password);
                } else if (error.getMessage().contains("blocked")) {
                    errorMessage = getString(R.string.operation_not_allowed);
                } else {
                    errorMessage = getString(R.string.sign_up_failed);
                }
            } else {
                errorMessage = getString(R.string.sign_up_failed);
            }

            Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG).show();

            // Log sign up error
            if (analyticsTracker != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("error", error != null ? error.getMessage() : "Unknown error");
                analyticsTracker.trackEvent("sign_up_error", params);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling sign up error: " + e.getMessage());
            Snackbar.make(binding.getRoot(), R.string.sign_up_failed, Snackbar.LENGTH_LONG).show();
        }
    }

    private void performHapticFeedback(View view) {
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Exception e) {
            // Ignore haptic feedback errors
        }
    }

    private void performHapticFeedback(View view, boolean isSuccess) {
        try {
            if (isSuccess) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            } else {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            }
        } catch (Exception e) {
            // Ignore haptic feedback errors
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}