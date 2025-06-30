package com.imaginit.hyperplux.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.databinding.FragmentHelpSupportBinding;
import com.imaginit.hyperplux.ui.adapters.FaqAdapter;
import com.imaginit.hyperplux.utils.AnalyticsTracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HelpSupportFragment extends Fragment {
    private static final String TAG = "HelpSupportFragment";
    private FragmentHelpSupportBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHelpSupportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Set up toolbar
            binding.toolbar.setNavigationOnClickListener(v ->
                    Navigation.findNavController(requireView()).navigateUp());

            // Set up FAQ list
            setupFaqList();

            // Set up contact methods
            setupContactMethods();

            // Track screen view
            AnalyticsTracker tracker = AnalyticsTracker.getInstance(requireContext());
            if (tracker != null) {
                tracker.trackScreenView("HelpSupport", "HelpSupportFragment");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up help & support: " + e.getMessage());
        }
    }

    private void setupFaqList() {
        try {
            // Create FAQ items using the FaqAdapter.FaqItem class
            List<FaqAdapter.FaqItem> faqItems = createFaqItems();

            // Set up recycler view
            FaqAdapter adapter = new FaqAdapter(index -> {
                try {
                    // Track FAQ item click
                    AnalyticsTracker tracker = AnalyticsTracker.getInstance(requireContext());
                    if (tracker != null && index >= 0 && index < faqItems.size()) {
                        Map<String, Object> params = new HashMap<>();
                        params.put("faq_index", index);
                        params.put("faq_question", faqItems.get(index).getQuestion());
                        tracker.trackEvent("faq_item_clicked", params);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error tracking FAQ click: " + e.getMessage());
                }
            });

            // Submit the list to the adapter
            adapter.submitList(faqItems);

            binding.faqRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            binding.faqRecyclerView.setAdapter(adapter);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up FAQ list: " + e.getMessage());
        }
    }

    private List<FaqAdapter.FaqItem> createFaqItems() {
        List<FaqAdapter.FaqItem> items = new ArrayList<>();

        try {
            // Add FAQ items using the FaqAdapter.FaqItem constructor
            items.add(new FaqAdapter.FaqItem(
                    getString(R.string.faq_how_to_add_asset_question),
                    getString(R.string.faq_how_to_add_asset_answer)
            ));

            items.add(new FaqAdapter.FaqItem(
                    getString(R.string.faq_what_is_will_question),
                    getString(R.string.faq_what_is_will_answer)
            ));

            items.add(new FaqAdapter.FaqItem(
                    getString(R.string.faq_how_to_sell_asset_question),
                    getString(R.string.faq_how_to_sell_asset_answer)
            ));

            items.add(new FaqAdapter.FaqItem(
                    getString(R.string.faq_how_to_transfer_asset_question),
                    getString(R.string.faq_how_to_transfer_asset_answer)
            ));

            items.add(new FaqAdapter.FaqItem(
                    getString(R.string.faq_delete_account_question),
                    getString(R.string.faq_delete_account_answer)
            ));

            items.add(new FaqAdapter.FaqItem(
                    getString(R.string.faq_payment_methods_question),
                    getString(R.string.faq_payment_methods_answer)
            ));
        } catch (Exception e) {
            Log.e(TAG, "Error creating FAQ items: " + e.getMessage());
        }

        return items;
    }

    private void setupContactMethods() {
        try {
            // Email contact
            binding.emailContactLayout.setOnClickListener(v -> {
                try {
                    String[] supportEmail = {getString(R.string.support_email)};
                    String subject = getString(R.string.support_email_subject);

                    Intent intent = new Intent(Intent.ACTION_SENDTO);
                    intent.setData(Uri.parse("mailto:"));
                    intent.putExtra(Intent.EXTRA_EMAIL, supportEmail);
                    intent.putExtra(Intent.EXTRA_SUBJECT, subject);

                    startActivity(Intent.createChooser(intent, getString(R.string.send_email)));

                    // Track email contact
                    AnalyticsTracker tracker = AnalyticsTracker.getInstance(requireContext());
                    if (tracker != null) {
                        tracker.trackEvent("contact_email_clicked", null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error launching email app: " + e.getMessage());
                    Snackbar.make(binding.getRoot(), R.string.no_email_app, Snackbar.LENGTH_SHORT).show();
                }
            });

            // Phone contact
            binding.phoneContactLayout.setOnClickListener(v -> {
                try {
                    String phoneNumber = getString(R.string.support_phone);

                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + phoneNumber));

                    startActivity(intent);

                    // Track phone contact
                    AnalyticsTracker tracker = AnalyticsTracker.getInstance(requireContext());
                    if (tracker != null) {
                        tracker.trackEvent("contact_phone_clicked", null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error launching phone app: " + e.getMessage());
                    Snackbar.make(binding.getRoot(), R.string.no_phone_app, Snackbar.LENGTH_SHORT).show();
                }
            });

            // Live chat
            binding.liveChatLayout.setOnClickListener(v -> {
                try {
                    Navigation.findNavController(v).navigate(R.id.action_helpSupportFragment_to_liveChatFragment);

                    // Track live chat
                    AnalyticsTracker tracker = AnalyticsTracker.getInstance(requireContext());
                    if (tracker != null) {
                        tracker.trackEvent("contact_live_chat_clicked", null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to live chat: " + e.getMessage());
                    Snackbar.make(binding.getRoot(), "Live chat is currently unavailable", Snackbar.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up contact methods: " + e.getMessage());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}