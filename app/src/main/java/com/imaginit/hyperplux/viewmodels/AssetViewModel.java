package com.imaginit.hyperplux.viewmodels;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.EngagementAlgorithm;
import com.imaginit.hyperplux.utils.ImageCompressor;
import com.imaginit.hyperplux.utils.NetworkMonitor;
import com.imaginit.hyperplux.utils.Validator;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.models.AssetTransaction;
import com.imaginit.hyperplux.models.User;
import com.imaginit.hyperplux.repositories.AssetRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AssetViewModel extends AndroidViewModel {
    private static final String TAG = "AssetViewModel";

    private AssetRepository repository;
    private FirebaseAuth auth;
    private FirebaseStorage storage;

    // LiveData
    private LiveData<List<Asset>> assets;
    private LiveData<List<Asset>> hiddenAssets;
    private LiveData<List<Asset>> assetsForSale;
    private LiveData<List<Asset>> marketplaceAssets;
    private LiveData<List<Asset>> topAssets;
    private MutableLiveData<List<Asset>> searchResults = new MutableLiveData<>();
    private MediatorLiveData<List<Asset>> personalizedFeed = new MediatorLiveData<>();
    private MutableLiveData<Asset> selectedAsset = new MutableLiveData<>();
    private MutableLiveData<List<Asset>> recentlyViewedAssets = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private LiveData<List<AssetTransaction>> pendingTransactions;

    // Keep track of observers to prevent memory leaks
    private Map<String, Object> observers = new HashMap<>();

    // Constructor
    public AssetViewModel(@NonNull Application application, AssetRepository repository) {
        super(application);
        this.repository = repository;
        this.auth = FirebaseAuth.getInstance();
        this.storage = FirebaseStorage.getInstance();

        // Load user assets
        loadAssets();

        // Set up personalized feed
        setupPersonalizedFeed();
    }

    // Load user assets
    public void loadAssets() {
        assets = repository.getAssetsByUser();
        hiddenAssets = repository.getHiddenAssetsByUser();
        assetsForSale = repository.getAssetsForSale();
        topAssets = repository.getTopAssets();
        marketplaceAssets = repository.getAllAssetsForSale();
        pendingTransactions = repository.getPendingIncomingTransactions();
    }

    // Load top assets when personalized feed is not available
    public void loadTopAssets() {
        topAssets = repository.getTopAssets();
        personalizedFeed.addSource(topAssets, assets -> {
            if (assets != null) {
                personalizedFeed.setValue(assets);
            }
            isLoading.setValue(false);
        });
    }

    // Set up personalized feed by combining top assets and following feed
    private void setupPersonalizedFeed() {
        // First source: top assets
        personalizedFeed.addSource(topAssets, assets -> {
            if (assets != null) {
                // Apply engagement algorithm when user is available
                FirebaseUser currentUser = auth.getCurrentUser();
                if (currentUser != null) {
                    // This would use the actual user object in production
                    User user = new User(currentUser.getUid(), currentUser.getEmail());
                    List<Asset> personalized = EngagementAlgorithm.generatePersonalizedFeed(
                            assets, user);
                    personalizedFeed.setValue(personalized);
                } else {
                    personalizedFeed.setValue(assets);
                }
            }
        });

        // Additional sources can be added here and combined
    }

    // Get user's assets
    public LiveData<List<Asset>> getAssets() {
        return assets;
    }

    // Get hidden assets
    public LiveData<List<Asset>> getHiddenAssets() {
        return hiddenAssets;
    }

    // Get assets for sale
    public LiveData<List<Asset>> getAssetsForSale() {
        return assetsForSale;
    }

    // Get marketplace assets
    public LiveData<List<Asset>> getMarketplaceAssets() {
        return marketplaceAssets;
    }

    // Get personalized feed
    public LiveData<List<Asset>> getPersonalizedFeed() {
        return personalizedFeed;
    }

    // Get pending transactions
    public LiveData<List<AssetTransaction>> getPendingTransactions() {
        return pendingTransactions;
    }

    // Add new asset
    public void addAsset(Asset asset) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            errorMessage.setValue("User not logged in");
            return;
        }

        repository.addAsset(asset);
    }

    // Update existing asset
    public void updateAsset(Asset asset) {
        repository.updateAsset(asset);
    }

    // Delete asset
    public void deleteAsset(Asset asset) {
        repository.deleteAsset(asset);
    }

    // Search assets
    public void searchAssets(String query) {
        isLoading.setValue(true);

        // Remove previous observer to prevent memory leaks
        if (observers.containsKey("searchObserver")) {
            LiveData<List<Asset>> previousResults = (LiveData<List<Asset>>) observers.get("searchObserver");
            if (previousResults != null) {
                previousResults.removeObservers(getCurrentLifecycleOwner());
            }
        }

        LiveData<List<Asset>> results = repository.searchAssets(query);
        observers.put("searchObserver", results);

        results.observe(getCurrentLifecycleOwner(), assets -> {
            searchResults.setValue(assets);
            isLoading.setValue(false);
        });
    }

    // Get search results
    public LiveData<List<Asset>> getSearchResults() {
        return searchResults;
    }

    // Like an asset
    public void likeAsset(Asset asset) {
        if (asset != null) {
            repository.incrementLikes(asset);
        }
    }

    // Dislike an asset
    public void dislikeAsset(Asset asset) {
        if (asset != null) {
            repository.incrementDislikes(asset);
        }
    }

    // View an asset (track view and add to recently viewed)
    public void viewAsset(Asset asset) {
        if (asset != null) {
            // Increment views
            repository.incrementViews(asset);

            // Add to recently viewed
            List<Asset> recent = recentlyViewedAssets.getValue();
            if (recent != null) {
                // Add to front of list
                if (recent.contains(asset)) {
                    recent.remove(asset);
                }
                recent.add(0, asset);

                // Limit list size
                if (recent.size() > 10) {
                    recent = recent.subList(0, 10);
                }

                recentlyViewedAssets.setValue(recent);
            }

            selectedAsset.setValue(asset);
        }
    }

    // Share an asset
    public void shareAsset(Asset asset) {
        if (asset != null) {
            repository.incrementShares(asset);
        }
    }

    // Comment on an asset
    public void commentOnAsset(Asset asset) {
        if (asset != null) {
            repository.incrementComments(asset);
        }
    }

    // Upload asset image to storage
    public void uploadAssetImage(Uri imageUri, AssetRepository.Callback<String> callback) {
        if (imageUri == null) {
            callback.onResult(null);
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            errorMessage.setValue("User not logged in");
            callback.onResult(null);
            return;
        }

        // Create a unique filename
        String filename = "assets/" + user.getUid() + "/" + UUID.randomUUID().toString() + ".jpg";
        StorageReference ref = storage.getReference().child(filename);

        isLoading.setValue(true);
        ref.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    ref.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                isLoading.setValue(false);
                                callback.onResult(uri.toString());
                            })
                            .addOnFailureListener(e -> {
                                isLoading.setValue(false);
                                errorMessage.setValue("Failed to get download URL: " + e.getMessage());
                                callback.onResult(null);
                            });
                })
                .addOnFailureListener(e -> {
                    isLoading.setValue(false);
                    errorMessage.setValue("Upload failed: " + e.getMessage());
                    callback.onResult(null);
                });
    }

    // Initiate asset sale
    public void initiateAssetSale(Asset asset, String buyerId, double amount, String currency) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            errorMessage.setValue("User not logged in");
            return;
        }

        if (asset == null || !asset.getUserId().equals(user.getUid())) {
            errorMessage.setValue("Cannot sell an asset you don't own");
            return;
        }

        // Create a transaction
        repository.createTransaction(asset.getId(), user.getUid(), buyerId, "SALE", amount, currency);
    }

    // Initiate asset loan
    public void initiateAssetLoan(Asset asset, String borrowerId, Date returnDate) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            errorMessage.setValue("User not logged in");
            return;
        }

        if (asset == null || !asset.getUserId().equals(user.getUid())) {
            errorMessage.setValue("Cannot loan an asset you don't own");
            return;
        }

        // Create a transaction
        repository.createTransaction(asset.getId(), user.getUid(), borrowerId, "LOAN", 0, "");
    }

    // Accept a pending transaction
    public void acceptTransaction(AssetTransaction transaction) {
        if (transaction == null) {
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !transaction.getToUserId().equals(user.getUid())) {
            errorMessage.setValue("Not authorized to accept this transaction");
            return;
        }

        repository.completeTransaction(transaction, success -> {
            if (!success) {
                errorMessage.setValue("Failed to complete transaction");
            }
        });
    }

    // Get selected asset
    public LiveData<Asset> getSelectedAsset() {
        return selectedAsset;
    }

    // Set selected asset
    public void setSelectedAsset(Asset asset) {
        selectedAsset.setValue(asset);
    }

    // Get recently viewed assets
    public LiveData<List<Asset>> getRecentlyViewedAssets() {
        return recentlyViewedAssets;
    }

    // Get loading state
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    // Get error messages
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Get a specific asset by ID
     * @param assetId Asset ID to fetch
     * @return LiveData containing the asset
     */
    public LiveData<Asset> getAssetById(int assetId) {
        // Create a MediatorLiveData to transform the result
        MediatorLiveData<Asset> result = new MediatorLiveData<>();

        // Show loading state
        isLoading.setValue(true);

        // Create temporary backing field
        LiveData<Asset> assetLiveData = repository.getAssetById(assetId);

        // Add assetLiveData as a source
        result.addSource(assetLiveData, asset -> {
            if (asset != null) {
                // Asset found
                result.setValue(asset);
                isLoading.setValue(false);
            } else {
                // Asset not found
                errorMessage.setValue("Asset not found");
                isLoading.setValue(false);
            }
        });

        return result;
    }

    /**
     * Upload asset image with compression and error handling
     * @param imageUri Original image Uri
     * @param callback Callback with image URL or null if error
     */
    public void uploadAssetImageWithCompression(Uri imageUri, AssetRepository.Callback<String> callback) {
        if (imageUri == null) {
            errorMessage.setValue("No image selected");
            callback.onResult(null);
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            errorMessage.setValue("User not logged in");
            callback.onResult(null);
            return;
        }

        isLoading.setValue(true);

        // Compress image before upload
        ImageCompressor.compressImage(getApplication(), imageUri, new ImageCompressor.CompressionCallback() {
            @Override
            public void onCompressed(Uri compressedImageUri) {
                // Upload compressed image
                StorageReference ref = storage.getReference().child(
                        "assets/" + user.getUid() + "/" + UUID.randomUUID().toString() + ".jpg");

                UploadTask uploadTask = ref.putFile(compressedImageUri);

                uploadTask.addOnSuccessListener(taskSnapshot -> {
                            ref.getDownloadUrl()
                                    .addOnSuccessListener(uri -> {
                                        isLoading.setValue(false);
                                        callback.onResult(uri.toString());

                                        // Track analytics
                                        AnalyticsTracker.getInstance(getApplication())
                                                .trackEvent("image_upload_success", null);
                                    })
                                    .addOnFailureListener(e -> {
                                        isLoading.setValue(false);
                                        FirebaseErrorHandler.handleException(e, errorMessage);
                                        callback.onResult(null);
                                    });
                        })
                        .addOnFailureListener(e -> {
                            isLoading.setValue(false);
                            FirebaseErrorHandler.handleException(e, errorMessage);
                            callback.onResult(null);
                        });
            }

            @Override
            public void onError(Exception e) {
                isLoading.setValue(false);
                errorMessage.setValue("Image compression failed: " + e.getMessage());
                callback.onResult(null);
            }
        });
    }

    /**
     * Add asset with validation
     * @param asset Asset to add
     * @return boolean indicating success
     */
    public boolean addAssetWithValidation(Asset asset) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            errorMessage.setValue("User not logged in");
            return false;
        }

        // Validate required fields
        Validator.ValidationResult nameValidation = Validator.validateAssetName(asset.getName());
        if (!nameValidation.isSuccess()) {
            errorMessage.setValue(nameValidation.getErrorMessage());
            return false;
        }

        // Validate quantity
        if (asset.getQuantity() <= 0) {
            errorMessage.setValue("Quantity must be greater than 0");
            return false;
        }

        // Validate price if for sale
        if (asset.isForSale()) {
            Validator.ValidationResult priceValidation = Validator.validatePrice(String.valueOf(asset.getAskingPrice()));
            if (!priceValidation.isSuccess()) {
                errorMessage.setValue(priceValidation.getErrorMessage());
                return false;
            }
        }

        // Ensure user ID is set
        asset.setUserId(user.getUid());

        // Add asset
        repository.addAsset(asset);

        // Track analytics
        AnalyticsTracker tracker = AnalyticsTracker.getInstance(getApplication());
        tracker.trackAssetCreate(asset);

        return true;
    }

    /**
     * Update asset with validation
     * @param asset Asset to update
     * @return boolean indicating success
     */
    public boolean updateAssetWithValidation(Asset asset) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            errorMessage.setValue("User not logged in");
            return false;
        }

        // Validate required fields
        Validator.ValidationResult nameValidation = Validator.validateAssetName(asset.getName());
        if (!nameValidation.isSuccess()) {
            errorMessage.setValue(nameValidation.getErrorMessage());
            return false;
        }

        // Validate quantity
        if (asset.getQuantity() <= 0) {
            errorMessage.setValue("Quantity must be greater than 0");
            return false;
        }

        // Validate price if for sale
        if (asset.isForSale() && asset.getAskingPrice() <= 0) {
            errorMessage.setValue("Asking price must be greater than 0");
            return false;
        }

        // Verify ownership
        if (!asset.getUserId().equals(user.getUid())) {
            errorMessage.setValue("You don't have permission to update this asset");
            return false;
        }

        // Update asset
        repository.updateAsset(asset);

        // Track analytics
        AnalyticsTracker tracker = AnalyticsTracker.getInstance(getApplication());
        tracker.trackAssetEdit(asset);

        return true;
    }

    /**
     * Delete asset with ownership verification
     * @param asset Asset to delete
     * @return boolean indicating success
     */
    public boolean deleteAssetWithVerification(Asset asset) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            errorMessage.setValue("User not logged in");
            return false;
        }

        // Verify ownership
        if (!asset.getUserId().equals(user.getUid())) {
            errorMessage.setValue("You don't have permission to delete this asset");
            return false;
        }

        // Delete asset
        repository.deleteAsset(asset);

        // Track analytics
        AnalyticsTracker tracker = AnalyticsTracker.getInstance(getApplication());
        tracker.trackAssetDelete(asset);

        return true;
    }

    /**
     * Like asset with error handling
     * @param asset Asset to like
     */
    public void likeAssetWithErrorHandling(Asset asset) {
        if (asset == null) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            errorMessage.setValue("You must be logged in to like assets");
            return;
        }

        // Check if user is trying to like their own asset
        if (asset.getUserId().equals(user.getUid())) {
            // Don't show error, just silently ignore
            return;
        }

        // Increment likes
        repository.incrementLikes(asset);

        // Track analytics
        AnalyticsTracker tracker = AnalyticsTracker.getInstance(getApplication());
        tracker.trackAssetLike(asset);
    }

    /**
     * Dislike asset with error handling
     * @param asset Asset to dislike
     */
    public void dislikeAssetWithErrorHandling(Asset asset) {
        if (asset == null) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            errorMessage.setValue("You must be logged in to dislike assets");
            return;
        }

        // Check if user is trying to dislike their own asset
        if (asset.getUserId().equals(user.getUid())) {
            // Don't show error, just silently ignore
            return;
        }

        // Increment dislikes
        repository.incrementDislikes(asset);

        // Track analytics
        AnalyticsTracker tracker = AnalyticsTracker.getInstance(getApplication());
        tracker.trackAssetDislike(asset);
    }

    /**
     * Share asset with tracking
     * @param asset Asset to share
     */
    public void shareAssetWithTracking(Asset asset) {
        if (asset == null) {
            return;
        }

        // Increment shares
        repository.incrementShares(asset);

        // Track analytics
        AnalyticsTracker tracker = AnalyticsTracker.getInstance(getApplication());
        tracker.trackAssetShare(asset);
    }

    /**
     * Load marketplace assets with network error handling
     */
    public void loadMarketplaceAssets() {
        isLoading.setValue(true);

        // Check network connectivity first
        NetworkMonitor networkMonitor = NetworkMonitor.getInstance(getApplication());
        if (!networkMonitor.isNetworkCurrentlyAvailable()) {
            errorMessage.setValue("No internet connection. Showing cached data.");
        }

        LiveData<List<Asset>> marketplaceAssetsLiveData = repository.getAllAssetsForSale();

        // Use mediator live data to transform the result
        MediatorLiveData<List<Asset>> result = new MediatorLiveData<>();
        result.addSource(marketplaceAssetsLiveData, assets -> {
            isLoading.setValue(false);
            if (assets != null) {
                marketplaceAssets = marketplaceAssetsLiveData;
                result.setValue(assets);
            } else {
                errorMessage.setValue("Failed to load marketplace assets");
            }
        });
    }

    /**
     * Load personalized feed with fallback to top assets
     */
    public void loadPersonalizedFeed() {
        isLoading.setValue(true);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // Fallback to top assets for non-authenticated users
            loadTopAssets();
            return;
        }

        // Load top assets as fallback and handle in a proper lifecycle-aware way
        loadTopAssets();
    }

    /**
     * Create transaction with validation
     * @param assetId Asset ID
     * @param toUserId Recipient user ID
     * @param transactionType Transaction type
     * @param amount Transaction amount
     * @param currency Currency
     * @return boolean indicating success
     */
    public boolean createTransactionWithValidation(int assetId, String toUserId,
                                                   String transactionType, double amount, String currency) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            errorMessage.setValue("User not logged in");
            return false;
        }

        // Basic validation
        if (toUserId == null || toUserId.isEmpty()) {
            errorMessage.setValue("Invalid recipient");
            return false;
        }

        if (transactionType == null || transactionType.isEmpty()) {
            errorMessage.setValue("Invalid transaction type");
            return false;
        }

        // For sales, validate amount and currency
        if (transactionType.equals("SALE")) {
            if (amount <= 0) {
                errorMessage.setValue("Sale amount must be greater than 0");
                return false;
            }

            if (currency == null || currency.isEmpty()) {
                errorMessage.setValue("Currency is required for sales");
                return false;
            }
        }

        // Create transaction
        try {
            repository.createTransaction(assetId, user.getUid(), toUserId, transactionType, amount, currency);

            // Track analytics
            AssetTransaction transaction = new AssetTransaction(assetId, user.getUid(), toUserId, transactionType, amount, currency);
            AnalyticsTracker tracker = AnalyticsTracker.getInstance(getApplication());
            tracker.trackTransactionCreate(transaction);

            return true;
        } catch (Exception e) {
            errorMessage.setValue("Error creating transaction: " + e.getMessage());
            return false;
        }
    }

    /**
     * Accept transaction with validation
     * @param transaction Transaction to accept
     * @return boolean indicating success
     */
    public boolean acceptTransactionWithValidation(AssetTransaction transaction) {
        if (transaction == null) {
            errorMessage.setValue("Invalid transaction");
            return false;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            errorMessage.setValue("User not logged in");
            return false;
        }

        // Verify recipient
        if (!transaction.getToUserId().equals(user.getUid())) {
            errorMessage.setValue("You don't have permission to accept this transaction");
            return false;
        }

        // Check transaction status
        if (!transaction.getStatus().equals("PENDING")) {
            errorMessage.setValue("This transaction is no longer pending");
            return false;
        }

        // Complete transaction
        try {
            repository.completeTransaction(transaction, success -> {
                if (success) {
                    // Track analytics
                    AnalyticsTracker tracker = AnalyticsTracker.getInstance(getApplication());
                    tracker.trackTransactionComplete(transaction);
                } else {
                    errorMessage.setValue("Failed to complete transaction");
                }
            });
            return true;
        } catch (Exception e) {
            errorMessage.setValue("Error accepting transaction: " + e.getMessage());
            return false;
        }
    }

    /**
     * Helper method to get the current lifecycle owner
     */
    private androidx.lifecycle.LifecycleOwner getCurrentLifecycleOwner() {
        // In a real app, you would inject this or get it from a ProcessLifecycleOwner
        // For this example, just return null and we'll handle it in the observers
        return null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        // Clean up observers to prevent memory leaks
        for (Object observer : observers.values()) {
            if (observer instanceof LiveData) {
                ((LiveData<?>) observer).removeObservers(getCurrentLifecycleOwner());
            }
        }
        observers.clear();
    }
}