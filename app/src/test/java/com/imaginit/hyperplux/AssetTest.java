package com.imaginit.hyperplux;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.*;

import com.imaginit.hyperplux.models.Asset;

/**
 * Unit tests for the Asset model
 */
public class AssetTest {
    private Asset asset;
    private final String TEST_USER_ID = "testUser123";
    private final String TEST_ASSET_NAME = "Test Asset";

    @Before
    public void setUp() {
        // Create a test asset with minimum required fields
        asset = new Asset(TEST_ASSET_NAME, 1, TEST_USER_ID);
    }

    @Test
    public void testAssetCreation() {
        // Verify asset is created with correct values
        assertEquals(TEST_ASSET_NAME, asset.getName());
        assertEquals(1, asset.getQuantity());
        assertEquals(TEST_USER_ID, asset.getUserId());
        assertEquals(0, asset.getViews());
        assertEquals(0, asset.getLikes());
        assertEquals(0, asset.getDislikes());
        assertFalse(asset.isForSale());
        assertFalse(asset.isHidden());
        assertFalse(asset.isLoanedOut());
        assertFalse(asset.isBequest());
        assertNotNull(asset.getPurchaseDate());
    }

    @Test
    public void testEngagementScoreCalculation() {
        // Initial score should be 0
        assertEquals(0.0, asset.getEngagementScore(), 0.001);

        // Set views, likes, dislikes, and recalculate
        asset.setViews(100);
        asset.setLikes(50);
        asset.setDislikes(10);
        asset.setShares(5);
        asset.setComments(20);

        // Check score is calculated based on engagement formula
        // views * 0.2 + likes * 1.0 + dislikes * -0.5 + shares * 2.0 + comments * 1.5
        // With time decay factor (recent date so decay is minimal)
        double expectedScore = (100 * 0.2) + (50 * 1.0) + (10 * -0.5) + (5 * 2.0) + (20 * 1.5);
        double actualScore = asset.getEngagementScore();

        // Allow a small tolerance due to time decay factor
        assertTrue(Math.abs(expectedScore - actualScore) < expectedScore * 0.1);
    }

    @Test
    public void testTimeDecay() {
        // Set up asset with old engagement date
        asset.setViews(100);
        asset.setLikes(50);

        // Record the current score
        double currentScore = asset.getEngagementScore();

        // Set last interaction date to 30 days ago
        Date oldDate = new Date(System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000));
        asset.setLastInteractionDate(oldDate);
        asset.recalculateEngagementScore();

        // Score should be significantly lower due to time decay
        double oldScore = asset.getEngagementScore();
        assertTrue(oldScore < currentScore * 0.5);
    }

    @Test
    public void testEquals() {
        // Two assets with same ID should be equal
        Asset asset1 = new Asset("Asset 1", 1, TEST_USER_ID);
        asset1.setId(123);

        Asset asset2 = new Asset("Different Name", 2, "differentUser");
        asset2.setId(123);

        assertEquals(asset1, asset2);

        // Different IDs should not be equal
        Asset asset3 = new Asset("Asset 3", 1, TEST_USER_ID);
        asset3.setId(456);

        assertNotEquals(asset1, asset3);
    }

    @Test
    public void testParcelable() {
        // Create a complete asset with all fields
        Asset originalAsset = new Asset("Parcel Test", 5, "Test Description", "Electronics",
                "Apple", "MacBook Pro", "SN12345", 1299.99,
                "USD", 999.99, new Date(),
                "Amazon", "receipt.jpg", "Home Office",
                37.7749, -122.4194, true, "Excellent",
                true, 899.99, false, false,
                null, null, null, "image.jpg",
                null, "doc.pdf", TEST_USER_ID,
                10, 5, 1, 2, 3,
                new Date(), 20.0, new Date(),
                "1 year warranty", new Date(), new Date(),
                "heir123", "Give to daughter", true);
        originalAsset.setId(789);

        // Create a parcel and write to it
        android.os.Parcel parcel = android.os.Parcel.obtain();
        originalAsset.writeToParcel(parcel, 0);

        // Reset parcel for reading
        parcel.setDataPosition(0);

        // Create from parcel
        Asset fromParcel = Asset.CREATOR.createFromParcel(parcel);

        // Verify all fields match
        assertEquals(originalAsset.getId(), fromParcel.getId());
        assertEquals(originalAsset.getName(), fromParcel.getName());
        assertEquals(originalAsset.getQuantity(), fromParcel.getQuantity());
        assertEquals(originalAsset.getDescription(), fromParcel.getDescription());
        assertEquals(originalAsset.getCategory(), fromParcel.getCategory());
        assertEquals(originalAsset.getBrand(), fromParcel.getBrand());
        assertEquals(originalAsset.getModel(), fromParcel.getModel());
        assertEquals(originalAsset.getSerialNumber(), fromParcel.getSerialNumber());
        assertEquals(originalAsset.getCost(), fromParcel.getCost(), 0.001);
        assertEquals(originalAsset.getCurrency(), fromParcel.getCurrency());
        assertEquals(originalAsset.getCurrentValue(), fromParcel.getCurrentValue(), 0.001);
        assertEquals(originalAsset.getPurchaseDate(), fromParcel.getPurchaseDate());
        assertEquals(originalAsset.getPurchaseLocation(), fromParcel.getPurchaseLocation());
        assertEquals(originalAsset.getReceiptImageUri(), fromParcel.getReceiptImageUri());
        assertEquals(originalAsset.getCurrentLocation(), fromParcel.getCurrentLocation());
        assertEquals(originalAsset.getLatitude(), fromParcel.getLatitude(), 0.0001);
        assertEquals(originalAsset.getLongitude(), fromParcel.getLongitude(), 0.0001);
        assertEquals(originalAsset.isShared(), fromParcel.isShared());
        assertEquals(originalAsset.getCondition(), fromParcel.getCondition());
        assertEquals(originalAsset.isForSale(), fromParcel.isForSale());
        assertEquals(originalAsset.getAskingPrice(), fromParcel.getAskingPrice(), 0.001);
        assertEquals(originalAsset.isHidden(), fromParcel.isHidden());
        assertEquals(originalAsset.isLoanedOut(), fromParcel.isLoanedOut());
        assertEquals(originalAsset.getImageUri(), fromParcel.getImageUri());
        assertEquals(originalAsset.getDocumentUri(), fromParcel.getDocumentUri());
        assertEquals(originalAsset.getUserId(), fromParcel.getUserId());
        assertEquals(originalAsset.getViews(), fromParcel.getViews());
        assertEquals(originalAsset.getLikes(), fromParcel.getLikes());
        assertEquals(originalAsset.getDislikes(), fromParcel.getDislikes());
        assertEquals(originalAsset.getShares(), fromParcel.getShares());
        assertEquals(originalAsset.getComments(), fromParcel.getComments());
        assertEquals(originalAsset.getWarrantyInfo(), fromParcel.getWarrantyInfo());
        assertEquals(originalAsset.getHeirId(), fromParcel.getHeirId());
        assertEquals(originalAsset.getWillInstructions(), fromParcel.getWillInstructions());
        assertEquals(originalAsset.isBequest(), fromParcel.isBequest());
    }
}