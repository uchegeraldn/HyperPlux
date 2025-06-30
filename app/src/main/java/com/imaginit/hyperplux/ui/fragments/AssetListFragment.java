package com.imaginit.hyperplux.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.FragmentAssetListBinding;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.repositories.AssetRepository;
import com.imaginit.hyperplux.ui.adapters.AssetAdapter;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.viewmodels.AssetViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import java.util.ArrayList;
import java.util.List;

public class AssetListFragment extends Fragment {
    private static final String TAG = "AssetListFragment";

    private FragmentAssetListBinding binding;
    private AssetViewModel viewModel;
    private AssetAdapter adapter;
    private AnalyticsTracker analyticsTracker;

    // Track observers to prevent memory leaks
    private Observer<List<Asset>> allAssetsObserver;
    private Observer<List<Asset>> assetsForSaleObserver;
    private Observer<List<Asset>> hiddenAssetsObserver;
    private Observer<List<Asset>> searchResultsObserver;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAssetListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize ViewModel properly with factory
            AppDatabase database = AppDatabase.getDatabase(requireContext());
            AssetRepository repository = new AssetRepository(database.assetDao());
            ViewModelFactory factory = new ViewModelFactory(requireActivity().getApplication(), repository);
            viewModel = new ViewModelProvider(this, factory).get(AssetViewModel.class);

            // Initialize Analytics Tracker
            analyticsTracker = AnalyticsTracker.getInstance(requireContext());

            // Set up RecyclerView
            setupRecyclerView();

            // Set up SearchView
            setupSearchView();

            // Set up Tab Layout
            setupTabLayout();

            // Set up SwipeRefreshLayout
            binding.swipeRefresh.setOnRefreshListener(this::refreshAssets);

            // Set up FAB
            binding.addAssetButton.setOnClickListener(v -> {
                try {
                    Navigation.findNavController(v).navigate(R.id.action_assetListFragment_to_addEditAssetFragment);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Error navigating to add asset screen", Toast.LENGTH_SHORT).show();
                }
            });

            // Set up empty state button
            binding.addFirstAssetButton.setOnClickListener(v -> {
                try {
                    Navigation.findNavController(v).navigate(R.id.action_assetListFragment_to_addEditAssetFragment);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Error navigating to add asset screen", Toast.LENGTH_SHORT).show();
                }
            });

            // Initialize observers
            initializeObservers();

            // Observe assets based on default tab
            observeAssets(binding.tabLayout.getSelectedTabPosition());

            // Observe loading state
            viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
                binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
                binding.swipeRefresh.setRefreshing(isLoading);
            });

            // Observe error messages
            viewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                }
            });

            // Track screen view
            if (analyticsTracker != null) {
                analyticsTracker.trackScreenView("AssetList", "AssetListFragment");
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error initializing asset list", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeObservers() {
        // Create observers that will be reused
        allAssetsObserver = assets -> updateUI(assets);
        assetsForSaleObserver = assets -> updateUI(assets);
        hiddenAssetsObserver = assets -> updateUI(assets);
        searchResultsObserver = assets -> {
            if (assets != null) {
                updateUI(assets);
                // Update analytics with actual result count
                String query = binding.searchView.getQuery().toString();
                if (!query.isEmpty() && analyticsTracker != null) {
                    analyticsTracker.trackSearch(query, assets.size());
                }
            }
        };

        // Observe search results
        viewModel.getSearchResults().observe(getViewLifecycleOwner(), searchResultsObserver);
    }

    private void setupRecyclerView() {
        adapter = new AssetAdapter(
                // Asset click
                asset -> {
                    try {
                        if (asset != null) {
                            viewModel.viewAsset(asset); // Track view

                            // Navigate to detail
                            Bundle args = new Bundle();
                            args.putInt("assetId", asset.getId());
                            Navigation.findNavController(requireView())
                                    .navigate(R.id.action_assetListFragment_to_assetDetailFragment, args);
                        }
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Error navigating to asset details", Toast.LENGTH_SHORT).show();
                    }
                },
                // Like click
                asset -> {
                    if (asset != null) {
                        viewModel.likeAssetWithErrorHandling(asset);
                    }
                },
                // Dislike click
                asset -> {
                    if (asset != null) {
                        viewModel.dislikeAssetWithErrorHandling(asset);
                    }
                }
        );

        binding.assetRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.assetRecyclerView.setAdapter(adapter);

        // Initially submit empty list
        adapter.submitList(new ArrayList<>());
    }

    private void setupSearchView() {
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query != null && !query.isEmpty()) {
                    viewModel.searchAssets(query);
                    if (analyticsTracker != null) {
                        analyticsTracker.trackSearch(query, 0); // Count will be updated when results arrive
                    }
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    // Reset to showing user's assets
                    refreshAssets();
                }
                return true;
            }
        });
    }

    private void setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // Change displayed assets based on selected tab
                observeAssets(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Remove observer based on tab position
                removeObserver(tab.getPosition());
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Refresh data on tab reselection
                refreshAssets();
            }
        });
    }

    private void observeAssets(int tabPosition) {
        // Remove any existing observers first
        removeAllObservers();

        switch (tabPosition) {
            case 0: // All Assets
                viewModel.getAssets().observe(getViewLifecycleOwner(), allAssetsObserver);
                break;
            case 1: // Assets For Sale
                viewModel.getAssetsForSale().observe(getViewLifecycleOwner(), assetsForSaleObserver);
                break;
            case 2: // Hidden Assets
                viewModel.getHiddenAssets().observe(getViewLifecycleOwner(), hiddenAssetsObserver);
                break;
        }
    }

    private void removeObserver(int tabPosition) {
        switch (tabPosition) {
            case 0: // All Assets
                if (allAssetsObserver != null) {
                    viewModel.getAssets().removeObserver(allAssetsObserver);
                }
                break;
            case 1: // Assets For Sale
                if (assetsForSaleObserver != null) {
                    viewModel.getAssetsForSale().removeObserver(assetsForSaleObserver);
                }
                break;
            case 2: // Hidden Assets
                if (hiddenAssetsObserver != null) {
                    viewModel.getHiddenAssets().removeObserver(hiddenAssetsObserver);
                }
                break;
        }
    }

    private void removeAllObservers() {
        if (allAssetsObserver != null) {
            viewModel.getAssets().removeObserver(allAssetsObserver);
        }
        if (assetsForSaleObserver != null) {
            viewModel.getAssetsForSale().removeObserver(assetsForSaleObserver);
        }
        if (hiddenAssetsObserver != null) {
            viewModel.getHiddenAssets().removeObserver(hiddenAssetsObserver);
        }
    }

    private void updateUI(List<Asset> assets) {
        try {
            if (assets != null && !assets.isEmpty()) {
                adapter.submitList(new ArrayList<>(assets)); // Create new list to ensure DiffUtil works correctly
                showContent();
            } else {
                adapter.submitList(new ArrayList<>());
                showEmptyState();
            }
        } catch (Exception e) {
            adapter.submitList(new ArrayList<>());
            showEmptyState();
            Toast.makeText(requireContext(), "Error updating asset list", Toast.LENGTH_SHORT).show();
        }
    }

    private void showContent() {
        binding.assetRecyclerView.setVisibility(View.VISIBLE);
        binding.emptyStateContainer.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        binding.assetRecyclerView.setVisibility(View.GONE);
        binding.emptyStateContainer.setVisibility(View.VISIBLE);
    }

    private void refreshAssets() {
        try {
            viewModel.loadAssets();

            // Re-observe the current tab's data
            observeAssets(binding.tabLayout.getSelectedTabPosition());
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error refreshing assets", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_asset_list, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            refreshAssets();
            return true;
        } else if (id == R.id.action_sort) {
            // Show sort options dialog
            // Not implemented in this version
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        // Clean up observers
        removeAllObservers();
        if (searchResultsObserver != null) {
            viewModel.getSearchResults().removeObserver(searchResultsObserver);
        }

        super.onDestroyView();
        binding = null;
    }
}