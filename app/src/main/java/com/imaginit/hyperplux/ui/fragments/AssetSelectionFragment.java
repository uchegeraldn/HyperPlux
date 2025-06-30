package com.imaginit.hyperplux.ui.fragments;

import android.content.Context;
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
import androidx.recyclerview.widget.GridLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.FragmentAssetSelectionBinding;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.repositories.AssetRepository;
import com.imaginit.hyperplux.ui.adapters.AssetGridAdapter;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.HapticFeedbackManager;
import com.imaginit.hyperplux.viewmodels.AssetViewModel;
import com.imaginit.hyperplux.viewmodels.ChatViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import java.util.ArrayList;

/**
 * Fragment for selecting assets to share in chat
 */
public class AssetSelectionFragment extends Fragment {
    private FragmentAssetSelectionBinding binding;
    private AssetViewModel assetViewModel;
    private ChatViewModel chatViewModel;
    private AssetGridAdapter adapter;
    private String chatRoomId;
    private HapticFeedbackManager hapticManager;
    private AnalyticsTracker analyticsTracker;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAssetSelectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize ViewModels with proper factory
            AppDatabase database = AppDatabase.getDatabase(requireContext());
            AssetRepository repository = new AssetRepository(database.assetDao());
            ViewModelFactory factory = new ViewModelFactory(requireActivity().getApplication(), repository);

            assetViewModel = new ViewModelProvider(this, factory).get(AssetViewModel.class);
            chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);

            // Initialize utility classes
            try {
                hapticManager = HapticFeedbackManager.getInstance(requireContext());
            } catch (Exception e) {
                // Fallback if HapticFeedbackManager isn't available
                hapticManager = null;
            }

            analyticsTracker = AnalyticsTracker.getInstance(requireContext());

            // Get chat room ID from arguments
            if (getArguments() != null && getArguments().containsKey("chatRoomId")) {
                chatRoomId = getArguments().getString("chatRoomId");
                if (chatRoomId == null || chatRoomId.isEmpty()) {
                    throw new IllegalArgumentException("Invalid chat room ID");
                }
            } else {
                throw new IllegalArgumentException("Chat room ID not provided");
            }

            // Set up toolbar
            binding.backButton.setOnClickListener(v -> {
                performHapticFeedback(v, HapticFeedbackManager.CLICK);
                Navigation.findNavController(v).popBackStack();
            });

            // Set up asset grid
            adapter = new AssetGridAdapter(this::onAssetSelected);
            binding.assetRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            binding.assetRecyclerView.setAdapter(adapter);

            // Load user's assets
            loadAssets();

            // Track screen view
            if (analyticsTracker != null) {
                analyticsTracker.trackScreenView("AssetSelection", "AssetSelectionFragment");
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error initializing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Navigation.findNavController(view).popBackStack();
        }
    }

    private void performHapticFeedback(View view, int feedbackType) {
        if (hapticManager != null) {
            try {
                switch (feedbackType) {
                    case HapticFeedbackManager.CLICK:
                        hapticManager.performClick(view);
                        break;
                    case HapticFeedbackManager.SUCCESS:
                        hapticManager.performSuccess();
                        break;
                    case HapticFeedbackManager.ERROR:
                        hapticManager.performError();
                        break;
                }
            } catch (Exception e) {
                // Fallback to basic haptic feedback
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            }
        }
    }

    private void loadAssets() {
        try {
            binding.loadingProgressBar.setVisibility(View.VISIBLE);
            binding.emptyStateContainer.setVisibility(View.GONE);

            assetViewModel.getAssets().observe(getViewLifecycleOwner(), assets -> {
                binding.loadingProgressBar.setVisibility(View.GONE);

                if (assets != null) {
                    if (assets.isEmpty()) {
                        binding.emptyStateContainer.setVisibility(View.VISIBLE);
                    } else {
                        adapter.submitList(assets);
                    }
                } else {
                    binding.emptyStateContainer.setVisibility(View.VISIBLE);
                    Toast.makeText(requireContext(),
                            "Error loading assets",
                            Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            binding.loadingProgressBar.setVisibility(View.GONE);
            binding.emptyStateContainer.setVisibility(View.VISIBLE);
            Toast.makeText(requireContext(),
                    "Error loading assets: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void onAssetSelected(Asset asset) {
        if (asset == null) return;

        try {
            performHapticFeedback(binding.getRoot(), HapticFeedbackManager.CLICK);

            // Show confirmation dialog
            binding.confirmationDialog.setVisibility(View.VISIBLE);
            binding.assetNameText.setText(asset.getName() != null ? asset.getName() : "");

            // Load asset image
            String imageUri = asset.getImageUri();
            if (imageUri != null && !imageUri.isEmpty()) {
                Glide.with(requireContext())
                        .load(imageUri)
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.ic_image_placeholder)
                                .error(R.drawable.ic_image_placeholder))
                        .into(binding.assetImage);
            } else {
                binding.assetImage.setImageResource(R.drawable.ic_image_placeholder);
            }

            // Set up confirmation dialog buttons
            binding.cancelButton.setOnClickListener(v -> {
                binding.confirmationDialog.setVisibility(View.GONE);
            });

            binding.confirmButton.setOnClickListener(v -> {
                binding.confirmationDialog.setVisibility(View.GONE);
                binding.sendingProgressBar.setVisibility(View.VISIBLE);

                // Send asset message
                String assetId = String.valueOf(asset.getId());
                chatViewModel.sendAssetMessage(chatRoomId, assetId)
                        .observe(getViewLifecycleOwner(), chatMessage -> {
                            binding.sendingProgressBar.setVisibility(View.GONE);

                            if (chatMessage != null) {
                                performHapticFeedback(binding.getRoot(), HapticFeedbackManager.SUCCESS);

                                // Navigate back to chat
                                Navigation.findNavController(binding.getRoot()).popBackStack();

                                // Track asset shared event
                                if (analyticsTracker != null) {
                                    analyticsTracker.trackEvent("asset_shared_in_chat",
                                            "asset_id", String.valueOf(asset.getId()));
                                }
                            } else {
                                Toast.makeText(requireContext(),
                                        "Error sharing asset",
                                        Toast.LENGTH_SHORT).show();
                                performHapticFeedback(binding.getRoot(), HapticFeedbackManager.ERROR);
                            }
                        });
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Error displaying asset: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            binding.confirmationDialog.setVisibility(View.GONE);
        }
    }

    // Add constants for HapticFeedbackManager if it doesn't exist
    private static class HapticFeedbackManager {
        static final int CLICK = 1;
        static final int SUCCESS = 2;
        static final int ERROR = 3;

        static HapticFeedbackManager getInstance(Context context) {
            // Implementation would normally go here
            return new HapticFeedbackManager();
        }

        void performClick(View view) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }

        void performSuccess() {
            // Placeholder implementation
        }

        void performError() {
            // Placeholder implementation
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}