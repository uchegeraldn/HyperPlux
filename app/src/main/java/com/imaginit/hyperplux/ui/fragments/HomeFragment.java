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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.FragmentHomeBinding;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.models.User;
import com.imaginit.hyperplux.repositories.AssetRepository;
import com.imaginit.hyperplux.repositories.UserRepository;
import com.imaginit.hyperplux.ui.adapters.AssetAdapter;
import com.imaginit.hyperplux.viewmodels.AssetViewModel;
import com.imaginit.hyperplux.viewmodels.UserViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";

    private FragmentHomeBinding binding;
    private AssetViewModel assetViewModel;
    private UserViewModel userViewModel;
    private AssetAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize ViewModels with proper factory
            initializeViewModels();

            // Set up the RecyclerView
            setupRecyclerView();

            // Set up FAB for adding new assets
            binding.addAssetFab.setOnClickListener(v -> {
                try {
                    Navigation.findNavController(v).navigate(R.id.action_homeFragment_to_addEditAssetFragment);
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to add asset screen: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error navigating to add asset screen", Toast.LENGTH_SHORT).show();
                }
            });

            // Observe personalized feed
            assetViewModel.getPersonalizedFeed().observe(getViewLifecycleOwner(), this::handleAssetList);

            // Observe loading state
            assetViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
                binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
                if (isLoading) {
                    binding.contentGroup.setVisibility(View.GONE);
                    binding.emptyStateGroup.setVisibility(View.GONE);
                }
            });

            // Observe current user
            userViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
                if (user != null) {
                    updateWelcomeMessage(user);
                }
            });

            // Observe error messages
            assetViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
            Toast.makeText(requireContext(), "Error initializing home screen", Toast.LENGTH_SHORT).show();
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

            assetViewModel = new ViewModelProvider(this, factory).get(AssetViewModel.class);
            userViewModel = new ViewModelProvider(this, factory).get(UserViewModel.class);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ViewModels: " + e.getMessage());
            throw e;  // Rethrow to be caught by the outer try-catch
        }
    }

    private void setupRecyclerView() {
        try {
            adapter = new AssetAdapter(
                    // Asset click
                    asset -> {
                        try {
                            if (asset != null) {
                                // Navigate to asset detail
                                Bundle args = new Bundle();
                                args.putInt("assetId", asset.getId());
                                Navigation.findNavController(requireView())
                                        .navigate(R.id.action_homeFragment_to_assetDetailFragment, args);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error navigating to asset detail: " + e.getMessage());
                            Toast.makeText(requireContext(), "Error opening asset details", Toast.LENGTH_SHORT).show();
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

            binding.assetsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            binding.assetsRecyclerView.setAdapter(adapter);

            // Initially submit empty list
            adapter.submitList(new ArrayList<>());
        } catch (Exception e) {
            Log.e(TAG, "Error setting up RecyclerView: " + e.getMessage());
            throw e;  // Rethrow to be caught by the outer try-catch
        }
    }

    private void handleAssetList(List<Asset> assets) {
        try {
            if (assets != null && !assets.isEmpty()) {
                adapter.submitList(new ArrayList<>(assets));  // Create new list to ensure DiffUtil works correctly
                showContent();
            } else {
                showEmptyState();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling asset list: " + e.getMessage());
            showEmptyState();
        }
    }

    private void showContent() {
        binding.contentGroup.setVisibility(View.VISIBLE);
        binding.emptyStateGroup.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        binding.contentGroup.setVisibility(View.GONE);
        binding.emptyStateGroup.setVisibility(View.VISIBLE);

        binding.emptyStateButton.setOnClickListener(v -> {
            try {
                Navigation.findNavController(v).navigate(R.id.action_homeFragment_to_addEditAssetFragment);
            } catch (Exception e) {
                Log.e(TAG, "Error navigating from empty state: " + e.getMessage());
                Toast.makeText(requireContext(), "Error navigating to add asset screen", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateWelcomeMessage(User user) {
        try {
            String displayName = user.getDisplayName();
            if (displayName == null || displayName.isEmpty()) {
                FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                if (firebaseUser != null && firebaseUser.getDisplayName() != null) {
                    displayName = firebaseUser.getDisplayName();
                } else if (firebaseUser != null && firebaseUser.getEmail() != null) {
                    displayName = firebaseUser.getEmail().split("@")[0];
                } else {
                    displayName = getString(R.string.there);
                }
            }

            binding.welcomeTextView.setText(getString(R.string.welcome_message, displayName));
        } catch (Exception e) {
            Log.e(TAG, "Error updating welcome message: " + e.getMessage());
            // Fallback to a generic message
            binding.welcomeTextView.setText(getString(R.string.welcome_message, getString(R.string.there)));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}