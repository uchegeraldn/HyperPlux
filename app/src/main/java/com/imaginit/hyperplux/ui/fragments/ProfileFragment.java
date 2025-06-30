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
import com.google.firebase.auth.FirebaseAuth;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.FragmentProfileBinding;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.models.User;
import com.imaginit.hyperplux.repositories.AssetRepository;
import com.imaginit.hyperplux.repositories.UserRepository;
import com.imaginit.hyperplux.ui.adapters.AssetAdapter;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.viewmodels.AssetViewModel;
import com.imaginit.hyperplux.viewmodels.UserViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";

    private FragmentProfileBinding binding;
    private UserViewModel userViewModel;
    private AssetViewModel assetViewModel;
    private AssetAdapter adapter;
    private AnalyticsTracker analyticsTracker;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize ViewModels properly
            initializeViewModels();

            // Initialize analytics
            analyticsTracker = AnalyticsTracker.getInstance(requireContext());

            // Set up RecyclerView for top assets
            setupRecyclerView();

            // Set up action buttons
            setupActionButtons();

            // Observe current user
            userViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
                try {
                    if (user != null) {
                        updateUI(user);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error updating UI with user data: " + e.getMessage());
                }
            });

            // Observe top engaging assets
            assetViewModel.getTopEngagingAssets().observe(getViewLifecycleOwner(), assets -> {
                try {
                    updateTopAssets(assets);
                } catch (Exception e) {
                    Log.e(TAG, "Error updating top assets: " + e.getMessage());
                }
            });

            // Observe pending transactions
            assetViewModel.getPendingTransactions().observe(getViewLifecycleOwner(), transactions -> {
                try {
                    updatePendingTransactions(transactions);
                } catch (Exception e) {
                    Log.e(TAG, "Error updating pending transactions: " + e.getMessage());
                }
            });

            // Track screen view
            if (analyticsTracker != null) {
                analyticsTracker.trackScreenView("Profile", "ProfileFragment");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
            Toast.makeText(requireContext(), "Error loading profile", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeViewModels() {
        try {
            AppDatabase database = AppDatabase.getDatabase(requireContext());
            AssetRepository assetRepository = new AssetRepository(database.assetDao());
            UserRepository userRepository = new UserRepository(database.userDao());

            ViewModelFactory factory = new ViewModelFactory(
                    requireActivity().getApplication(),
                    assetRepository,
                    userRepository);

            userViewModel = new ViewModelProvider(this, factory).get(UserViewModel.class);
            assetViewModel = new ViewModelProvider(this, factory).get(AssetViewModel.class);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ViewModels: " + e.getMessage());
            throw e; // Rethrow to be caught by the outer try-catch
        }
    }

    private void setupRecyclerView() {
        try {
            adapter = new AssetAdapter(
                    // Asset click
                    asset -> {
                        try {
                            if (asset != null) {
                                Bundle args = new Bundle();
                                args.putInt("assetId", asset.getId());
                                Navigation.findNavController(requireView())
                                        .navigate(R.id.action_profileFragment_to_assetDetailFragment, args);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error navigating to asset detail: " + e.getMessage());
                            Toast.makeText(requireContext(), "Error viewing asset details", Toast.LENGTH_SHORT).show();
                        }
                    },
                    // Like click
                    asset -> {
                        if (asset != null) {
                            assetViewModel.likeAssetWithErrorHandling(asset);
                        }
                    },
                    // Dislike click
                    asset -> {
                        if (asset != null) {
                            assetViewModel.dislikeAssetWithErrorHandling(asset);
                        }
                    }
            );

            binding.topAssetsRecyclerView.setLayoutManager(
                    new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            binding.topAssetsRecyclerView.setAdapter(adapter);

            // Initially submit empty list
            adapter.submitList(new ArrayList<>());
        } catch (Exception e) {
            Log.e(TAG, "Error setting up RecyclerView: " + e.getMessage());
        }
    }

    private void setupActionButtons() {
        try {
            // Edit profile
            binding.editProfileButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    Navigation.findNavController(v).navigate(R.id.action_profileFragment_to_editProfileFragment);
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to edit profile: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error opening profile editor", Toast.LENGTH_SHORT).show();
                }
            });

            // Will settings
            binding.willSettingsButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    Navigation.findNavController(v).navigate(R.id.action_profileFragment_to_willSettingsFragment);
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to will settings: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error opening will settings", Toast.LENGTH_SHORT).show();
                }
            });

            // Settings
            binding.settingsButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    Navigation.findNavController(v).navigate(R.id.action_profileFragment_to_settingsFragment);
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to settings: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error opening settings", Toast.LENGTH_SHORT).show();
                }
            });

            // Transactions
            binding.viewTransactionsButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    Navigation.findNavController(v).navigate(R.id.action_profileFragment_to_transactionListFragment);
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to transactions: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error opening transactions", Toast.LENGTH_SHORT).show();
                }
            });

            // Pending transactions
            binding.pendingTransactionsContainer.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    Navigation.findNavController(v).navigate(R.id.action_profileFragment_to_transactionListFragment);
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to transactions: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error opening transactions", Toast.LENGTH_SHORT).show();
                }
            });

            // Logout
            binding.logoutButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    logout();
                } catch (Exception e) {
                    Log.e(TAG, "Error during logout: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error logging out", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up action buttons: " + e.getMessage());
        }
    }

    private void updateTopAssets(List<Asset> assets) {
        try {
            if (assets != null && !assets.isEmpty()) {
                adapter.submitList(new ArrayList<>(assets));  // Create a new list to ensure DiffUtil works correctly
                binding.topAssetsContainer.setVisibility(View.VISIBLE);
            } else {
                binding.topAssetsContainer.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating top assets: " + e.getMessage());
            binding.topAssetsContainer.setVisibility(View.GONE);
        }
    }

    private void updatePendingTransactions(List<?> transactions) {
        try {
            if (transactions != null && !transactions.isEmpty()) {
                binding.pendingTransactionsCount.setText(String.valueOf(transactions.size()));
                binding.pendingTransactionsContainer.setVisibility(View.VISIBLE);
            } else {
                binding.pendingTransactionsContainer.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating pending transactions: " + e.getMessage());
            binding.pendingTransactionsContainer.setVisibility(View.GONE);
        }
    }

    private void updateUI(User user) {
        try {
            // Set profile image
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

            // Set user info
            binding.displayName.setText(user.getDisplayName() != null ? user.getDisplayName() : "");
            binding.email.setText(user.getEmail() != null ? user.getEmail() : "");

            String bio = user.getBio();
            if (bio != null && !bio.isEmpty()) {
                binding.bio.setText(bio);
                binding.bio.setVisibility(View.VISIBLE);
            } else {
                binding.bio.setVisibility(View.GONE);
            }

            // Set stats - with null checks
            int followerCount = 0;
            int followingCount = 0;

            if (user.getFollowers() != null) {
                followerCount = user.getFollowers().size();
            }

            if (user.getFollowing() != null) {
                followingCount = user.getFollowing().size();
            }

            binding.followerCount.setText(String.valueOf(followerCount));
            binding.followingCount.setText(String.valueOf(followingCount));
            binding.assetsCount.setText(String.valueOf(user.getTotalAssets()));

            // Set join date
            if (user.getCreationDate() != null) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
                binding.joinDate.setText(getString(R.string.member_since, dateFormat.format(user.getCreationDate())));
            }

            // Check will status
            updateWillStatus(user);
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI: " + e.getMessage());
        }
    }

    private void updateWillStatus(User user) {
        try {
            if (user.isWillActivated()) {
                binding.willStatus.setText(R.string.will_active);
                binding.willStatus.setTextColor(getResources().getColor(R.color.success, null));
            } else if (user.getNextOfKinName() != null && !user.getNextOfKinName().isEmpty()) {
                binding.willStatus.setText(R.string.will_in_progress);
                binding.willStatus.setTextColor(getResources().getColor(R.color.pending, null));
            } else {
                binding.willStatus.setText(R.string.will_not_set);
                binding.willStatus.setTextColor(getResources().getColor(R.color.error, null));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating will status: " + e.getMessage());
            binding.willStatus.setText(R.string.will_not_set);
            binding.willStatus.setTextColor(getResources().getColor(R.color.error, null));
        }
    }

    private void logout() {
        try {
            FirebaseAuth.getInstance().signOut();
            Toast.makeText(requireContext(), R.string.logged_out, Toast.LENGTH_SHORT).show();

            // Track logout event
            if (analyticsTracker != null) {
                analyticsTracker.trackEvent("user_logged_out", null);
            }

            // Clear any cached data
            if (userViewModel != null) {
                userViewModel.clearUserData();
            }

            // Navigate to login screen
            try {
                Navigation.findNavController(requireView()).navigate(R.id.action_profileFragment_to_loginFragment);
            } catch (Exception e) {
                Log.e(TAG, "Error navigating to login screen: " + e.getMessage());
                // Try alternative navigation
                if (getActivity() != null) {
                    getActivity().recreate();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during logout: " + e.getMessage());
            Toast.makeText(requireContext(), "Error during logout", Toast.LENGTH_SHORT).show();
        }
    }

    private void performHapticFeedback(View view) {
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
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