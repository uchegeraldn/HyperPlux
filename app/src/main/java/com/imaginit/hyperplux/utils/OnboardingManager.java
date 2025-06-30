package com.imaginit.hyperplux.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.ui.fragments.AddEditAssetFragment;
import com.imaginit.hyperplux.ui.fragments.AssetDetailFragment;
import com.imaginit.hyperplux.ui.fragments.AssetListFragment;
import com.imaginit.hyperplux.ui.fragments.HomeFragment;
import com.imaginit.hyperplux.ui.fragments.MarketplaceFragment;
import com.imaginit.hyperplux.ui.fragments.ProfileFragment;
import com.imaginit.hyperplux.ui.fragments.SignUpFragment;

import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetSequence;

/**
 * Manager class for handling user onboarding tutorials
 */
public class OnboardingManager {
    private static final String PREFS_NAME = "onboarding_prefs";
    private static final String KEY_PREFIX = "has_seen_tutorial_";
    private static final String KEY_FIRST_LAUNCH = "first_launch_completed";

    private final SharedPreferences preferences;
    private final Context context;
    private final AnalyticsTracker analyticsTracker;

    public OnboardingManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.analyticsTracker = AnalyticsTracker.getInstance();
    }

    /**
     * Check if it's the very first app launch
     */
    public boolean isFirstLaunch() {
        return !preferences.getBoolean(KEY_FIRST_LAUNCH, false);
    }

    /**
     * Mark first launch as completed
     */
    public void completeFirstLaunch() {
        preferences.edit().putBoolean(KEY_FIRST_LAUNCH, true).apply();
    }

    /**
     * Check if the user has seen a specific tutorial
     */
    public boolean hasSeenTutorial(String tutorialName) {
        return preferences.getBoolean(KEY_PREFIX + tutorialName, false);
    }

    /**
     * Mark a tutorial as seen
     */
    public void markTutorialAsSeen(String tutorialName) {
        preferences.edit().putBoolean(KEY_PREFIX + tutorialName, true).apply();

        // Track tutorial completion
        analyticsTracker.logEvent("tutorial_completed", "tutorial_name", tutorialName);
    }

    /**
     * Reset all tutorials so they'll be shown again
     */
    public void resetAllTutorials() {
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(KEY_PREFIX)) {
                editor.remove(key);
            }
        }
        editor.apply();

        analyticsTracker.logEvent("tutorials_reset", null);
    }

    /**
     * Show the appropriate tutorial for a fragment if user hasn't seen it yet
     */
    public void showTutorialIfNeeded(Fragment fragment) {
        if (fragment instanceof HomeFragment) {
            showHomeTutorial((HomeFragment) fragment);
        } else if (fragment instanceof AssetListFragment) {
            showAssetListTutorial((AssetListFragment) fragment);
        } else if (fragment instanceof AssetDetailFragment) {
            showAssetDetailTutorial((AssetDetailFragment) fragment);
        } else if (fragment instanceof AddEditAssetFragment) {
            showAddEditAssetTutorial((AddEditAssetFragment) fragment);
        } else if (fragment instanceof MarketplaceFragment) {
            showMarketplaceTutorial((MarketplaceFragment) fragment);
        } else if (fragment instanceof ProfileFragment) {
            showProfileTutorial((ProfileFragment) fragment);
        } else if (fragment instanceof SignUpFragment) {
            showSignUpTutorial((SignUpFragment) fragment);
        }
    }

    /**
     * Show home screen tutorial
     */
    private void showHomeTutorial(HomeFragment fragment) {
        if (hasSeenTutorial("home")) return;

        View bottomNav = fragment.getActivity().findViewById(R.id.bottom_navigation);
        View addButton = fragment.getActivity().findViewById(R.id.fab_add_asset);

        if (bottomNav == null || addButton == null) return;

        new MaterialTapTargetSequence()
                .addPrompt(createPrompt(fragment.getActivity(), bottomNav,
                        R.string.tutorial_home_title,
                        R.string.tutorial_home_message))
                .addPrompt(createPrompt(fragment.getActivity(), addButton,
                        R.string.tutorial_add_asset_title,
                        R.string.tutorial_add_asset_message))
                .setSequenceCompleteListener(() -> markTutorialAsSeen("home"))
                .show();
    }

    /**
     * Show asset list tutorial
     */
    private void showAssetListTutorial(AssetListFragment fragment) {
        if (hasSeenTutorial("asset_list") || fragment.getView() == null) return;

        View searchButton = fragment.getView().findViewById(R.id.search_button);
        View filterTabs = fragment.getView().findViewById(R.id.filter_tabs);

        if (searchButton == null || filterTabs == null) return;

        new MaterialTapTargetSequence()
                .addPrompt(createPrompt(fragment.getActivity(), searchButton,
                        R.string.tutorial_search_title,
                        R.string.tutorial_search_message))
                .addPrompt(createPrompt(fragment.getActivity(), filterTabs,
                        R.string.tutorial_filter_title,
                        R.string.tutorial_filter_message))
                .setSequenceCompleteListener(() -> markTutorialAsSeen("asset_list"))
                .show();
    }

    /**
     * Show asset detail tutorial
     */
    private void showAssetDetailTutorial(AssetDetailFragment fragment) {
        if (hasSeenTutorial("asset_detail") || fragment.getView() == null) return;

        View likeButton = fragment.getView().findViewById(R.id.likeButton);
        View shareButton = fragment.getView().findViewById(R.id.shareButton);
        View commentSection = fragment.getView().findViewById(R.id.commentsSection);

        if (likeButton == null || shareButton == null || commentSection == null) return;

        new MaterialTapTargetSequence()
                .addPrompt(createPrompt(fragment.getActivity(), likeButton,
                        R.string.tutorial_like_title,
                        R.string.tutorial_like_message))
                .addPrompt(createPrompt(fragment.getActivity(), shareButton,
                        R.string.tutorial_share_title,
                        R.string.tutorial_share_message))
                .addPrompt(createPrompt(fragment.getActivity(), commentSection,
                        R.string.tutorial_comment_title,
                        R.string.tutorial_comment_message))
                .setSequenceCompleteListener(() -> markTutorialAsSeen("asset_detail"))
                .show();
    }

    /**
     * Show add/edit asset tutorial
     */
    private void showAddEditAssetTutorial(AddEditAssetFragment fragment) {
        // Implementation for add/edit asset tutorial
        if (hasSeenTutorial("add_edit_asset") || fragment.getView() == null) return;

        // Mark as seen without showing tutorial if it's not the first time
        // Adding an asset (complex screen, don't want to overwhelm user)
        markTutorialAsSeen("add_edit_asset");
    }

    /**
     * Show marketplace tutorial
     */
    private void showMarketplaceTutorial(MarketplaceFragment fragment) {
        if (hasSeenTutorial("marketplace") || fragment.getView() == null) return;

        View categoryFilter = fragment.getView().findViewById(R.id.category_filter);
        View sortOptions = fragment.getView().findViewById(R.id.sort_options);

        if (categoryFilter == null || sortOptions == null) return;

        new MaterialTapTargetSequence()
                .addPrompt(createPrompt(fragment.getActivity(), categoryFilter,
                        R.string.tutorial_category_title,
                        R.string.tutorial_category_message))
                .addPrompt(createPrompt(fragment.getActivity(), sortOptions,
                        R.string.tutorial_sort_title,
                        R.string.tutorial_sort_message))
                .setSequenceCompleteListener(() -> markTutorialAsSeen("marketplace"))
                .show();
    }

    /**
     * Show profile tutorial
     */
    private void showProfileTutorial(ProfileFragment fragment) {
        if (hasSeenTutorial("profile") || fragment.getView() == null) return;

        View editProfileButton = fragment.getView().findViewById(R.id.edit_profile_button);
        View estatePlanningSection = fragment.getView().findViewById(R.id.will_settings_button);

        if (editProfileButton == null || estatePlanningSection == null) return;

        new MaterialTapTargetSequence()
                .addPrompt(createPrompt(fragment.getActivity(), editProfileButton,
                        R.string.tutorial_profile_edit_title,
                        R.string.tutorial_profile_edit_message))
                .addPrompt(createPrompt(fragment.getActivity(), estatePlanningSection,
                        R.string.tutorial_will_title,
                        R.string.tutorial_will_message))
                .setSequenceCompleteListener(() -> markTutorialAsSeen("profile"))
                .show();
    }

    /**
     * Show sign up tutorial
     */
    private void showSignUpTutorial(SignUpFragment fragment) {
        // No tutorial needed for signup, just mark as seen
        markTutorialAsSeen("sign_up");
    }

    /**
     * Create a tap target prompt
     */
    private MaterialTapTargetPrompt createPrompt(Activity activity, View target, int titleResId, int messageResId) {
        return new MaterialTapTargetPrompt.Builder(activity)
                .setTarget(target)
                .setPrimaryText(context.getString(titleResId))
                .setSecondaryText(context.getString(messageResId))
                .setBackgroundColour(context.getResources().getColor(R.color.primary))
                .setPrimaryTextColour(Color.WHITE)
                .setSecondaryTextColour(Color.WHITE)
                .setPromptStateChangeListener((prompt, state) -> {
                    if (state == MaterialTapTargetPrompt.STATE_DISMISSED ||
                            state == MaterialTapTargetPrompt.STATE_FOCAL_PRESSED) {
                        // Log individual step completion
                        analyticsTracker.logEvent("tutorial_step_completed",
                                "tutorial_step", context.getString(titleResId));
                    }
                })
                .create();
    }
}