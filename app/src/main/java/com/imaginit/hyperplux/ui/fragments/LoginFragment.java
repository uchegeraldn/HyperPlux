package com.imaginit.hyperplux.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.FragmentLoginBinding;
import com.imaginit.hyperplux.models.User;
import com.imaginit.hyperplux.repositories.UserRepository;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.NetworkMonitor;
import com.imaginit.hyperplux.viewmodels.UserViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Fragment for handling user login
 */
public class LoginFragment extends Fragment {
    private static final String TAG = "LoginFragment";

    private FragmentLoginBinding binding;
    private UserViewModel viewModel;
    private FirebaseAuth auth;
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private AnalyticsTracker analyticsTracker;
    private NetworkMonitor networkMonitor;
    private boolean isProcessing = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize Firebase Auth
            auth = FirebaseAuth.getInstance();

            // Initialize ViewModel with proper factory
            initializeViewModel();

            // Initialize utility classes
            analyticsTracker = AnalyticsTracker.getInstance(requireContext());
            networkMonitor = NetworkMonitor.getInstance(requireContext());

            // Setup biometric authentication if available
            setupBiometricAuth();

            // Check for saved credentials
            checkSavedCredentials();

            // Set up click listeners
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
                analyticsTracker.trackScreenView("Login", "LoginFragment");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing login screen: " + e.getMessage());
            Toast.makeText(requireContext(), "Error initializing login", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), "Error initializing authentication", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupClickListeners() {
        binding.loginButton.setOnClickListener(v -> {
            try {
                if (!isProcessing && validateInput()) {
                    attemptLogin();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during login: " + e.getMessage());
                Toast.makeText(requireContext(), "Error during login", Toast.LENGTH_SHORT).show();
            }
        });

        binding.biometricLoginButton.setOnClickListener(v -> {
            try {
                if (biometricPrompt != null) {
                    biometricPrompt.authenticate(promptInfo);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initiating biometric login: " + e.getMessage());
                Toast.makeText(requireContext(), "Error with biometric login", Toast.LENGTH_SHORT).show();
            }
        });

        binding.signUpText.setOnClickListener(v -> {
            try {
                Navigation.findNavController(v).navigate(R.id.action_loginFragment_to_signUpFragment);
            } catch (Exception e) {
                Log.e(TAG, "Error navigating to sign up: " + e.getMessage());
                Toast.makeText(requireContext(), "Error navigating to sign up", Toast.LENGTH_SHORT).show();
            }
        });

        binding.forgotPasswordText.setOnClickListener(v -> {
            try {
                showForgotPasswordDialog();
            } catch (Exception e) {
                Log.e(TAG, "Error showing forgot password dialog: " + e.getMessage());
                Toast.makeText(requireContext(), "Error with password reset", Toast.LENGTH_SHORT).show();
            }
        });

        binding.rememberMeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                if (!isChecked) {
                    // Clear saved credentials
                    // Using direct SharedPreferences instead of SecureStorageManager since it may not be compatible
                    getActivity().getSharedPreferences("secure_prefs", 0)
                            .edit()
                            .remove("saved_email")
                            .remove("saved_password")
                            .apply();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating remember me: " + e.getMessage());
            }
        });
    }

    private void setupBiometricAuth() {
        try {
            executor = ContextCompat.getMainExecutor(requireContext());

            biometricPrompt = new BiometricPrompt(this, executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                    errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                                Toast.makeText(requireContext(),
                                        getString(R.string.biometric_error, errString),
                                        Toast.LENGTH_SHORT).show();
                            }

                            // Log biometric error
                            if (analyticsTracker != null) {
                                Map<String, Object> params = new HashMap<>();
                                params.put("error_code", errorCode);
                                params.put("error_message", errString.toString());
                                analyticsTracker.trackEvent("biometric_auth_error", params);
                            }
                        }

                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);

                            try {
                                // Get saved credentials
                                String email = getSavedEmail();
                                String password = getSavedPassword();

                                if (email != null && password != null) {
                                    binding.emailEditText.setText(email);
                                    binding.passwordEditText.setText(password);
                                    loginUser(email, password);

                                    // Log biometric login success
                                    if (analyticsTracker != null) {
                                        analyticsTracker.trackEvent("biometric_login_success", null);
                                    }
                                    performHapticFeedback(true);
                                } else {
                                    Toast.makeText(requireContext(),
                                            R.string.no_saved_credentials,
                                            Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error in biometric success: " + e.getMessage());
                                Toast.makeText(requireContext(), "Error with biometric login", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            Toast.makeText(requireContext(),
                                    R.string.biometric_failed,
                                    Toast.LENGTH_SHORT).show();

                            // Log biometric login failure
                            if (analyticsTracker != null) {
                                analyticsTracker.trackEvent("biometric_login_failed", null);
                            }
                        }
                    });

            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.biometric_login_title))
                    .setSubtitle(getString(R.string.biometric_login_subtitle))
                    .setNegativeButtonText(getString(R.string.cancel))
                    .build();

            // Check if biometric auth is available
            if (canUseBiometricAuth() && getSavedEmail() != null && getSavedPassword() != null) {
                binding.biometricLoginButton.setVisibility(View.VISIBLE);
            } else {
                binding.biometricLoginButton.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up biometric auth: " + e.getMessage());
            binding.biometricLoginButton.setVisibility(View.GONE);
        }
    }

    private boolean canUseBiometricAuth() {
        try {
            androidx.biometric.BiometricManager biometricManager =
                    androidx.biometric.BiometricManager.from(requireContext());

            return biometricManager.canAuthenticate(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                    androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) {
            Log.e(TAG, "Error checking biometric availability: " + e.getMessage());
            return false;
        }
    }

    private String getSavedEmail() {
        try {
            return getActivity().getSharedPreferences("secure_prefs", 0)
                    .getString("saved_email", null);
        } catch (Exception e) {
            Log.e(TAG, "Error getting saved email: " + e.getMessage());
            return null;
        }
    }

    private String getSavedPassword() {
        try {
            return getActivity().getSharedPreferences("secure_prefs", 0)
                    .getString("saved_password", null);
        } catch (Exception e) {
            Log.e(TAG, "Error getting saved password: " + e.getMessage());
            return null;
        }
    }

    private void saveCredentials(String email, String password) {
        try {
            getActivity().getSharedPreferences("secure_prefs", 0)
                    .edit()
                    .putString("saved_email", email)
                    .putString("saved_password", password)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving credentials: " + e.getMessage());
        }
    }

    private void checkSavedCredentials() {
        try {
            String savedEmail = getSavedEmail();
            if (savedEmail != null) {
                binding.emailEditText.setText(savedEmail);
                binding.rememberMeCheckbox.setChecked(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking saved credentials: " + e.getMessage());
        }
    }

    private boolean validateInput() {
        boolean isValid = true;
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();

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
        } else {
            binding.passwordLayout.setError(null);
        }

        return isValid;
    }

    private void attemptLogin() {
        try {
            if (networkMonitor != null && !networkMonitor.isNetworkAvailable()) {
                Snackbar.make(binding.getRoot(), R.string.no_internet_connection, Snackbar.LENGTH_LONG).show();
                return;
            }

            String email = binding.emailEditText.getText().toString().trim();
            String password = binding.passwordEditText.getText().toString().trim();

            // Show loading
            isProcessing = true;
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.loginButton.setEnabled(false);

            // Use Firebase Auth directly instead of ViewModel
            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(requireActivity(), task -> {
                        isProcessing = false;
                        binding.progressBar.setVisibility(View.GONE);
                        binding.loginButton.setEnabled(true);

                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = auth.getCurrentUser();
                            if (firebaseUser != null) {
                                // Create user object
                                User user = new User(firebaseUser.getUid(), firebaseUser.getEmail());
                                handleLoginSuccess(email, password, user);
                            }
                        } else {
                            handleLoginError(task.getException());
                        }
                    });
        } catch (Exception e) {
            isProcessing = false;
            binding.progressBar.setVisibility(View.GONE);
            binding.loginButton.setEnabled(true);
            Log.e(TAG, "Error during login attempt: " + e.getMessage());
            Snackbar.make(binding.getRoot(), "Error during login: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    private void loginUser(String email, String password) {
        try {
            isProcessing = true;
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.loginButton.setEnabled(false);

            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(requireActivity(), task -> {
                        isProcessing = false;
                        binding.progressBar.setVisibility(View.GONE);
                        binding.loginButton.setEnabled(true);

                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = auth.getCurrentUser();
                            if (firebaseUser != null) {
                                User user = new User(firebaseUser.getUid(), firebaseUser.getEmail());
                                handleLoginSuccess(email, password, user);
                            }
                        } else {
                            handleLoginError(task.getException());
                        }
                    });
        } catch (Exception e) {
            isProcessing = false;
            binding.progressBar.setVisibility(View.GONE);
            binding.loginButton.setEnabled(true);
            Log.e(TAG, "Error during login: " + e.getMessage());
            Snackbar.make(binding.getRoot(), "Error during login: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    private void handleLoginSuccess(String email, String password, User user) {
        try {
            // Save credentials if remember me is checked
            if (binding.rememberMeCheckbox.isChecked()) {
                saveCredentials(email, password);
            }

            // Play success haptic feedback
            performHapticFeedback(true);

            // Navigate to home screen
            Navigation.findNavController(requireView()).navigate(R.id.action_loginFragment_to_homeFragment);

            // Log successful login
            if (analyticsTracker != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("user_id", user.getUid());
                analyticsTracker.trackEvent("login_success", params);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling login success: " + e.getMessage());
            // Still navigate to home even if tracking fails
            try {
                Navigation.findNavController(requireView()).navigate(R.id.action_loginFragment_to_homeFragment);
            } catch (Exception ex) {
                Log.e(TAG, "Error navigating after login: " + ex.getMessage());
            }
        }
    }

    private void handleLoginError(Exception error) {
        try {
            Log.e(TAG, "Login failed", error);

            // Play error haptic feedback
            performHapticFeedback(false);

            String errorMessage;
            if (error != null && error.getMessage() != null) {
                if (error.getMessage().contains("password")) {
                    errorMessage = getString(R.string.incorrect_password);
                } else if (error.getMessage().contains("no user record")) {
                    errorMessage = getString(R.string.email_not_found);
                } else if (error.getMessage().contains("blocked")) {
                    errorMessage = getString(R.string.account_disabled);
                } else {
                    errorMessage = getString(R.string.login_failed);
                }
            } else {
                errorMessage = getString(R.string.login_failed);
            }

            Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG).show();

            // Log login error
            if (analyticsTracker != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("error", error != null ? error.getMessage() : "Unknown error");
                analyticsTracker.trackEvent("login_error", params);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling login error: " + e.getMessage());
            Snackbar.make(binding.getRoot(), R.string.login_failed, Snackbar.LENGTH_LONG).show();
        }
    }

    private void performHapticFeedback(boolean isSuccess) {
        try {
            View view = binding.getRoot();
            if (isSuccess) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            } else {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error performing haptic feedback: " + e.getMessage());
        }
    }

    private void showForgotPasswordDialog() {
        try {
            String email = binding.emailEditText.getText().toString().trim();

            // Simple implementation instead of using a dialog fragment
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Snackbar.make(binding.getRoot(), R.string.enter_valid_email_for_reset, Snackbar.LENGTH_LONG).show();
                return;
            }

            auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Snackbar.make(binding.getRoot(), R.string.password_reset_email_sent, Snackbar.LENGTH_LONG).show();

                            // Track password reset
                            if (analyticsTracker != null) {
                                analyticsTracker.trackEvent("password_reset_requested", null);
                            }
                        } else {
                            Snackbar.make(binding.getRoot(), R.string.password_reset_failed, Snackbar.LENGTH_LONG).show();
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error showing forgot password dialog: " + e.getMessage());
            Snackbar.make(binding.getRoot(), "Error with password reset", Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}