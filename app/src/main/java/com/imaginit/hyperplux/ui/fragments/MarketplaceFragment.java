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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.FragmentMarketplaceBinding;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.repositories.AssetRepository;
import com.imaginit.hyperplux.ui.adapters.AssetAdapter;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.NetworkMonitor;
import com.imaginit.hyperplux.viewmodels.AssetViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import java.util.ArrayList;
import java.util.List;

public class MarketplaceFragment extends Fragment {
    private static final String TAG = "MarketplaceFragment";

    private FragmentMarketplaceBinding binding;
    private AssetViewModel assetViewModel;
    private AssetAdapter adapter;
    private AnalyticsTracker analyticsTracker;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMarketplaceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize ViewModel with proper factory
            initializeViewModel();

            // Initialize utils
            analyticsTracker = AnalyticsTracker.getInstance(requireContext());

            // Set up the RecyclerView with grid layout
            setupRecyclerView();

            // Set up SwipeRefreshLayout
            binding.swipeRefresh.setOnRefreshListener(this::refreshMarketplace);

            // Set up search functionality
            setupSearch();

            // Observe marketplace assets
            assetViewModel.getMarketplaceAssets().observe(getViewLifecycleOwner(), this::handleAssetList);

            // Observe loading state
            assetViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
                binding.progressBar.setVisibility(isLoading && !binding.swipeRefresh.isRefreshing() ?
                        View.VISIBLE : View.GONE);

                if (isLoading) {
                    binding.emptyStateContainer.setVisibility(View.GONE);
                }

                // If load is complete, stop the refresh animation if active
                if (!isLoading) {
                    binding.swipeRefresh.setRefreshing(false);
                }
            });

            // Observe error messages
            assetViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                }
            });

            // Check network status
            NetworkMonitor networkMonitor = NetworkMonitor.getInstance(requireContext());
            if (networkMonitor != null) {
                networkMonitor.getNetworkAvailability().observe(getViewLifecycleOwner(), isConnected -> {
                    binding.offlineWarning.setVisibility(isConnected ? View.GONE : View.VISIBLE);
                });
            }

            // Track screen view
            if (analyticsTracker != null) {
                analyticsTracker.trackScreenView("Marketplace", "MarketplaceFragment");
            }

            // Load initial data
            loadMarketplace();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing marketplace: " + e.getMessage());
            Toast.makeText(requireContext(), "Error loading marketplace", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeViewModel() {
        try {
            AppDatabase database = AppDatabase.getDatabase(requireContext());
            AssetRepository assetRepository = new AssetRepository(database.assetDao());
            ViewModelFactory factory = new ViewModelFactory(
                    requireActivity().getApplication(),
                    assetRepository);

            assetViewModel = new ViewModelProvider(this, factory).get(AssetViewModel.class);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ViewModel: " + e.getMessage());
            throw e; // Rethrow to be caught by the outer try-catch
        }
    }

    private void setupRecyclerView() {
        try {
            // Create adapter
            adapter = new AssetAdapter(
                    // Asset click
                    asset -> {
                        try {
                            if (asset != null) {
                                // Navigate to asset detail
                                Bundle args = new Bundle();
                                args.putInt("assetId", asset.getId());
                                Navigation.findNavController(requireView())
                                        .navigate(R.id.action_marketplaceFragment_to_assetDetailFragment, args);
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

            // Set up grid layout with 2 columns
            int spanCount = 2;
            GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), spanCount);
            binding.assetsRecyclerView.setLayoutManager(layoutManager);
            binding.assetsRecyclerView.setAdapter(adapter);

            // Initially submit empty list
            adapter.submitList(new ArrayList<>());
        } catch (Exception e) {
            Log.e(TAG, "Error setting up RecyclerView: " + e.getMessage());
            throw e; // Rethrow to be caught by the outer try-catch
        }
    }

    private void setupSearch() {
        try {
            binding.searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    if (query != null && !query.isEmpty()) {
                        assetViewModel.searchAssets(query);

                        // Track search
                        if (analyticsTracker != null) {
                            analyticsTracker.trackEvent("marketplace_search", "query", query);
                        }
                    }
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    // Reset to showing all assets when search is cleared
                    if (newText.isEmpty()) {
                        loadMarketplace();
                    }
                    return true;
                }
            });

            // Add clear button listener
            binding.searchView.setOnCloseListener(() -> {
                loadMarketplace();
                return false;
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up search: " + e.getMessage());
        }
    }

    private void handleAssetList(List<Asset> assets) {
        try {
            binding.progressBar.setVisibility(View.GONE);
            binding.swipeRefresh.setRefreshing(false);

            if (assets != null && !assets.isEmpty()) {
                adapter.submitList(new ArrayList<>(assets));  // Create new list to ensure DiffUtil works correctly
                binding.assetsRecyclerView.setVisibility(View.VISIBLE);
                binding.emptyStateContainer.setVisibility(View.GONE);
            } else {
                adapter.submitList(new ArrayList<>());
                binding.assetsRecyclerView.setVisibility(View.GONE);
                binding.emptyStateContainer.setVisibility(View.VISIBLE);

                // Set up empty state buttons
                binding.browseButton.setOnClickListener(v -> loadMarketplace());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling asset list: " + e.getMessage());
            binding.assetsRecyclerView.setVisibility(View.GONE);
            binding.emptyStateContainer.setVisibility(View.VISIBLE);
        }
    }

    private void loadMarketplace() {
        try {
            binding.progressBar.setVisibility(View.VISIBLE);
            assetViewModel.loadMarketplaceAssets();
        } catch (Exception e) {
            Log.e(TAG, "Error loading marketplace: " + e.getMessage());
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(requireContext(), "Error loading marketplace", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshMarketplace() {
        try {
            // The refresh indicator will be shown by SwipeRefreshLayout
            assetViewModel.loadMarketplaceAssets();

            // Track refresh action
            if (analyticsTracker != null) {
                analyticsTracker.trackEvent("marketplace_refreshed", null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing marketplace: " + e.getMessage());
            binding.swipeRefresh.setRefreshing(false);
            Toast.makeText(requireContext(), "Error refreshing marketplace", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}