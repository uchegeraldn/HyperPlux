package com.imaginit.hyperplux.ui.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.FragmentQrScannerBinding;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.models.ProductInfo;
import com.imaginit.hyperplux.repositories.AssetRepository;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.viewmodels.AssetViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fragment for scanning QR codes to create or find assets
 */
public class QrScannerFragment extends Fragment {
    private static final String TAG = "QrScannerFragment";
    private static final int CAMERA_PERMISSION_CODE = 100;

    private FragmentQrScannerBinding binding;
    private AssetViewModel viewModel;
    private ExecutorService cameraExecutor;
    private FirebaseVisionBarcodeDetector barcodeDetector;
    private ProcessCameraProvider cameraProvider;
    private String scannedBarcode = null;
    private boolean cameraInitialized = false;
    private boolean processingBarcode = false;
    private AnalyticsTracker analyticsTracker;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentQrScannerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize ViewModel properly
            initializeViewModel();

            // Initialize analytics tracker
            analyticsTracker = AnalyticsTracker.getInstance(requireContext());

            // Set up barcode detector
            FirebaseVisionBarcodeDetectorOptions options = new FirebaseVisionBarcodeDetectorOptions.Builder()
                    .setBarcodeFormats(
                            FirebaseVisionBarcode.FORMAT_QR_CODE,
                            FirebaseVisionBarcode.FORMAT_EAN_13,
                            FirebaseVisionBarcode.FORMAT_EAN_8,
                            FirebaseVisionBarcode.FORMAT_UPC_A,
                            FirebaseVisionBarcode.FORMAT_UPC_E,
                            FirebaseVisionBarcode.FORMAT_CODE_128)
                    .build();

            barcodeDetector = FirebaseVision.getInstance().getVisionBarcodeDetector(options);

            // Set up the camera executor
            cameraExecutor = Executors.newSingleThreadExecutor();

            binding.closeButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    Navigation.findNavController(v).popBackStack();
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating back: " + e.getMessage());
                    if (getActivity() != null) {
                        getActivity().onBackPressed();
                    }
                }
            });

            binding.flashButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    // Toggle flashlight - would be implemented in camera setup
                    Toast.makeText(requireContext(), "Flashlight toggle not implemented", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error toggling flashlight: " + e.getMessage());
                }
            });

            // Check camera permission
            if (hasCameraPermission()) {
                startCamera();
            } else {
                requestCameraPermission();
            }

            // Track screen view
            if (analyticsTracker != null) {
                analyticsTracker.trackScreenView("QRScanner", "QrScannerFragment");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
            Toast.makeText(requireContext(), "Error initializing QR scanner", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeViewModel() {
        try {
            AppDatabase database = AppDatabase.getDatabase(requireContext());
            AssetRepository repository = new AssetRepository(database.assetDao());
            ViewModelFactory factory = new ViewModelFactory(
                    requireActivity().getApplication(),
                    repository);

            viewModel = new ViewModelProvider(this, factory).get(AssetViewModel.class);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ViewModel: " + e.getMessage());
            throw e; // Rethrow to be caught by the outer try-catch
        }
    }

    private boolean hasCameraPermission() {
        try {
            return ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            Log.e(TAG, "Error checking camera permission: " + e.getMessage());
            return false;
        }
    }

    private void requestCameraPermission() {
        try {
            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);

            // Show rationale UI
            binding.permissionRequiredContainer.setVisibility(View.VISIBLE);
            binding.previewView.setVisibility(View.GONE);
            binding.scannerOverlay.setVisibility(View.GONE);

            // Setup grant permission button
            binding.grantPermissionButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    requestCameraPermission();
                } catch (Exception e) {
                    Log.e(TAG, "Error requesting permission: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error requesting camera permission: " + e.getMessage());
            Toast.makeText(requireContext(), "Error requesting camera permission", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        try {
            if (requestCode == CAMERA_PERMISSION_CODE) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    binding.permissionRequiredContainer.setVisibility(View.GONE);
                    binding.previewView.setVisibility(View.VISIBLE);
                    binding.scannerOverlay.setVisibility(View.VISIBLE);
                    startCamera();
                } else {
                    binding.permissionRequiredContainer.setVisibility(View.VISIBLE);
                    binding.previewView.setVisibility(View.GONE);
                    binding.scannerOverlay.setVisibility(View.GONE);

                    binding.grantPermissionButton.setOnClickListener(v -> {
                        try {
                            performHapticFeedback(v);
                            requestCameraPermission();
                        } catch (Exception e) {
                            Log.e(TAG, "Error requesting permission: " + e.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling permission result: " + e.getMessage());
        }
    }

    private void startCamera() {
        try {
            if (cameraInitialized) return;

            binding.loadingIndicator.setVisibility(View.VISIBLE);

            ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                    ProcessCameraProvider.getInstance(requireContext());

            cameraProviderFuture.addListener(() -> {
                try {
                    cameraProvider = cameraProviderFuture.get();

                    // Set up the preview
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                    // Select back camera
                    CameraSelector cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build();

                    // Set up image analysis
                    ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();

                    imageAnalysis.setAnalyzer(cameraExecutor, new BarcodeAnalyzer());

                    // Unbind any bound use cases before rebinding
                    cameraProvider.unbindAll();

                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview, imageAnalysis);

                    binding.loadingIndicator.setVisibility(View.GONE);
                    cameraInitialized = true;

                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Error starting camera: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error starting camera", Toast.LENGTH_SHORT).show();
                    binding.loadingIndicator.setVisibility(View.GONE);
                }
            }, ContextCompat.getMainExecutor(requireContext()));
        } catch (Exception e) {
            Log.e(TAG, "Error starting camera: " + e.getMessage());
            Toast.makeText(requireContext(), "Error starting camera", Toast.LENGTH_SHORT).show();
            binding.loadingIndicator.setVisibility(View.GONE);
        }
    }

    private class BarcodeAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            try {
                if (processingBarcode) {
                    imageProxy.close();
                    return;
                }

                // Convert ImageProxy to FirebaseVisionImage
                FirebaseVisionImage image = getVisionImageFromImageProxy(imageProxy);

                if (image != null) {
                    processingBarcode = true;

                    barcodeDetector.detectInImage(image)
                            .addOnSuccessListener(barcodes -> {
                                try {
                                    // Handle detected barcodes
                                    if (barcodes.size() > 0) {
                                        FirebaseVisionBarcode barcode = barcodes.get(0);
                                        String rawValue = barcode.getRawValue();

                                        if (rawValue != null && (scannedBarcode == null || !scannedBarcode.equals(rawValue))) {
                                            scannedBarcode = rawValue;
                                            handleBarcodeDetected(barcode);
                                        }
                                    }
                                    processingBarcode = false;
                                    imageProxy.close();
                                } catch (Exception e) {
                                    Log.e(TAG, "Error processing barcode: " + e.getMessage());
                                    processingBarcode = false;
                                    imageProxy.close();
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Barcode detection failed: " + e.getMessage());
                                processingBarcode = false;
                                imageProxy.close();
                            });
                } else {
                    imageProxy.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in analyze: " + e.getMessage());
                processingBarcode = false;
                imageProxy.close();
            }
        }

        private FirebaseVisionImage getVisionImageFromImageProxy(ImageProxy imageProxy) {
            try {
                // Get the YUV_420_888 image format
                @FirebaseVisionImageMetadata.ImageFormat int format =
                        FirebaseVisionImageMetadata.IMAGE_FORMAT_YV12;

                // Create metadata
                FirebaseVisionImageMetadata metadata = new FirebaseVisionImageMetadata.Builder()
                        .setWidth(imageProxy.getWidth())
                        .setHeight(imageProxy.getHeight())
                        .setFormat(format)
                        .setRotation(imageProxy.getImageInfo().getRotationDegrees() / 90)
                        .build();

                // Get image data
                if (imageProxy.getImage() == null) {
                    return null;
                }

                return FirebaseVisionImage.fromMediaImage(imageProxy.getImage(), metadata.getRotation());
            } catch (Exception e) {
                Log.e(TAG, "Error creating FirebaseVisionImage: " + e.getMessage());
                return null;
            }
        }
    }

    private void handleBarcodeDetected(FirebaseVisionBarcode barcode) {
        try {
            // Play success haptic feedback
            performHapticFeedback(binding.getRoot(), true);

            String rawValue = barcode.getRawValue();

            // Analytics
            if (analyticsTracker != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("barcode_format", getBarcodeFormatName(barcode.getFormat()));
                analyticsTracker.trackEvent("barcode_scanned", params);
            }

            // Process based on barcode type
            switch (barcode.getValueType()) {
                case FirebaseVisionBarcode.TYPE_PRODUCT:
                    // Handle product code (EAN, UPC, etc.)
                    handleProductBarcode(barcode);
                    break;

                case FirebaseVisionBarcode.TYPE_TEXT:
                    // Plain text QR code
                    handleTextBarcode(barcode);
                    break;

                case FirebaseVisionBarcode.TYPE_URL:
                    // URL QR code
                    FirebaseVisionBarcode.UrlBookmark urlBookmark = barcode.getUrl();
                    if (urlBookmark != null) {
                        handleUrlBarcode(urlBookmark.getUrl(), barcode);
                    } else {
                        handleTextBarcode(barcode);
                    }
                    break;

                case FirebaseVisionBarcode.TYPE_ISBN:
                    // ISBN book code
                    handleIsbnBarcode(barcode);
                    break;

                default:
                    // Try to interpret as JSON or plain text
                    handleTextBarcode(barcode);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling barcode: " + e.getMessage());
        }
    }

    private void handleProductBarcode(FirebaseVisionBarcode barcode) {
        try {
            String rawValue = barcode.getRawValue();

            // Show lookup UI
            requireActivity().runOnUiThread(() -> {
                try {
                    binding.barcodeResultContainer.setVisibility(View.VISIBLE);
                    binding.barcodeValueText.setText(rawValue);
                    binding.barcodeTypeText.setText(R.string.product_barcode);
                    binding.loadingProductInfo.setVisibility(View.VISIBLE);
                    binding.productInfoContainer.setVisibility(View.GONE);
                    binding.createAssetButton.setEnabled(false);
                } catch (Exception e) {
                    Log.e(TAG, "Error updating UI: " + e.getMessage());
                }
            });

            // Modified to work without Result wrapper
            viewModel.getProductInfo(rawValue).observe(getViewLifecycleOwner(), productInfo -> {
                try {
                    binding.loadingProductInfo.setVisibility(View.GONE);

                    if (productInfo != null) {
                        displayProductInfo(productInfo);
                    } else {
                        // Show error and option to enter manually
                        binding.productInfoContainer.setVisibility(View.VISIBLE);
                        binding.productNameText.setText(R.string.product_not_found);
                        binding.productDescriptionText.setText(R.string.scan_enter_manually);
                        binding.createAssetButton.setEnabled(true);

                        binding.createAssetButton.setOnClickListener(v -> {
                            try {
                                performHapticFeedback(v);

                                // Navigate to AddEditAssetFragment with barcode
                                Bundle args = new Bundle();
                                args.putString("barcode", rawValue);
                                Navigation.findNavController(requireView())
                                        .navigate(R.id.action_qrScannerFragment_to_addEditAssetFragment, args);
                            } catch (Exception e) {
                                Log.e(TAG, "Error navigating to add asset: " + e.getMessage());
                                Toast.makeText(requireContext(), "Error creating asset", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling product info: " + e.getMessage());
                    binding.loadingProductInfo.setVisibility(View.GONE);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error handling product barcode: " + e.getMessage());
        }
    }

    private void displayProductInfo(ProductInfo productInfo) {
        try {
            binding.productInfoContainer.setVisibility(View.VISIBLE);
            binding.productNameText.setText(productInfo.getName() != null ? productInfo.getName() : "");
            binding.productDescriptionText.setText(productInfo.getDescription() != null ? productInfo.getDescription() : "");
            binding.productBrandText.setText(productInfo.getBrand() != null ? productInfo.getBrand() : "");
            binding.createAssetButton.setEnabled(true);

            binding.createAssetButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);

                    // Create an asset with product info and navigate to edit it
                    Asset asset = new Asset();
                    asset.setName(productInfo.getName());
                    asset.setDescription(productInfo.getDescription());
                    asset.setBrand(productInfo.getBrand());
                    asset.setBarcode(productInfo.getBarcode());
                    asset.setModel(productInfo.getModelNumber());

                    // Set a default value if available
                    if (productInfo.getPrice() > 0) {
                        asset.setCost(productInfo.getPrice());
                        asset.setCurrentValue(productInfo.getPrice());
                        asset.setCurrency("USD"); // Default
                    }

                    // Navigate to AddEditAssetFragment with pre-filled asset
                    Bundle args = new Bundle();
                    args.putSerializable("asset", asset);
                    Navigation.findNavController(requireView())
                            .navigate(R.id.action_qrScannerFragment_to_addEditAssetFragment, args);
                } catch (Exception e) {
                    Log.e(TAG, "Error creating asset from product info: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error creating asset", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error displaying product info: " + e.getMessage());
        }
    }

    private void handleTextBarcode(FirebaseVisionBarcode barcode) {
        try {
            String rawValue = barcode.getRawValue();

            // Check if it's a JSON format that we recognize
            if (rawValue != null && rawValue.startsWith("{") && rawValue.endsWith("}")) {
                try {
                    JSONObject json = new JSONObject(rawValue);

                    // Check if it's our app's asset format
                    if (json.has("assetType") && json.getString("assetType").equals("HyperPlux")) {
                        handleHyperPluxAssetQr(json);
                        return;
                    }
                } catch (JSONException e) {
                    // Not valid JSON, treat as text
                    Log.e(TAG, "Not valid JSON: " + e.getMessage());
                }
            }

            // Show as plain text
            requireActivity().runOnUiThread(() -> {
                try {
                    binding.barcodeResultContainer.setVisibility(View.VISIBLE);
                    binding.barcodeValueText.setText(rawValue);
                    binding.barcodeTypeText.setText(R.string.text_qr_code);
                    binding.loadingProductInfo.setVisibility(View.GONE);
                    binding.productInfoContainer.setVisibility(View.GONE);
                    binding.createAssetButton.setEnabled(true);

                    binding.createAssetButton.setOnClickListener(v -> {
                        try {
                            performHapticFeedback(v);

                            // Create a new asset with description set to the QR text
                            Asset asset = new Asset();
                            asset.setDescription(rawValue);

                            // Navigate to AddEditAssetFragment
                            Bundle args = new Bundle();
                            args.putSerializable("asset", asset);
                            Navigation.findNavController(requireView())
                                    .navigate(R.id.action_qrScannerFragment_to_addEditAssetFragment, args);
                        } catch (Exception e) {
                            Log.e(TAG, "Error creating asset from text: " + e.getMessage());
                            Toast.makeText(requireContext(), "Error creating asset", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error updating UI for text barcode: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error handling text barcode: " + e.getMessage());
        }
    }

    private void handleUrlBarcode(String url, FirebaseVisionBarcode barcode) {
        try {
            requireActivity().runOnUiThread(() -> {
                try {
                    binding.barcodeResultContainer.setVisibility(View.VISIBLE);
                    binding.barcodeValueText.setText(url);
                    binding.barcodeTypeText.setText(R.string.url_qr_code);
                    binding.loadingProductInfo.setVisibility(View.GONE);
                    binding.productInfoContainer.setVisibility(View.GONE);
                    binding.createAssetButton.setEnabled(true);

                    binding.createAssetButton.setOnClickListener(v -> {
                        try {
                            performHapticFeedback(v);

                            // Create a new asset with URL as additional info
                            Asset asset = new Asset();
                            asset.setDescription(getString(R.string.scanned_url) + ": " + url);

                            // Navigate to AddEditAssetFragment
                            Bundle args = new Bundle();
                            args.putSerializable("asset", asset);
                            Navigation.findNavController(requireView())
                                    .navigate(R.id.action_qrScannerFragment_to_addEditAssetFragment, args);
                        } catch (Exception e) {
                            Log.e(TAG, "Error creating asset from URL: " + e.getMessage());
                            Toast.makeText(requireContext(), "Error creating asset", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error updating UI for URL barcode: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error handling URL barcode: " + e.getMessage());
        }
    }

    private void handleIsbnBarcode(FirebaseVisionBarcode barcode) {
        try {
            String isbn = barcode.getRawValue();

            // Show lookup UI
            requireActivity().runOnUiThread(() -> {
                try {
                    binding.barcodeResultContainer.setVisibility(View.VISIBLE);
                    binding.barcodeValueText.setText(isbn);
                    binding.barcodeTypeText.setText(R.string.isbn_barcode);
                    binding.loadingProductInfo.setVisibility(View.VISIBLE);
                    binding.productInfoContainer.setVisibility(View.GONE);
                    binding.createAssetButton.setEnabled(false);
                } catch (Exception e) {
                    Log.e(TAG, "Error updating UI for ISBN barcode: " + e.getMessage());
                }
            });

            // Modified to work without Result wrapper
            viewModel.getBookInfo(isbn).observe(getViewLifecycleOwner(), bookInfo -> {
                try {
                    binding.loadingProductInfo.setVisibility(View.GONE);

                    if (bookInfo != null) {
                        displayProductInfo(bookInfo);
                    } else {
                        // Show error and option to enter manually
                        binding.productInfoContainer.setVisibility(View.VISIBLE);
                        binding.productNameText.setText(R.string.book_not_found);
                        binding.productDescriptionText.setText(R.string.scan_enter_manually);
                        binding.createAssetButton.setEnabled(true);

                        binding.createAssetButton.setOnClickListener(v -> {
                            try {
                                performHapticFeedback(v);

                                // Navigate to AddEditAssetFragment with ISBN
                                Bundle args = new Bundle();
                                args.putString("isbn", isbn);
                                Navigation.findNavController(requireView())
                                        .navigate(R.id.action_qrScannerFragment_to_addEditAssetFragment, args);
                            } catch (Exception e) {
                                Log.e(TAG, "Error creating asset from ISBN: " + e.getMessage());
                                Toast.makeText(requireContext(), "Error creating asset", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling book info: " + e.getMessage());
                    binding.loadingProductInfo.setVisibility(View.GONE);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error handling ISBN barcode: " + e.getMessage());
        }
    }

    private void handleHyperPluxAssetQr(JSONObject json) {
        try {
            String assetId = json.getString("assetId");

            // Modified to work without Result wrapper
            viewModel.getAssetById(Integer.parseInt(assetId)).observe(getViewLifecycleOwner(), asset -> {
                try {
                    if (asset != null) {
                        // Navigate to asset detail
                        Bundle args = new Bundle();
                        args.putInt("assetId", asset.getId());
                        Navigation.findNavController(requireView())
                                .navigate(R.id.action_qrScannerFragment_to_assetDetailFragment, args);
                    } else {
                        // Asset not found or other error
                        Toast.makeText(requireContext(), R.string.asset_not_found, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to asset detail: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error viewing asset", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error handling HyperPlux QR: " + e.getMessage());
            Toast.makeText(requireContext(), R.string.invalid_asset_qr, Toast.LENGTH_SHORT).show();
        }
    }

    private String getBarcodeFormatName(int format) {
        switch (format) {
            case FirebaseVisionBarcode.FORMAT_QR_CODE:
                return "QR_CODE";
            case FirebaseVisionBarcode.FORMAT_EAN_13:
                return "EAN_13";
            case FirebaseVisionBarcode.FORMAT_EAN_8:
                return "EAN_8";
            case FirebaseVisionBarcode.FORMAT_UPC_A:
                return "UPC_A";
            case FirebaseVisionBarcode.FORMAT_UPC_E:
                return "UPC_E";
            case FirebaseVisionBarcode.FORMAT_CODE_128:
                return "CODE_128";
            default:
                return "OTHER";
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

    @Override
    public void onResume() {
        super.onResume();
        try {
            if (hasCameraPermission() && !cameraInitialized) {
                startCamera();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume: " + e.getMessage());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
                cameraInitialized = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            if (cameraExecutor != null) {
                cameraExecutor.shutdown();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down camera executor: " + e.getMessage());
        }
        binding = null;
    }
}