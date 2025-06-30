package com.imaginit.hyperplux.ui.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.databinding.ItemPaymentMethodBinding;
import com.imaginit.hyperplux.models.PaymentMethod;

import java.util.function.Consumer;

public class PaymentMethodAdapter extends ListAdapter<PaymentMethod, PaymentMethodAdapter.PaymentMethodViewHolder> {

    private static final String TAG = "PaymentMethodAdapter";
    private final Consumer<PaymentMethod> onPaymentMethodSelected;
    private String selectedPaymentMethodId = null;

    public PaymentMethodAdapter(Consumer<PaymentMethod> onPaymentMethodSelected) {
        super(DIFF_CALLBACK);
        this.onPaymentMethodSelected = onPaymentMethodSelected;
    }

    @NonNull
    @Override
    public PaymentMethodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        try {
            ItemPaymentMethodBinding binding = ItemPaymentMethodBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new PaymentMethodViewHolder(binding);
        } catch (Exception e) {
            Log.e(TAG, "Error creating view holder: " + e.getMessage());
            // Fallback in case of inflation error
            ItemPaymentMethodBinding binding = ItemPaymentMethodBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new PaymentMethodViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PaymentMethodViewHolder holder, int position) {
        try {
            PaymentMethod paymentMethod = getItem(position);
            if (paymentMethod != null) {
                boolean isSelected = selectedPaymentMethodId != null &&
                        paymentMethod.getId() != null &&
                        selectedPaymentMethodId.equals(paymentMethod.getId());
                holder.bind(paymentMethod, isSelected);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error binding view holder: " + e.getMessage());
        }
    }

    public void setSelectedPaymentMethodId(String selectedPaymentMethodId) {
        try {
            String oldSelectedId = this.selectedPaymentMethodId;
            this.selectedPaymentMethodId = selectedPaymentMethodId;

            // Update the items that changed selection state
            if (oldSelectedId != null) {
                for (int i = 0; i < getItemCount(); i++) {
                    PaymentMethod item = getItem(i);
                    if (item != null && item.getId() != null && oldSelectedId.equals(item.getId())) {
                        notifyItemChanged(i);
                        break;
                    }
                }
            }

            if (selectedPaymentMethodId != null) {
                for (int i = 0; i < getItemCount(); i++) {
                    PaymentMethod item = getItem(i);
                    if (item != null && item.getId() != null && selectedPaymentMethodId.equals(item.getId())) {
                        notifyItemChanged(i);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting selected payment method: " + e.getMessage());
        }
    }

    class PaymentMethodViewHolder extends RecyclerView.ViewHolder {
        private final ItemPaymentMethodBinding binding;

        PaymentMethodViewHolder(ItemPaymentMethodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PaymentMethod paymentMethod, boolean isSelected) {
            try {
                Context context = binding.getRoot().getContext();
                if (context == null) return;

                // Set the payment method icon based on type
                if (paymentMethod.getType() != null) {
                    switch (paymentMethod.getType()) {
                        case CREDIT_CARD:
                            binding.paymentMethodIcon.setImageResource(R.drawable.ic_credit_card);
                            break;
                        case DEBIT_CARD:
                            binding.paymentMethodIcon.setImageResource(R.drawable.ic_credit_card);
                            break;
                        case PAYPAL:
                            binding.paymentMethodIcon.setImageResource(R.drawable.ic_paypal);
                            break;
                        case BANK_ACCOUNT:
                            binding.paymentMethodIcon.setImageResource(R.drawable.ic_bank);
                            break;
                        case CRYPTO:
                            binding.paymentMethodIcon.setImageResource(R.drawable.ic_crypto);
                            break;
                        default:
                            binding.paymentMethodIcon.setImageResource(R.drawable.ic_credit_card);
                            break;
                    }
                } else {
                    binding.paymentMethodIcon.setImageResource(R.drawable.ic_credit_card);
                }

                // Set payment method name
                binding.paymentMethodName.setText(paymentMethod.getDisplayName() != null ?
                        paymentMethod.getDisplayName() : "");

                // Set last four digits or identifier
                String lastFourDigits = paymentMethod.getLastFourDigits();
                String identifier = paymentMethod.getIdentifier();

                if (!TextUtils.isEmpty(lastFourDigits)) {
                    binding.paymentMethodDetails.setText(
                            context.getString(R.string.ending_in, lastFourDigits));
                    binding.paymentMethodDetails.setVisibility(View.VISIBLE);
                } else if (!TextUtils.isEmpty(identifier)) {
                    binding.paymentMethodDetails.setText(identifier);
                    binding.paymentMethodDetails.setVisibility(View.VISIBLE);
                } else {
                    binding.paymentMethodDetails.setVisibility(View.GONE);
                }

                // Set expiry date if available
                int expiryMonth = paymentMethod.getExpiryMonth();
                int expiryYear = paymentMethod.getExpiryYear();

                if (expiryMonth > 0 && expiryYear > 0) {
                    String expiryText = String.format("%02d/%02d", expiryMonth, expiryYear % 100);
                    binding.paymentMethodExpiry.setText(context.getString(R.string.expires, expiryText));
                    binding.paymentMethodExpiry.setVisibility(View.VISIBLE);
                } else {
                    binding.paymentMethodExpiry.setVisibility(View.GONE);
                }

                // Set selection state
                binding.radioButton.setChecked(isSelected);
                if (isSelected) {
                    binding.cardView.setStrokeWidth(
                            context.getResources().getDimensionPixelSize(R.dimen.selected_card_stroke_width));
                } else {
                    binding.cardView.setStrokeWidth(
                            context.getResources().getDimensionPixelSize(R.dimen.card_stroke_width));
                }

                // Set click listener
                binding.getRoot().setOnClickListener(v -> {
                    if (onPaymentMethodSelected != null) {
                        onPaymentMethodSelected.accept(paymentMethod);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error binding payment method: " + e.getMessage());
            }
        }
    }

    private static final DiffUtil.ItemCallback<PaymentMethod> DIFF_CALLBACK = new DiffUtil.ItemCallback<PaymentMethod>() {
        @Override
        public boolean areItemsTheSame(@NonNull PaymentMethod oldItem, @NonNull PaymentMethod newItem) {
            return oldItem != null && newItem != null &&
                    oldItem.getId() != null && newItem.getId() != null &&
                    oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull PaymentMethod oldItem, @NonNull PaymentMethod newItem) {
            if (oldItem == null || newItem == null) return false;

            return oldItem.equals(newItem);
        }
    };
}