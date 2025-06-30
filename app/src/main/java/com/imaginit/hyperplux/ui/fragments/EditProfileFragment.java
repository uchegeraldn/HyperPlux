package com.imaginit.hyperplux.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.snackbar.Snackbar;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.FragmentEditProfileBinding;
import com.imaginit.hyperplux.models.User;
import com.imaginit.hyperplux.repositories.UserRepository;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.NetworkMonitor;
import com.imaginit.hyperplux.viewmodels.UserViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import java.util.HashMap;
import java.util.Map;

public class EditProfileFragment extends Fragment {
    private static final String TAG = "EditProfileFragment";

    private FragmentEditProfileBinding binding;
    private UserViewModel userViewModel;
    private User currentUser;
    private Uri selectedImageUri;
    private boolean isProcessing = false;

    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentEditProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize ViewModel
            AppDatabase database = AppDatabase.getDatabase(requireContext());
            UserRepository userRepository = new UserRepository(database.userDao());
            ViewModelFactory factory = new ViewModelFactory(requireActivity().getApplication(), null, userRepository);
            userViewModel = new ViewModelProvider(this, factory).get(UserViewModel.class);

            // Set up toolbar
            binding.toolbar.setNavigationOnClickListener(v ->
                    Navigation.findNavController(requireView()).navigateUp());

            // Initialize activity result launchers
            setupActivityResultLaunchers();

            // Set up click listeners
            setupClickListeners();

            // Observe user data
            userViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
                if (user != null) {
                    currentUser = user;
                    populateUserData(user);
                }
            });

            // Track screen view
            AnalyticsTracker tracker = AnalyticsTracker.getInstance(requireContext());
            if (tracker != null) {
                tracker.trackScreenView("EditProfile", "EditProfileFragment");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing edit profile: " + e.getMessage());
            Snackbar.make(binding.getRoot(), "Error initializing profile editor", Snackbar.LENGTH_SHORT).show();
            Navigation.findNavController(view).popBackStack();
        }
    }

    private void setupActivityResultLaunchers() {
        // Gallery launcher
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    try {
                        if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                            Uri imageUri = result.getData().getData();
                            if (imageUri != null) {
                                selectedImageUri = imageUri;
                                Glide.with(requireContext())
                                        .load(imageUri)
                                        .apply(new RequestOptions()
                                                .placeholder(R.drawable.ic_person)
                                                .error(R.drawable.ic_person))
                                        .circleCrop()
                                        .into(binding.profileImage);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing gallery result: " + e.getMessage());
                    }
                }
        );

        // Permission launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openGallery();
                    } else {
                        Snackbar.make(binding.getRoot(), R.string.storage_permission_denied, Snackbar.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupClickListeners() {
        try {
            // Profile image click
            binding.profileImageContainer.setOnClickListener(v -> {
                if (isProcessing) return;
                requestStoragePermission();
            });

            // Save button
            binding.saveButton.setOnClickListener(v -> {
                if (isProcessing) return;

                // Check network connectivity
                NetworkMonitor networkMonitor = NetworkMonitor.getInstance(requireContext());
                if (networkMonitor != null && !networkMonitor.isNetworkAvailable()) {
                    Snackbar.make(binding.getRoot(), R.string.no_internet_connection, Snackbar.LENGTH_SHORT).show();
                    return;
                }

                // Validate and save profile
                validateAndSaveProfile();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up click listeners: " + e.getMessage());
        }
    }

    private void requestStoragePermission() {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error requesting storage permission: " + e.getMessage());
            Snackbar.make(binding.getRoot(), "Error accessing storage", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening gallery: " + e.getMessage());
            Snackbar.make(binding.getRoot(), "Error opening gallery", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void populateUserData(User user) {
        try {
            // Set user name
            binding.displayNameEditText.setText(user.getDisplayName() != null ? user.getDisplayName() : "");

            // Set phone number
            if (user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty()) {
                binding.phoneNumberEditText.setText(user.getPhoneNumber());
            }

            // Set bio
            if (user.getBio() != null && !user.getBio().isEmpty()) {
                binding.bioEditText.setText(user.getBio());
            }

            // Load profile image
            String profileImageUri = user.getProfileImageUri();
            if (profileImageUri != null && !profileImageUri.isEmpty()) {
                Glide.with(requireContext())
                        .load(profileImageUri)
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.ic_person)
                                .error(R.drawable.ic_person))
                        .circleCrop()
                        .into(binding.profileImage);
            } else {
                binding.profileImage.setImageResource(R.drawable.ic_person);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error populating user data: " + e.getMessage());
        }
    }

    private void validateAndSaveProfile() {
        try {
            // Get input values
            String displayName = binding.displayNameEditText.getText().toString().trim();
            String phoneNumber = binding.phoneNumberEditText.getText().toString().trim();
            String bio = binding.bioEditText.getText().toString().trim();

            // Validate display name
            if (displayName.isEmpty()) {
                binding.displayNameLayout.setError(getString(R.string.display_name_required));
                return;
            } else {
                binding.displayNameLayout.setError(null);
            }

            // Validate phone number if provided
            if (!phoneNumber.isEmpty() && !isValidPhoneNumber(phoneNumber)) {
                binding.phoneNumberLayout.setError(getString(R.string.invalid_phone_number));
                return;
            } else {
                binding.phoneNumberLayout.setError(null);
            }

            // Show progress
            isProcessing = true;
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.saveButton.setEnabled(false);

            // If we have a new image, upload it first then update the profile
            if (selectedImageUri != null) {
                uploadProfileImageAndUpdateUser(displayName, phoneNumber, bio);
            } else {
                // Just update the user profile
                updateUserProfile(displayName, phoneNumber, bio, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error validating profile: " + e.getMessage());
            handleUpdateError("Error updating profile");
        }
    }

    private void uploadProfileImageAndUpdateUser(String displayName, String phoneNumber, String bio) {
        try {
            // Show upload progress
            binding.progressBar.setVisibility(View.VISIBLE);

            // Upload the image to storage
            userViewModel.setProfileImage(selectedImageUri.toString());

            // Directly update the profile (in a real app, you would wait for the upload to complete)
            // For now, we'll just use the local URI
            updateUserProfile(displayName, phoneNumber, bio, selectedImageUri.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error uploading profile image: " + e.getMessage());
            handleUpdateError("Error uploading image");
        }
    }

    private void updateUserProfile(String displayName, String phoneNumber, String bio, String imageUri) {
        try {
            // Update the profile data
            userViewModel.updateUserProfile(displayName, bio, phoneNumber);

            // If we have a new image URI, update it
            if (imageUri != null && !imageUri.isEmpty()) {
                userViewModel.setProfileImage(imageUri);
            }

            // Show success and navigate back
            isProcessing = false;
            binding.progressBar.setVisibility(View.GONE);
            binding.saveButton.setEnabled(true);

            // Show success message
            Snackbar.make(binding.getRoot(), R.string.profile_updated_successfully, Snackbar.LENGTH_SHORT).show();

            // Track profile update
            AnalyticsTracker tracker = AnalyticsTracker.getInstance(requireContext());
            if (tracker != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("has_bio", bio != null && !bio.isEmpty());
                params.put("has_phone", phoneNumber != null && !phoneNumber.isEmpty());
                params.put("has_profile_image", imageUri != null && !imageUri.isEmpty());
                tracker.trackEvent("profile_updated", params);
            }

            // Navigate back
            Navigation.findNavController(requireView()).navigateUp();
        } catch (Exception e) {
            Log.e(TAG, "Error updating user profile: " + e.getMessage());
            handleUpdateError("Error updating profile");
        }
    }

    private void handleUpdateError(String error) {
        isProcessing = false;
        binding.progressBar.setVisibility(View.GONE);
        binding.saveButton.setEnabled(true);

        Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
    }

    // Basic phone number validation
    private boolean isValidPhoneNumber(String phoneNumber) {
        // This is a very basic validation, replace with your own validation logic
        return phoneNumber.matches("\\+?[0-9]{10,15}");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}