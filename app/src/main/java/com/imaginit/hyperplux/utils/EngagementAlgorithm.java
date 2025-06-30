package com.imaginit.hyperplux.utils;

import com.imaginit.hyperplux.models.Asset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Algorithm for generating personalized content feeds and recommendations
 * based on user engagement and interests.
 */
public class EngagementAlgorithm {

    // Weights for different engagement types
    private static final double VIEW_WEIGHT = 0.2;
    private static final double LIKE_WEIGHT = 1.0;
    private static final double DISLIKE_WEIGHT = -0.5;
    private static final double SHARE_WEIGHT = 2.0;
    private static final double COMMENT_WEIGHT = 1.5;

    // Time decay parameters
    private static final double TIME_DECAY_FACTOR = 0.05; // Higher value = faster decay
    private static final long MILLISECONDS_IN_DAY = 1000 * 60 * 60 * 24;

    // Interest match parameters
    private static final double INTEREST_MATCH_BOOST = 1.5;

    /**
     * Calculate engagement score for a single asset
     */
    public static double calculateAssetScore(Asset asset) {
        if (asset == null) return 0;

        // Base engagement score
        double rawScore = (asset.getViews() * VIEW_WEIGHT) +
                (asset.getLikes() * LIKE_WEIGHT) +
                (asset.getDislikes() * DISLIKE_WEIGHT) +
                (asset.getShares() * SHARE_WEIGHT) +
                (asset.getComments() * COMMENT_WEIGHT);

        // Apply time decay
        long timeElapsed = System.currentTimeMillis() - asset.getLastInteractionDate().getTime();
        double daysSinceLastInteraction = timeElapsed / (double) MILLISECONDS_IN_DAY;
        double decayFactor = Math.exp(-TIME_DECAY_FACTOR * daysSinceLastInteraction);

        return rawScore * decayFactor;
    }

    /**
     * Generate personalized feed for a user based on their interests and social connections
     */
    public static List<Asset> generatePersonalizedFeed(List<Asset> availableAssets, User currentUser) {
        if (availableAssets == null || availableAssets.isEmpty() || currentUser == null) {
            return availableAssets;
        }

        List<Asset> result = new ArrayList<>(availableAssets);
        Map<String, Double> userScores = new HashMap<>();

        // Calculate personalized score for each asset
        for (Asset asset : result) {
            double baseScore = asset.getEngagementScore();
            double personalizedScore = baseScore;

            // Boost for interest match
            if (currentUser.getInterests() != null) {
                for (String interest : currentUser.getInterests()) {
                    if (asset.getCategory() != null && asset.getCategory().equalsIgnoreCase(interest)) {
                        personalizedScore *= INTEREST_MATCH_BOOST;
                        break;
                    }
                }
            }

            // Boost for social connection
            String assetOwnerId = asset.getUserId();
            if (assetOwnerId != null) {
                // Check if user follows the asset owner
                if (currentUser.getFollowing() != null && currentUser.getFollowing().contains(assetOwnerId)) {
                    personalizedScore *= 1.3; // 30% boost for following
                }

                // Apply user authority score (cached)
                Double userScore = userScores.get(assetOwnerId);
                if (userScore == null) {
                    // Calculate user influence score based on followers
                    User assetOwner = getUserById(assetOwnerId); // This would be replaced with actual repository call
                    if (assetOwner != null) {
                        userScore = 1.0 + (assetOwner.getFollowers().size() * 0.01);
                        userScores.put(assetOwnerId, userScore);
                    } else {
                        userScore = 1.0;
                    }
                }

                personalizedScore *= userScore;
            }

            // Store the personalized score temporarily
            asset.setEngagementScore(personalizedScore);
        }

        // Sort by personalized score
        Collections.sort(result, new Comparator<Asset>() {
            @Override
            public int compare(Asset a1, Asset a2) {
                return Double.compare(a2.getEngagementScore(), a1.getEngagementScore());
            }
        });

        return result;
    }

    /**
     * Get asset recommendations based on user's recent activity and interests
     */
    public static List<Asset> getRecommendations(List<Asset> availableAssets,
                                                 List<Asset> recentlyViewedAssets,
                                                 User currentUser) {
        if (availableAssets == null || availableAssets.isEmpty()) {
            return availableAssets;
        }

        // Extract categories from recently viewed assets
        Map<String, Integer> categoryInterestScore = new HashMap<>();
        if (recentlyViewedAssets != null) {
            for (Asset asset : recentlyViewedAssets) {
                String category = asset.getCategory();
                if (category != null) {
                    Integer count = categoryInterestScore.getOrDefault(category, 0);
                    categoryInterestScore.put(category, count + 1);
                }
            }
        }

        // Add user's explicit interests
        if (currentUser != null && currentUser.getInterests() != null) {
            for (String interest : currentUser.getInterests()) {
                Integer count = categoryInterestScore.getOrDefault(interest, 0);
                categoryInterestScore.put(interest, count + 2); // Higher weight for explicit interests
            }
        }

        // Score assets based on category matches
        List<Asset> result = new ArrayList<>(availableAssets);
        for (Asset asset : result) {
            double score = asset.getEngagementScore();

            String category = asset.getCategory();
            if (category != null && categoryInterestScore.containsKey(category)) {
                int categoryScore = categoryInterestScore.get(category);
                score *= (1.0 + (categoryScore * 0.2)); // Boost by 20% per occurrence
            }

            asset.setEngagementScore(score);
        }

        // Sort by score
        Collections.sort(result, new Comparator<Asset>() {
            @Override
            public int compare(Asset a1, Asset a2) {
                return Double.compare(a2.getEngagementScore(), a1.getEngagementScore());
            }
        });

        return result;
    }

    // This would be replaced with a real repository call in production
    private static User getUserById(String userId) {
        // Placeholder - in real app, this would fetch from repository
        return null;
    }
}