package com.imaginit.hyperplux.ui.fragments;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.FragmentAddEditAssetBinding;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.repositories.AssetRepository;
import com.imaginit.hyperplux.viewmodels.AssetViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class AddEditAssetFragment extends Fragment {
    private static final String TAG = "AddEditAssetFragment";

    private FragmentAddEditAssetBinding binding;
    private AssetViewModel viewModel;
    private Asset asset;
    private Uri selectedImageUri;
    private Date purchaseDate = new Date();
    private Date warrantyDate;
    private String[] conditionOptions = {"New", "Excellent", "Good", "Fair", "Poor"};
    private String[] currencyOptions = {"USD", "EUR", "GBP", "JPY", "CAD", "AUD"};
    private String[] categoryOptions = {
            "Electronics", "Furniture", "Vehicle", "Jewelry", "Art", "Clothing",
            "Collectibles", "Real Estate", "Financial", "Documents", "Other"
    };

    // Image picker
    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                            selectedImageUri = result.getData().getData();
                            // Load the image into the ImageView
                            Glide.with(requireContext())
                                    .load(selectedImageUri)
                                    .centerCrop()
                                    .into(binding.assetImageView);
                        }
                    });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAddEditAssetBinding.inflate(inflater, container, false);

        if (getContext() == null) {
            Log.e(TAG, "Context is null in onCreateView");
            return binding.getRoot();
        }

        // Initialize ViewModel
        AppDatabase database = AppDatabase.getDatabase(requireContext());
        AssetRepository repository = new AssetRepository(database.assetDao());
        ViewModelFactory factory = new ViewModelFactory(requireActivity().getApplication(), repository);
        viewModel = new ViewModelProvider(this, factory).get(AssetViewModel.class);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set up spinners
        setupSpinners();

        // Set up date pickers
        setupDatePickers();

        // Check if we're editing an existing asset
        if (getArguments() != null) {
            try {
                if (getArguments().containsKey("asset")) {
                    asset = (Asset) getArguments().getSerializable("asset");
                } else if (getArguments().containsKey("assetId")) {
                    int assetId = getArguments().getInt("assetId", -1);
                    if (assetId != -1) {
                        viewModel.getAssetById(assetId).observe(getViewLifecycleOwner(), retrievedAsset -> {
                            if (retrievedAsset != null) {
                                asset = retrievedAsset;
                                populateFields();
                                binding.deleteButton.setVisibility(View.VISIBLE);
                                binding.toolbarTitle.setText(R.string.edit_asset);
                            }
                        });
                    }
                }

                if (asset != null) {
                    populateFields();
                    binding.deleteButton.setVisibility(View.VISIBLE);
                    binding.toolbarTitle.setText(R.string.edit_asset);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving asset from arguments: " + e.getMessage());
                binding.deleteButton.setVisibility(View.GONE);
                binding.toolbarTitle.setText(R.string.add_asset);
            }
        } else {
            binding.deleteButton.setVisibility(View.GONE);
            binding.toolbarTitle.setText(R.string.add_asset);
        }

        // Set up action buttons
        binding.saveButton.setOnClickListener(v -> saveAsset());
        binding.deleteButton.setOnClickListener(v -> deleteAsset());
        binding.cancelButton.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());

        // Image selection
        binding.selectImageButton.setOnClickListener(v -> openImagePicker());

        // Toggle advanced fields
        binding.showAdvancedFieldsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.advancedFieldsContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Observe loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.saveButton.setEnabled(!isLoading);
        });

        // Observe error messages
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupSpinners() {
        try {
            // Condition spinner
            ArrayAdapter<String> conditionAdapter = new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_spinner_item, conditionOptions);
            conditionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.conditionSpinner.setAdapter(conditionAdapter);

            // Currency spinner
            ArrayAdapter<String> currencyAdapter = new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_spinner_item, currencyOptions);
            currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.currencySpinner.setAdapter(currencyAdapter);

            // Category spinner
            ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_spinner_item, categoryOptions);
            categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.categorySpinner.setAdapter(categoryAdapter);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up spinners: " + e.getMessage());
        }
    }

    private void setupDatePickers() {
        try {
            // Format for displaying dates
            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);

            // Purchase date picker
            binding.purchaseDateButton.setText(dateFormat.format(purchaseDate));
            binding.purchaseDateButton.setOnClickListener(v -> {
                Calendar calendar = Calendar.getInstance();
                if (purchaseDate != null) {
                    calendar.setTime(purchaseDate);
                }

                DatePickerDialog datePickerDialog = new DatePickerDialog(
                        requireContext(),
                        (view, year, month, dayOfMonth) -> {
                            Calendar selectedDate = Calendar.getInstance();
                            selectedDate.set(year, month, dayOfMonth);
                            purchaseDate = selectedDate.getTime();
                            binding.purchaseDateButton.setText(dateFormat.format(purchaseDate));
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );
                datePickerDialog.show();
            });

            // Warranty date picker
            binding.warrantyDateButton.setOnClickListener(v -> {
                Calendar calendar = Calendar.getInstance();
                if (warrantyDate != null) {
                    calendar.setTime(warrantyDate);
                }

                DatePickerDialog datePickerDialog = new DatePickerDialog(
                        requireContext(),
                        (view, year, month, dayOfMonth) -> {
                            Calendar selectedDate = Calendar.getInstance();
                            selectedDate.set(year, month, dayOfMonth);
                            warrantyDate = selectedDate.getTime();
                            binding.warrantyDateButton.setText(dateFormat.format(warrantyDate));
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );
                datePickerDialog.show();
            });

            if (warrantyDate == null) {
                binding.warrantyDateButton.setText(R.string.select_date);
            } else {
                binding.warrantyDateButton.setText(dateFormat.format(warrantyDate));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up date pickers: " + e.getMessage());
        }
    }

    private void openImagePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening image picker: " + e.getMessage());
            Toast.makeText(getContext(), "Error opening image picker", Toast.LENGTH_SHORT).show();
        }
    }

    private void populateFields() {
        try {
            if (asset == null) return;

            // Basic information
            binding.nameEditText.setText(asset.getName());
            binding.quantityEditText.setText(String.valueOf(asset.getQuantity()));
            binding.descriptionEditText.setText(asset.getDescription());

            // Find category position in spinner
            if (asset.getCategory() != null) {
                for (int i = 0; i < categoryOptions.length; i++) {
                    if (categoryOptions[i].equals(asset.getCategory())) {
                        binding.categorySpinner.setSelection(i);
                        break;
                    }
                }
            }

            binding.brandEditText.setText(asset.getBrand());
            binding.modelEditText.setText(asset.getModel());
            binding.serialNumberEditText.setText(asset.getSerialNumber());

            // Financial information
            binding.costEditText.setText(String.valueOf(asset.getCost()));

            // Find currency position in spinner
            if (asset.getCurrency() != null) {
                for (int i = 0; i < currencyOptions.length; i++) {
                    if (currencyOptions[i].equals(asset.getCurrency())) {
                        binding.currencySpinner.setSelection(i);
                        break;
                    }
                }
            }

            binding.currentValueEditText.setText(String.valueOf(asset.getCurrentValue()));
            binding.purchaseLocationEditText.setText(asset.getPurchaseLocation());

            // Update purchase date
            if (asset.getPurchaseDate() != null) {
                purchaseDate = asset.getPurchaseDate();
                SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
                binding.purchaseDateButton.setText(dateFormat.format(purchaseDate));
            }

            // Location information
            binding.currentLocationEditText.setText(asset.getCurrentLocation());
            binding.sharedCheckbox.setChecked(asset.isShared());

            // Condition
            if (asset.getCondition() != null) {
                for (int i = 0; i < conditionOptions.length; i++) {
                    if (conditionOptions[i].equals(asset.getCondition())) {
                        binding.conditionSpinner.setSelection(i);
                        break;
                    }
                }
            }

            // Sale status
            binding.forSaleCheckbox.setChecked(asset.isForSale());
            binding.askingPriceEditText.setText(String.valueOf(asset.getAskingPrice()));
            binding.hiddenCheckbox.setChecked(asset.isHidden());

            // Image
            if (asset.getImageUri() != null && !asset.getImageUri().isEmpty()) {
                Glide.with(requireContext())
                        .load(asset.getImageUri())
                        .centerCrop()
                        .into(binding.assetImageView);
            }

            // Warranty information
            if (asset.getWarrantyExpiration() != null) {
                warrantyDate = asset.getWarrantyExpiration();
                SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
                binding.warrantyDateButton.setText(dateFormat.format(warrantyDate));
            }
            binding.warrantyInfoEditText.setText(asset.getWarrantyInfo());

            // Will instructions
            binding.willInstructionsEditText.setText(asset.getWillInstructions());
            binding.bequestCheckbox.setChecked(asset.isBequest());

            // Show advanced fields if they contain data
            boolean hasAdvancedData = asset.getBrand() != null || asset.getModel() != null
                    || asset.getSerialNumber() != null || asset.getCurrentValue() > 0
                    || asset.getWarrantyInfo() != null || asset.getWillInstructions() != null;

            binding.showAdvancedFieldsCheckbox.setChecked(hasAdvancedData);
            binding.advancedFieldsContainer.setVisibility(hasAdvancedData ? View.VISIBLE : View.GONE);
        } catch (Exception e) {
            Log.e(TAG, "Error populating fields: " + e.getMessage());
        }
    }

    private void saveAsset() {
        try {
            // Validate required fields
            String name = binding.nameEditText.getText().toString().trim();
            if (name.isEmpty()) {
                binding.nameEditText.setError(getString(R.string.field_required));
                binding.nameEditText.requestFocus();
                return;
            }

            // Get current user
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(getContext(), R.string.must_be_logged_in, Toast.LENGTH_SHORT).show();
                return;
            }

            // Parse quantity
            int quantity;
            try {
                String quantityText = binding.quantityEditText.getText().toString().trim();
                quantity = quantityText.isEmpty() ? 1 : Integer.parseInt(quantityText);
            } catch (NumberFormatException e) {
                quantity = 1;
            }

            // Create or update asset object
            final Asset assetToSave;
            if (asset == null) {
                // Create new asset with minimum required fields
                assetToSave = new Asset(name, quantity, user.getUid());
            } else {
                // Update existing asset
                assetToSave = asset;
                assetToSave.setName(name);
                assetToSave.setQuantity(quantity);
            }

            // Set basic information
            assetToSave.setDescription(binding.descriptionEditText.getText().toString().trim());
            assetToSave.setCategory(categoryOptions[binding.categorySpinner.getSelectedItemPosition()]);
            assetToSave.setBrand(binding.brandEditText.getText().toString().trim());
            assetToSave.setModel(binding.modelEditText.getText().toString().trim());
            assetToSave.setSerialNumber(binding.serialNumberEditText.getText().toString().trim());

            // Set financial information
            try {
                String costText = binding.costEditText.getText().toString().trim();
                double cost = costText.isEmpty() ? 0 : Double.parseDouble(costText);
                assetToSave.setCost(cost);
            } catch (NumberFormatException e) {
                assetToSave.setCost(0);
            }

            assetToSave.setCurrency(currencyOptions[binding.currencySpinner.getSelectedItemPosition()]);

            try {
                String valueText = binding.currentValueEditText.getText().toString().trim();
                double value = valueText.isEmpty() ? 0 : Double.parseDouble(valueText);
                assetToSave.setCurrentValue(value);
            } catch (NumberFormatException e) {
                assetToSave.setCurrentValue(0);
            }

            assetToSave.setPurchaseLocation(binding.purchaseLocationEditText.getText().toString().trim());
            assetToSave.setPurchaseDate(purchaseDate);

            // Set location information
            assetToSave.setCurrentLocation(binding.currentLocationEditText.getText().toString().trim());
            assetToSave.setShared(binding.sharedCheckbox.isChecked());

            // Set condition
            assetToSave.setCondition(conditionOptions[binding.conditionSpinner.getSelectedItemPosition()]);

            // Set sale status
            assetToSave.setForSale(binding.forSaleCheckbox.isChecked());
            try {
                String priceText = binding.askingPriceEditText.getText().toString().trim();
                double price = priceText.isEmpty() ? 0 : Double.parseDouble(priceText);
                assetToSave.setAskingPrice(price);
            } catch (NumberFormatException e) {
                assetToSave.setAskingPrice(0);
            }
            assetToSave.setHidden(binding.hiddenCheckbox.isChecked());

            // Set warranty information
            assetToSave.setWarrantyExpiration(warrantyDate);
            assetToSave.setWarrantyInfo(binding.warrantyInfoEditText.getText().toString().trim());

            // Set will instructions
            assetToSave.setWillInstructions(binding.willInstructionsEditText.getText().toString().trim());
            assetToSave.setBequest(binding.bequestCheckbox.isChecked());

            // Upload image if selected
            if (selectedImageUri != null) {
                viewModel.uploadAssetImage(selectedImageUri, imageUrl -> {
                    if (imageUrl != null) {
                        assetToSave.setImageUri(imageUrl);
                        saveAssetToDatabase(assetToSave);
                    } else {
                        // Still save if image upload fails
                        saveAssetToDatabase(assetToSave);
                    }
                });
            } else {
                saveAssetToDatabase(assetToSave);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving asset: " + e.getMessage());
            Toast.makeText(getContext(), "Error saving asset: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAssetToDatabase(Asset assetToSave) {
        try {
            if (asset == null) {
                // New asset
                viewModel.addAssetWithValidation(assetToSave);
            } else {
                // Update existing asset
                viewModel.updateAssetWithValidation(assetToSave);
            }

            // Navigate back to asset list
            Navigation.findNavController(binding.getRoot()).popBackStack();
        } catch (Exception e) {
            Log.e(TAG, "Error saving asset to database: " + e.getMessage());
            Toast.makeText(getContext(), "Error saving asset: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteAsset() {
        try {
            if (asset != null) {
                viewModel.deleteAssetWithVerification(asset);
                Navigation.findNavController(binding.getRoot()).popBackStack();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting asset: " + e.getMessage());
            Toast.makeText(getContext(), "Error deleting asset: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}