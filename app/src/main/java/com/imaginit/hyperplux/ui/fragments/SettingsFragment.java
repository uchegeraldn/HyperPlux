package com.imaginit.hyperplux.ui.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.databinding.FragmentSettingsBinding;
import com.imaginit.hyperplux.models.User;
import com.imaginit.hyperplux.viewmodels.UserViewModel;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;
    private UserViewModel userViewModel;
    private SharedPreferences prefs;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        setupToolbar();
        setupUserProfile();
        setupPreferenceToggles();
        setupClickListeners();
        displayAppVersion();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(requireView()).navigateUp());
    }

    private void setupUserProfile() {
        userViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                updateProfileUI(user);
            }
        });

        // Profile card click listeners
        View profileCard = binding.profileSection.getRoot().findViewById(R.id.profile_layout);
        profileCard.setOnClickListener(v ->
                Navigation.findNavController(requireView()).navigate(R.id.action_settingsFragment_to_editProfileFragment));

        View editIcon = binding.profileSection.getRoot().findViewById(R.id.edit_profile_icon);
        editIcon.setOnClickListener(v ->
                Navigation.findNavController(requireView()).navigate(R.id.action_settingsFragment_to_editProfileFragment));
    }

    private void updateProfileUI(User user) {
        View rootView = binding.profileSection.getRoot();

        // Get view references using findViewById since we're using <include>
        android.widget.TextView nameText = rootView.findViewById(R.id.user_name);
        android.widget.TextView emailText = rootView.findViewById(R.id.user_email);

        nameText.setText(user.getDisplayName());
        emailText.setText(user.getEmail());

        // Load profile image if available
        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            com.google.android.material.imageview.ShapeableImageView profileImage =
                    rootView.findViewById(R.id.user_profile_image);

            // Use your preferred image loading library here (Glide, Picasso, etc.)
            // Example with Glide:
            // Glide.with(requireContext())
            //     .load(user.getProfileImageUrl())
            //     .placeholder(R.drawable.ic_person)
            //     .into(profileImage);
        }
    }

    private void setupPreferenceToggles() {
        // Notifications switch
        com.google.android.material.switchmaterial.SwitchMaterial notificationsSwitch =
                binding.preferencesSection.getRoot().findViewById(R.id.notifications_switch);
        notificationsSwitch.setChecked(prefs.getBoolean("notifications_enabled", true));
        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply();
            // Implement notification handling logic here
        });

        // Biometric authentication switch
        com.google.android.material.switchmaterial.SwitchMaterial biometricSwitch =
                binding.preferencesSection.getRoot().findViewById(R.id.biometric_switch);
        biometricSwitch.setChecked(prefs.getBoolean("biometric_enabled", false));
        biometricSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("biometric_enabled", isChecked).apply();
            // Implement biometric auth logic here
        });

        // Display theme & language values
        android.widget.TextView themeValue = binding.preferencesSection.getRoot().findViewById(R.id.theme_value);
        android.widget.TextView currencyValue = binding.preferencesSection.getRoot().findViewById(R.id.currency_value);
        android.widget.TextView languageValue = binding.preferencesSection.getRoot().findViewById(R.id.language_value);

        String theme = prefs.getString("theme", "system");
        themeValue.setText(getThemeDisplayName(theme));

        String currency = prefs.getString("currency", "USD");
        currencyValue.setText(currency);

        String language = prefs.getString("language", "English");
        languageValue.setText(language);
    }

    private String getThemeDisplayName(String theme) {
        switch (theme) {
            case "light":
                return getString(R.string.theme_light);
            case "dark":
                return getString(R.string.theme_dark);
            case "system":
            default:
                return getString(R.string.theme_system);
        }
    }

    private void setupClickListeners() {
        // Theme selection
        View themeLayout = binding.preferencesSection.getRoot().findViewById(R.id.theme_layout);
        themeLayout.setOnClickListener(v -> showThemeSelectionDialog());

        // Currency selection
        View currencyLayout = binding.preferencesSection.getRoot().findViewById(R.id.currency_layout);
        currencyLayout.setOnClickListener(v -> showCurrencySelectionDialog());

        // Language selection
        View languageLayout = binding.preferencesSection.getRoot().findViewById(R.id.language_layout);
        languageLayout.setOnClickListener(v -> showLanguageSelectionDialog());

        // Payment methods
        View paymentMethodsLayout = binding.accountSection.getRoot().findViewById(R.id.payment_methods_layout);
        paymentMethodsLayout.setOnClickListener(v ->
                Navigation.findNavController(requireView()).navigate(R.id.action_settingsFragment_to_paymentMethodsFragment));

        // Backup & restore
        View backupRestoreLayout = binding.accountSection.getRoot().findViewById(R.id.backup_restore_layout);
        backupRestoreLayout.setOnClickListener(v -> showBackupRestoreDialog());

        // Privacy policy
        View privacyPolicyLayout = binding.supportSection.getRoot().findViewById(R.id.privacy_policy_layout);
        privacyPolicyLayout.setOnClickListener(v -> openUrl(getString(R.string.privacy_policy_url)));

        // Terms of service
        View termsLayout = binding.supportSection.getRoot().findViewById(R.id.terms_layout);
        termsLayout.setOnClickListener(v -> openUrl(getString(R.string.terms_of_service_url)));

        // Help & support
        View helpSupportLayout = binding.supportSection.getRoot().findViewById(R.id.help_support_layout);
        helpSupportLayout.setOnClickListener(v ->
                Navigation.findNavController(requireView()).navigate(R.id.action_settingsFragment_to_helpSupportFragment));

        // Delete account
        android.widget.TextView deleteAccountButton = binding.accountActionsSection.getRoot().findViewById(R.id.delete_account_button);
        deleteAccountButton.setOnClickListener(v -> showDeleteAccountDialog());

        // Logout
        android.widget.TextView logoutButton = binding.accountActionsSection.getRoot().findViewById(R.id.logout_button);
        logoutButton.setOnClickListener(v -> showLogoutDialog());
    }

    private void showThemeSelectionDialog() {
        final String[] themes = {
                getString(R.string.theme_light),
                getString(R.string.theme_dark),
                getString(R.string.theme_system)
        };

        final String[] themeValues = {"light", "dark", "system"};

        String currentTheme = prefs.getString("theme", "system");
        int selectedIndex = 2; // Default to system

        for (int i = 0; i < themeValues.length; i++) {
            if (themeValues[i].equals(currentTheme)) {
                selectedIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dark_mode)
                .setSingleChoiceItems(themes, selectedIndex, (dialog, which) -> {
                    String selectedTheme = themeValues[which];
                    prefs.edit().putString("theme", selectedTheme).apply();

                    // Update the theme value display
                    android.widget.TextView themeValue = binding.preferencesSection.getRoot().findViewById(R.id.theme_value);
                    themeValue.setText(themes[which]);

                    // Apply the theme change
                    ThemeManager.applyTheme(selectedTheme);

                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCurrencySelectionDialog() {
        // Implement currency selection dialog
        // This would typically show a list of currencies to choose from
    }

    private void showLanguageSelectionDialog() {
        // Implement language selection dialog
        // This would typically show a list of languages to choose from
    }

    private void showBackupRestoreDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_and_restore)
                .setMessage(R.string.backup_data_message)
                .setPositiveButton(R.string.backup, (dialog, which) -> {
                    // Implement backup logic
                    Toast.makeText(requireContext(), R.string.backup_successful, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.restore, (dialog, which) -> {
                    // Implement restore logic
                    Toast.makeText(requireContext(), R.string.restore_successful, Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.cannot_open_url, Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteAccountDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_account)
                .setMessage(R.string.delete_account_confirmation)
                .setPositiveButton(R.string.delete_account, (dialog, which) -> {
                    // Show loading indicator
                    binding.progressBar.setVisibility(View.VISIBLE);

                    // Call the ViewModel method to delete the account
                    userViewModel.deleteAccount().observe(getViewLifecycleOwner(), success -> {
                        binding.progressBar.setVisibility(View.GONE);

                        if (success) {
                            Toast.makeText(requireContext(), R.string.account_deleted, Toast.LENGTH_SHORT).show();
                            // Navigate to login screen
                            Navigation.findNavController(requireView()).navigate(
                                    R.id.action_settingsFragment_to_loginFragment,
                                    null,
                                    // Clear the back stack so user can't navigate back after logout
                                    new androidx.navigation.NavOptions.Builder()
                                            .setPopUpTo(R.id.homeFragment, true)
                                            .build()
                            );
                        } else {
                            Toast.makeText(requireContext(), R.string.error_authentication, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirmation)
                .setPositiveButton(R.string.logout, (dialog, which) -> {
                    // Perform logout
                    userViewModel.signOut();
                    Toast.makeText(requireContext(), R.string.logged_out, Toast.LENGTH_SHORT).show();

                    // Navigate to login screen
                    Navigation.findNavController(requireView()).navigate(
                            R.id.action_settingsFragment_to_loginFragment,
                            null,
                            // Clear the back stack so user can't navigate back after logout
                            new androidx.navigation.NavOptions.Builder()
                                    .setPopUpTo(R.id.homeFragment, true)
                                    .build()
                    );
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void displayAppVersion() {
        try {
            String versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            int versionCode = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionCode;

            binding.appVersion.setText(getString(R.string.app_version_format, versionName, versionCode));
        } catch (Exception e) {
            binding.appVersion.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}