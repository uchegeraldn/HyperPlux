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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.FragmentAssetDetailBinding;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.repositories.AssetRepository;
import com.imaginit.hyperplux.repositories.UserRepository;
import com.imaginit.hyperplux.viewmodels.AssetViewModel;
import com.imaginit.hyperplux.viewmodels.UserViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class AssetDetailFragment extends Fragment {
    private static final String TAG = "AssetDetailFragment";

    private FragmentAssetDetailBinding binding;
    private AssetViewModel assetViewModel;
    private UserViewModel userViewModel;
    private Asset asset;
    private int assetId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        // Get asset ID from arguments
        if (getArguments() != null) {
            try {
                // Try to use Safe Args
                if (getArguments().containsKey("assetId")) {
                    assetId = getArguments().getInt("assetId", -1);
                }
            } catch (Exception e) {
                // Fallback if Safe Args fails
                assetId = -1;
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAssetDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize ViewModels properly
        try {
            AppDatabase database = AppDatabase.getDatabase(requireContext());
            AssetRepository assetRepository = new AssetRepository(database.assetDao());
            UserRepository userRepository = new UserRepository(database.userDao());

            ViewModelFactory factory = new ViewModelFactory(
                    requireActivity().getApplication(), assetRepository, userRepository);

            assetViewModel = new ViewModelProvider(this, factory).get(AssetViewModel.class);
            userViewModel = new ViewModelProvider(this, factory).get(UserViewModel.class);

            // Check if we have a valid asset ID
            if (assetId > 0) {
                // Observe the selected asset
                assetViewModel.getAssetById(assetId).observe(getViewLifecycleOwner(), retrievedAsset -> {
                    if (retrievedAsset != null) {
                        this.asset = retrievedAsset;
                        updateUI(retrievedAsset);
                        assetViewModel.viewAsset(retrievedAsset); // Track view
                    } else {
                        Toast.makeText(requireContext(), R.string.asset_not_found, Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(requireView()).popBackStack();
                    }
                });
            } else {
                Toast.makeText(requireContext(), R.string.invalid_asset_id, Toast.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).popBackStack();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error loading asset details", Toast.LENGTH_SHORT).show();
            Navigation.findNavController(requireView()).popBackStack();
        }

        // Set up action buttons
        setupActionButtons();
    }

    private void updateUI(Asset asset) {
        try {
            // Load asset image with error handling
            if (asset.getImageUri() != null && !asset.getImageUri().isEmpty()) {
                Glide.with(requireContext())
                        .load(asset.getImageUri())
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.ic_image_placeholder)
                                .error(R.drawable.ic_image_placeholder))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .into(binding.assetImage);
            } else {
                binding.assetImage.setImageResource(R.drawable.ic_image_placeholder);
            }

            // Set asset details
            binding.assetName.setText(asset.getName() != null ? asset.getName() : "");
            binding.assetDescription.setText(asset.getDescription() != null ? asset.getDescription() : "");
            binding.assetCategory.setText(asset.getCategory() != null ? asset.getCategory() : "");
            binding.assetQuantity.setText(getString(R.string.quantity_format, asset.getQuantity()));

            // Set purchase information
            if (asset.getPurchaseDate() != null) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
                binding.purchaseDate.setText(getString(R.string.purchased_on, dateFormat.format(asset.getPurchaseDate())));
                binding.purchaseDate.setVisibility(View.VISIBLE);
            } else {
                binding.purchaseDate.setVisibility(View.GONE);
            }

            // Set purchase location if available
            if (asset.getPurchaseLocation() != null && !asset.getPurchaseLocation().isEmpty()) {
                binding.purchaseLocation.setText(asset.getPurchaseLocation());
                binding.purchaseLocation.setVisibility(View.VISIBLE);
            } else {
                binding.purchaseLocation.setVisibility(View.GONE);
            }

            // Set cost
            binding.cost.setText(getString(R.string.price, String.valueOf(asset.getCost()),
                    asset.getCurrency() != null ? asset.getCurrency() : "USD"));

            // Set condition and location
            binding.condition.setText(asset.getCondition() != null ? asset.getCondition() : "");
            binding.currentLocation.setText(asset.getCurrentLocation() != null ? asset.getCurrentLocation() : "");

            // Set sale status
            updateSaleStatus(asset);

            // Set social metrics
            binding.viewsCount.setText(getString(R.string.views, asset.getViews()));
            binding.likesCount.setText(getString(R.string.likes, asset.getLikes()));

            // Set will information if applicable
            updateWillInfo(asset);

            // Show/hide owner information
            updateOwnerInfo(asset);

            // Show/hide advanced information
            updateAdvancedInfo(asset);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error displaying asset details", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSaleStatus(Asset asset) {
        try {
            if (asset.isForSale()) {
                binding.saleStatus.setText(R.string.for_sale);
                binding.saleStatus.setBackgroundResource(R.drawable.status_indicator_background);
                binding.saleStatus.getBackground().setTint(getResources().getColor(R.color.info, null));
                binding.askingPrice.setVisibility(View.VISIBLE);
                binding.askingPrice.setText(getString(R.string.price,
                        String.valueOf(asset.getAskingPrice()),
                        asset.getCurrency() != null ? asset.getCurrency() : "USD"));
            } else {
                binding.saleStatus.setText(R.string.not_for_sale);
                binding.saleStatus.setBackgroundResource(R.drawable.status_indicator_background);
                binding.saleStatus.getBackground().setTint(getResources().getColor(R.color.dark_gray, null));
                binding.askingPrice.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            // Fallback if there's any issue
            binding.saleStatus.setText(R.string.status_unknown);
            binding.askingPrice.setVisibility(View.GONE);
        }
    }

    private void updateWillInfo(Asset asset) {
        try {
            if (asset.isBequest() && asset.getWillInstructions() != null && !asset.getWillInstructions().isEmpty()) {
                binding.willContainer.setVisibility(View.VISIBLE);
                binding.willInstructions.setText(asset.getWillInstructions());
            } else {
                binding.willContainer.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            binding.willContainer.setVisibility(View.GONE);
        }
    }

    private void updateOwnerInfo(Asset asset) {
        try {
            // Get current user safely
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            boolean isOwner = false;

            if (currentUser != null && asset.getUserId() != null) {
                isOwner = asset.getUserId().equals(currentUser.getUid());
            }

            binding.ownerActions.setVisibility(isOwner ? View.VISIBLE : View.GONE);
            binding.nonOwnerActions.setVisibility(isOwner ? View.GONE : View.VISIBLE);

            // Get owner information
            if (asset.getUserId() != null) {
                userViewModel.getUserById(asset.getUserId()).observe(getViewLifecycleOwner(), owner -> {
                    if (owner != null) {
                        binding.ownerName.setText(owner.getDisplayName() != null ? owner.getDisplayName() : "");
                        if (owner.getProfileImageUri() != null && !owner.getProfileImageUri().isEmpty()) {
                            Glide.with(requireContext())
                                    .load(owner.getProfileImageUri())
                                    .apply(new RequestOptions()
                                            .placeholder(R.drawable.ic_person)
                                            .error(R.drawable.ic_person))
                                    .circleCrop()
                                    .into(binding.ownerImage);
                        } else {
                            binding.ownerImage.setImageResource(R.drawable.ic_person);
                        }
                    } else {
                        binding.ownerName.setText(R.string.unknown_owner);
                        binding.ownerImage.setImageResource(R.drawable.ic_person);
                    }
                });
            } else {
                binding.ownerName.setText(R.string.unknown_owner);
                binding.ownerImage.setImageResource(R.drawable.ic_person);
            }
        } catch (Exception e) {
            // Default to non-owner view
            binding.ownerActions.setVisibility(View.GONE);
            binding.nonOwnerActions.setVisibility(View.VISIBLE);
            binding.ownerName.setText(R.string.unknown_owner);
            binding.ownerImage.setImageResource(R.drawable.ic_person);
        }
    }

    private void updateAdvancedInfo(Asset asset) {
        try {
            // Show advanced info if there's any data
            boolean hasAdvancedInfo = (asset.getBrand() != null && !asset.getBrand().isEmpty()) ||
                    (asset.getModel() != null && !asset.getModel().isEmpty()) ||
                    (asset.getSerialNumber() != null && !asset.getSerialNumber().isEmpty()) ||
                    asset.getWarrantyExpiration() != null;

            binding.advancedInfoContainer.setVisibility(hasAdvancedInfo ? View.VISIBLE : View.GONE);

            if (hasAdvancedInfo) {
                binding.brand.setText(asset.getBrand() != null ? asset.getBrand() : "");
                binding.model.setText(asset.getModel() != null ? asset.getModel() : "");
                binding.serialNumber.setText(asset.getSerialNumber() != null ? asset.getSerialNumber() : "");

                // Set warranty information
                if (asset.getWarrantyExpiration() != null) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
                    boolean isWarrantyValid = asset.getWarrantyExpiration().after(new java.util.Date());

                    if (isWarrantyValid) {
                        binding.warrantyStatus.setText(getString(R.string.warranty_valid,
                                dateFormat.format(asset.getWarrantyExpiration())));
                        binding.warrantyStatus.setTextColor(getResources().getColor(R.color.success, null));
                    } else {
                        binding.warrantyStatus.setText(R.string.warranty_expired);
                        binding.warrantyStatus.setTextColor(getResources().getColor(R.color.error, null));
                    }

                    binding.warrantyInfo.setText(asset.getWarrantyInfo() != null ? asset.getWarrantyInfo() : "");
                    binding.warrantyContainer.setVisibility(View.VISIBLE);
                } else {
                    binding.warrantyContainer.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
            binding.advancedInfoContainer.setVisibility(View.GONE);
        }
    }

    private void setupActionButtons() {
        try {
            // Owner actions
            binding.editButton.setOnClickListener(v -> {
                if (asset != null) {
                    try {
                        Bundle args = new Bundle();
                        args.putSerializable("asset", asset);
                        Navigation.findNavController(v).navigate(
                                R.id.action_assetDetailFragment_to_addEditAssetFragment, args);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Error navigating to edit screen", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // Non-owner actions
            binding.makeOfferButton.setOnClickListener(v -> {
                if (asset != null && asset.isForSale()) {
                    try {
                        Bundle args = new Bundle();
                        args.putInt("assetId", asset.getId());
                        args.putString("transactionType", "SALE");
                        Navigation.findNavController(v).navigate(
                                R.id.action_assetDetailFragment_to_transactionCreateFragment, args);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Error navigating to transaction screen", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(requireContext(), R.string.not_for_sale, Toast.LENGTH_SHORT).show();
                }
            });

            // Social interaction buttons
            binding.likeButton.setOnClickListener(v -> {
                if (asset != null) {
                    assetViewModel.likeAssetWithErrorHandling(asset);
                    // Update UI immediately for better UX
                    asset.setLikes(asset.getLikes() + 1);
                    binding.likesCount.setText(getString(R.string.likes, asset.getLikes()));
                }
            });

            binding.shareButton.setOnClickListener(v -> {
                if (asset != null) {
                    assetViewModel.shareAssetWithTracking(asset);
                    // Implement sharing functionality
                    shareAsset(asset);
                }
            });

            // View owner profile
            binding.ownerContainer.setOnClickListener(v -> {
                if (asset != null && asset.getUserId() != null) {
                    try {
                        Bundle args = new Bundle();
                        args.putString("userId", asset.getUserId());
                        Navigation.findNavController(v).navigate(
                                R.id.action_assetDetailFragment_to_userProfileFragment, args);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Error navigating to user profile", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error setting up action buttons", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareAsset(Asset asset) {
        try {
            // Create a share intent
            String shareText = getString(R.string.share_asset_text,
                    asset.getName(),
                    asset.getDescription() != null ? asset.getDescription() : "");

            android.content.Intent shareIntent = new android.content.Intent();
            shareIntent.setAction(android.content.Intent.ACTION_SEND);
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
            shareIntent.setType("text/plain");

            // Create chooser and start activity
            android.content.Intent chooser = android.content.Intent.createChooser(
                    shareIntent, getString(R.string.share_asset_title));
            startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error sharing asset", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_asset_detail, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Handle menu item clicks
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}