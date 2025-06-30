package com.imaginit.hyperplux.ui.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.databinding.FragmentOcrScannerBinding;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.models.ReceiptData;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.OcrManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fragment for scanning receipts with OCR
 */
public class OcrScannerFragment extends Fragment {
    private static final String TAG = "OcrScannerFragment";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_GALLERY_IMAGE = 2;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;

    private FragmentOcrScannerBinding binding;
    private OcrManager ocrManager;
    private AnalyticsTracker analyticsTracker;
    private Uri currentPhotoUri;
    private ReceiptData receiptData;
    private boolean isProcessing = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentOcrScannerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize OCR manager
            ocrManager = OcrManager.getInstance(requireContext());

            // Initialize analytics tracker
            analyticsTracker = AnalyticsTracker.getInstance(requireContext());

            // Set up UI elements
            setupUI();

            // Track screen view
            if (analyticsTracker != null) {
                analyticsTracker.trackScreenView("OCRScanner", "OcrScannerFragment");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing OCR scanner: " + e.getMessage());
            Toast.makeText(requireContext(), "Error initializing scanner", Toast.LENGTH_SHORT).show();
            navigateBack();
        }
    }

    private void setupUI() {
        try {
            binding.closeButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    navigateBack();
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating back: " + e.getMessage());
                    navigateBack();
                }
            });

            binding.takePictureButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    if (hasPermission(Manifest.permission.CAMERA)) {
                        dispatchTakePictureIntent();
                    } else {
                        requestCameraPermission();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error taking picture: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error with camera", Toast.LENGTH_SHORT).show();
                }
            });

            binding.pickFromGalleryButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    if (hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        openGallery();
                    } else {
                        requestStoragePermission();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error opening gallery: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error opening gallery", Toast.LENGTH_SHORT).show();
                }
            });

            binding.createAssetButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    if (receiptData != null) {
                        Asset asset = createAssetFromReceipt(receiptData);

                        // Navigate to add/edit asset screen with pre-populated data
                        Bundle args = new Bundle();
                        args.putSerializable("asset", asset);
                        navigateToAddEditAssetFragment(args);

                        // Track asset creation from receipt
                        if (analyticsTracker != null) {
                            Map<String, Object> params = new HashMap<>();
                            params.put("has_total", receiptData.getTotal() > 0);
                            params.put("has_date", receiptData.getDate() != null);
                            params.put("has_store", receiptData.getStore() != null);
                            analyticsTracker.trackEvent("create_asset_from_receipt", params);
                        }
                    } else {
                        Toast.makeText(requireContext(), R.string.no_receipt_data, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error creating asset: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error creating asset", Toast.LENGTH_SHORT).show();
                }
            });

            binding.editResultsButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    // Show the edit screen for manual adjustment
                    // This would be implemented in a real application
                    Toast.makeText(requireContext(), R.string.feature_coming_soon, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error with edit function: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up UI: " + e.getMessage());
        }
    }

    private Asset createAssetFromReceipt(ReceiptData receiptData) {
        // Create a new Asset from receipt data
        Asset asset = new Asset();

        // Set basic information from receipt
        if (receiptData.getItems() != null && !receiptData.getItems().isEmpty()) {
            asset.setName(receiptData.getItems().get(0)); // Use first item as name
        } else {
            asset.setName("Item from receipt");
        }

        asset.setQuantity(1);

        // Set store as purchase location
        if (receiptData.getStore() != null) {
            asset.setPurchaseLocation(receiptData.getStore());
        }

        // Set date
        if (receiptData.getDate() != null) {
            asset.setPurchaseDate(receiptData.getDate());
        }

        // Set price
        if (receiptData.getTotal() > 0) {
            asset.setCost(receiptData.getTotal());
            asset.setCurrentValue(receiptData.getTotal());
        }

        // Set currency to USD by default (could be detected from receipt in a real app)
        asset.setCurrency("USD");

        // Set condition to New by default
        asset.setCondition("New");

        // Current user ID would be set in the AddEditAssetFragment

        return asset;
    }

    private boolean hasPermission(String permission) {
        try {
            return ContextCompat.checkSelfPermission(
                    requireContext(), permission) == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            Log.e(TAG, "Error checking permission: " + e.getMessage());
            return false;
        }
    }

    private void requestCameraPermission() {
        try {
            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);

            // Show permission rationale toast
            Toast.makeText(requireContext(), R.string.camera_permission_rationale, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error requesting camera permission: " + e.getMessage());
        }
    }

    private void requestStoragePermission() {
        try {
            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);

            // Show permission rationale toast
            Toast.makeText(requireContext(), R.string.storage_permission_rationale, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error requesting storage permission: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        try {
            if (requestCode == CAMERA_PERMISSION_CODE) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    dispatchTakePictureIntent();
                } else {
                    Toast.makeText(requireContext(), R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == STORAGE_PERMISSION_CODE) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery();
                } else {
                    Toast.makeText(requireContext(), R.string.storage_permission_denied, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling permission result: " + e.getMessage());
        }
    }

    private void dispatchTakePictureIntent() {
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                // Create the file where the photo should go
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Log.e(TAG, "Error creating image file: " + ex.getMessage());
                    Toast.makeText(requireContext(), R.string.error_creating_file, Toast.LENGTH_SHORT).show();
                    return;
                }

                // Continue only if the file was successfully created
                if (photoFile != null) {
                    currentPhotoUri = FileProvider.getUriForFile(requireContext(),
                            "com.imaginit.hyperplux.fileprovider",
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);

                    // Track camera capture started
                    if (analyticsTracker != null) {
                        analyticsTracker.trackEvent("receipt_camera_started", null);
                    }
                }
            } else {
                Toast.makeText(requireContext(), R.string.no_camera_app, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error launching camera: " + e.getMessage());
            Toast.makeText(requireContext(), "Error launching camera", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireActivity().getFilesDir();
        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }

    private void openGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_GALLERY_IMAGE);

            // Track gallery selection started
            if (analyticsTracker != null) {
                analyticsTracker.trackEvent("receipt_gallery_started", null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening gallery: " + e.getMessage());
            Toast.makeText(requireContext(), "Error opening gallery", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        try {
            if (resultCode == Activity.RESULT_OK) {
                Uri imageUri = null;

                if (requestCode == REQUEST_IMAGE_CAPTURE) {
                    imageUri = currentPhotoUri;

                    // Track camera capture completed
                    if (analyticsTracker != null) {
                        analyticsTracker.trackEvent("receipt_camera_completed", null);
                    }
                } else if (requestCode == REQUEST_GALLERY_IMAGE && data != null) {
                    imageUri = data.getData();

                    // Track gallery selection completed
                    if (analyticsTracker != null) {
                        analyticsTracker.trackEvent("receipt_gallery_completed", null);
                    }
                }

                if (imageUri != null) {
                    processReceiptImage(imageUri);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling activity result: " + e.getMessage());
            Toast.makeText(requireContext(), "Error processing image", Toast.LENGTH_SHORT).show();

            // Hide loading state if it was shown
            binding.loadingOverlay.setVisibility(View.GONE);
            binding.ocrInstructionsContainer.setVisibility(View.VISIBLE);
        }
    }

    private void processReceiptImage(Uri imageUri) {
        try {
            // Avoid processing while another process is running
            if (isProcessing) return;
            isProcessing = true;

            // Show loading state
            binding.loadingOverlay.setVisibility(View.VISIBLE);
            binding.ocrInstructionsContainer.setVisibility(View.GONE);

            // Check if OCR manager is available
            if (ocrManager == null) {
                ocrManager = OcrManager.getInstance(requireContext());
                if (ocrManager == null) {
                    throw new Exception("OCR manager unavailable");
                }
            }

            // Process the image
            ocrManager.processReceiptImage(imageUri, new OcrManager.OcrCallback() {
                @Override
                public void onSuccess(ReceiptData data) {
                    receiptData = data;

                    if (getActivity() != null && !getActivity().isFinishing()) {
                        getActivity().runOnUiThread(() -> {
                            try {
                                isProcessing = false;

                                // Hide loading state
                                binding.loadingOverlay.setVisibility(View.GONE);

                                // Show the receipt thumbnail
                                binding.receiptThumbnail.setImageURI(imageUri);
                                binding.receiptResultsContainer.setVisibility(View.VISIBLE);

                                // Fill the results
                                displayReceiptData(data);

                                // Success feedback
                                performHapticFeedback(binding.getRoot(), true);
                            } catch (Exception e) {
                                Log.e(TAG, "Error displaying receipt data: " + e.getMessage());
                                Toast.makeText(requireContext(), "Error displaying results", Toast.LENGTH_SHORT).show();
                                binding.loadingOverlay.setVisibility(View.GONE);
                                binding.ocrInstructionsContainer.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    if (getActivity() != null && !getActivity().isFinishing()) {
                        getActivity().runOnUiThread(() -> {
                            try {
                                isProcessing = false;

                                // Hide loading state
                                binding.loadingOverlay.setVisibility(View.GONE);
                                binding.ocrInstructionsContainer.setVisibility(View.VISIBLE);

                                // Show error
                                Toast.makeText(requireContext(),
                                        getString(R.string.ocr_error, errorMessage),
                                        Toast.LENGTH_LONG).show();

                                // Error feedback
                                performHapticFeedback(binding.getRoot(), false);
                            } catch (Exception e) {
                                Log.e(TAG, "Error handling OCR failure: " + e.getMessage());
                                binding.loadingOverlay.setVisibility(View.GONE);
                                binding.ocrInstructionsContainer.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            isProcessing = false;
            Log.e(TAG, "Error processing receipt image: " + e.getMessage());
            Toast.makeText(requireContext(), "Error processing image", Toast.LENGTH_SHORT).show();

            // Hide loading state
            binding.loadingOverlay.setVisibility(View.GONE);
            binding.ocrInstructionsContainer.setVisibility(View.VISIBLE);
        }
    }

    private void displayReceiptData(ReceiptData data) {
        try {
            if (data.getStore() != null && !data.getStore().isEmpty()) {
                binding.storeNameText.setText(data.getStore());
                binding.storeNameText.setVisibility(View.VISIBLE);
                binding.storeLabel.setVisibility(View.VISIBLE);
            } else {
                binding.storeNameText.setVisibility(View.GONE);
                binding.storeLabel.setVisibility(View.GONE);
            }

            if (data.getDate() != null) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                binding.dateText.setText(dateFormat.format(data.getDate()));
                binding.dateText.setVisibility(View.VISIBLE);
                binding.dateLabel.setVisibility(View.VISIBLE);
            } else {
                binding.dateText.setVisibility(View.GONE);
                binding.dateLabel.setVisibility(View.GONE);
            }

            if (data.getTotal() > 0) {
                binding.totalText.setText(String.format(Locale.US, "$%.2f", data.getTotal()));
                binding.totalText.setVisibility(View.VISIBLE);
                binding.totalLabel.setVisibility(View.VISIBLE);
            } else {
                binding.totalText.setVisibility(View.GONE);
                binding.totalLabel.setVisibility(View.GONE);
            }

            if (data.getItems() != null && !data.getItems().isEmpty()) {
                StringBuilder itemsText = new StringBuilder();
                for (String item : data.getItems()) {
                    itemsText.append("• ").append(item).append("\n");
                }
                binding.itemsText.setText(itemsText.toString());
                binding.itemsText.setVisibility(View.VISIBLE);
                binding.itemsLabel.setVisibility(View.VISIBLE);
            } else {
                binding.itemsText.setVisibility(View.GONE);
                binding.itemsLabel.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error displaying receipt data: " + e.getMessage());
        }
    }

    private void performHapticFeedback(View view) {
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Exception e) {
            // Ignore haptic feedback errors
        }
    }

    private void performHapticFeedback(View view, boolean isSuccess) {
        try {
            if (isSuccess) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            } else {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            }
        } catch (Exception e) {
            // Ignore haptic feedback errors
        }
    }

    private void navigateBack() {
        try {
            Navigation.findNavController(requireView()).popBackStack();
        } catch (Exception e) {
            Log.e(TAG, "Error navigating back: " + e.getMessage());
            // If navigation fails, try to finish the activity
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        }
    }

    private void navigateToAddEditAssetFragment(Bundle args) {
        try {
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_ocrScannerFragment_to_addEditAssetFragment, args);
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to add/edit asset: " + e.getMessage());
            Toast.makeText(requireContext(), "Error navigating to asset editor", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}