package com.imaginit.hyperplux.ui.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.databinding.ItemMessageAssetBinding;
import com.imaginit.hyperplux.databinding.ItemMessageCallBinding;
import com.imaginit.hyperplux.databinding.ItemMessageImageBinding;
import com.imaginit.hyperplux.databinding.ItemMessageTextIncomingBinding;
import com.imaginit.hyperplux.databinding.ItemMessageTextOutgoingBinding;
import com.imaginit.hyperplux.models.Asset;
import com.imaginit.hyperplux.models.ChatMessage;
import com.imaginit.hyperplux.repositories.AssetRepository;

import java.util.Date;
import java.util.function.Consumer;

/**
 * Adapter for chat messages in a RecyclerView
 */
public class ChatMessageAdapter extends ListAdapter<ChatMessage, RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_TEXT_OUTGOING = 1;
    private static final int VIEW_TYPE_TEXT_INCOMING = 2;
    private static final int VIEW_TYPE_IMAGE_OUTGOING = 3;
    private static final int VIEW_TYPE_IMAGE_INCOMING = 4;
    private static final int VIEW_TYPE_ASSET = 5;
    private static final int VIEW_TYPE_CALL = 6;

    private final String currentUserId;
    private final AssetRepository assetRepository;
    private final LifecycleOwner lifecycleOwner;
    private final Consumer<Asset> onAssetClick;
    private final Consumer<String> onImageClick;

    public ChatMessageAdapter(String currentUserId, AssetRepository assetRepository,
                              LifecycleOwner lifecycleOwner,
                              Consumer<Asset> onAssetClick,
                              Consumer<String> onImageClick) {
        super(new ChatMessageDiffCallback());
        this.currentUserId = currentUserId;
        this.assetRepository = assetRepository;
        this.lifecycleOwner = lifecycleOwner;
        this.onAssetClick = onAssetClick;
        this.onImageClick = onImageClick;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = getItem(position);
        if (message == null) return VIEW_TYPE_TEXT_INCOMING;

        String type = message.getType();
        if (type == null) type = ChatMessage.TYPE_TEXT;

        if (type.equals(ChatMessage.TYPE_TEXT)) {
            return isOutgoingMessage(message) ? VIEW_TYPE_TEXT_OUTGOING : VIEW_TYPE_TEXT_INCOMING;
        } else if (type.equals(ChatMessage.TYPE_IMAGE)) {
            return isOutgoingMessage(message) ? VIEW_TYPE_IMAGE_OUTGOING : VIEW_TYPE_IMAGE_INCOMING;
        } else if (type.equals(ChatMessage.TYPE_ASSET)) {
            return VIEW_TYPE_ASSET;
        } else if (type.equals(ChatMessage.TYPE_CALL_INFO)) {
            return VIEW_TYPE_CALL;
        }

        // Default to text
        return isOutgoingMessage(message) ? VIEW_TYPE_TEXT_OUTGOING : VIEW_TYPE_TEXT_INCOMING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        try {
            switch (viewType) {
                case VIEW_TYPE_TEXT_OUTGOING:
                    return new OutgoingTextViewHolder(
                            ItemMessageTextOutgoingBinding.inflate(inflater, parent, false));

                case VIEW_TYPE_TEXT_INCOMING:
                    return new IncomingTextViewHolder(
                            ItemMessageTextIncomingBinding.inflate(inflater, parent, false));

                case VIEW_TYPE_IMAGE_OUTGOING:
                case VIEW_TYPE_IMAGE_INCOMING:
                    return new ImageViewHolder(
                            ItemMessageImageBinding.inflate(inflater, parent, false),
                            viewType == VIEW_TYPE_IMAGE_OUTGOING);

                case VIEW_TYPE_ASSET:
                    return new AssetViewHolder(
                            ItemMessageAssetBinding.inflate(inflater, parent, false));

                case VIEW_TYPE_CALL:
                    return new CallViewHolder(
                            ItemMessageCallBinding.inflate(inflater, parent, false));

                default:
                    return new OutgoingTextViewHolder(
                            ItemMessageTextOutgoingBinding.inflate(inflater, parent, false));
            }
        } catch (Exception e) {
            // Fallback to prevent crashes
            android.util.Log.e("ChatAdapter", "Error creating ViewHolder: " + e.getMessage());
            return new OutgoingTextViewHolder(
                    ItemMessageTextOutgoingBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = getItem(position);
        if (message == null) return;

        try {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_TEXT_OUTGOING:
                    ((OutgoingTextViewHolder) holder).bind(message);
                    break;

                case VIEW_TYPE_TEXT_INCOMING:
                    ((IncomingTextViewHolder) holder).bind(message);
                    break;

                case VIEW_TYPE_IMAGE_OUTGOING:
                case VIEW_TYPE_IMAGE_INCOMING:
                    ((ImageViewHolder) holder).bind(message);
                    break;

                case VIEW_TYPE_ASSET:
                    ((AssetViewHolder) holder).bind(message);
                    break;

                case VIEW_TYPE_CALL:
                    ((CallViewHolder) holder).bind(message);
                    break;
            }
        } catch (Exception e) {
            android.util.Log.e("ChatAdapter", "Error binding message: " + e.getMessage());
        }
    }

    private boolean isOutgoingMessage(ChatMessage message) {
        if (message == null || message.getSenderId() == null || currentUserId == null) {
            return false;
        }
        return message.getSenderId().equals(currentUserId);
    }

    private String formatTimestamp(Date timestamp) {
        if (timestamp == null) return "";

        try {
            long now = System.currentTimeMillis();
            long time = timestamp.getTime();

            return DateUtils.getRelativeTimeSpanString(
                    time, now, DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE).toString();
        } catch (Exception e) {
            android.util.Log.e("ChatAdapter", "Error formatting timestamp: " + e.getMessage());
            return "";
        }
    }

    /**
     * ViewHolder for outgoing text messages
     */
    class OutgoingTextViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageTextOutgoingBinding binding;

        OutgoingTextViewHolder(ItemMessageTextOutgoingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ChatMessage message) {
            binding.messageText.setText(message.getText() != null ? message.getText() : "");
            binding.timeText.setText(formatTimestamp(message.getTimestamp()));
        }
    }

    /**
     * ViewHolder for incoming text messages
     */
    class IncomingTextViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageTextIncomingBinding binding;

        IncomingTextViewHolder(ItemMessageTextIncomingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ChatMessage message) {
            binding.messageText.setText(message.getText() != null ? message.getText() : "");
            binding.timeText.setText(formatTimestamp(message.getTimestamp()));
        }
    }

    /**
     * ViewHolder for image messages
     */
    class ImageViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageImageBinding binding;
        private final boolean isOutgoing;

        ImageViewHolder(ItemMessageImageBinding binding, boolean isOutgoing) {
            super(binding.getRoot());
            this.binding = binding;
            this.isOutgoing = isOutgoing;
        }

        void bind(ChatMessage message) {
            Context context = binding.getRoot().getContext();
            if (context == null) return;

            try {
                // Set layout parameters based on message direction
                binding.messageContainer.setBackgroundResource(
                        isOutgoing ? R.drawable.bg_message_outgoing : R.drawable.bg_message_incoming);

                // Load image
                String imageUrl = message.getImageUrl();
                if (!TextUtils.isEmpty(imageUrl)) {
                    Glide.with(context.getApplicationContext())
                            .load(imageUrl)
                            .apply(new RequestOptions()
                                    .placeholder(R.drawable.ic_image_placeholder)
                                    .error(R.drawable.ic_image_placeholder))
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.messageImage);
                } else {
                    binding.messageImage.setImageResource(R.drawable.ic_image_placeholder);
                }

                binding.timeText.setText(formatTimestamp(message.getTimestamp()));

                // Set image click listener to view full-size image
                binding.messageImage.setOnClickListener(v -> {
                    if (onImageClick != null && !TextUtils.isEmpty(imageUrl)) {
                        onImageClick.accept(imageUrl);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("ChatAdapter", "Error binding image: " + e.getMessage());
            }
        }
    }

    /**
     * ViewHolder for asset messages
     */
    class AssetViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageAssetBinding binding;

        AssetViewHolder(ItemMessageAssetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ChatMessage message) {
            Context context = binding.getRoot().getContext();
            if (context == null) return;

            // Show loading state
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.assetContainer.setVisibility(View.GONE);

            // Safely parse asset ID
            int assetId = 0;
            try {
                assetId = message.getAssetId();
            } catch (Exception e) {
                android.util.Log.e("ChatAdapter", "Invalid asset ID: " + e.getMessage());
                showAssetError();
                return;
            }

            if (assetId <= 0) {
                showAssetError();
                return;
            }

            try {
                // Fetch asset details
                if (lifecycleOwner != null && assetRepository != null) {
                    assetRepository.getAssetById(assetId).observe(lifecycleOwner, asset -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.assetContainer.setVisibility(View.VISIBLE);

                        if (asset != null) {
                            binding.assetNameText.setText(asset.getName());

                            String description = asset.getDescription();
                            if (!TextUtils.isEmpty(description)) {
                                binding.assetDescriptionText.setText(description);
                                binding.assetDescriptionText.setVisibility(View.VISIBLE);
                            } else {
                                binding.assetDescriptionText.setVisibility(View.GONE);
                            }

                            // Set price if available
                            double price = asset.getCurrentValue();  // Using the correct method from Asset model
                            if (price > 0) {
                                String currency = asset.getCurrency();
                                if (currency == null) currency = "USD";
                                binding.assetPriceText.setText(String.format(
                                        "%s %s", currency, String.format("%.2f", price)));
                                binding.assetPriceText.setVisibility(View.VISIBLE);
                            } else {
                                binding.assetPriceText.setVisibility(View.GONE);
                            }

                            // Load asset image
                            String imageUri = asset.getImageUri();
                            if (!TextUtils.isEmpty(imageUri)) {
                                Glide.with(context.getApplicationContext())
                                        .load(imageUri)
                                        .apply(new RequestOptions()
                                                .placeholder(R.drawable.ic_image_placeholder)
                                                .error(R.drawable.ic_image_placeholder))
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        .into(binding.assetImage);
                            } else {
                                binding.assetImage.setImageResource(R.drawable.ic_image_placeholder);
                            }

                            // Set click listener to navigate to asset details
                            binding.assetContainer.setOnClickListener(v -> {
                                if (onAssetClick != null) {
                                    onAssetClick.accept(asset);
                                }
                            });
                        } else {
                            showAssetError();
                        }

                        binding.timeText.setText(formatTimestamp(message.getTimestamp()));
                    });
                } else {
                    showAssetError();
                }
            } catch (Exception e) {
                android.util.Log.e("ChatAdapter", "Error loading asset: " + e.getMessage());
                showAssetError();
            }
        }

        private void showAssetError() {
            binding.progressBar.setVisibility(View.GONE);
            binding.assetContainer.setVisibility(View.VISIBLE);
            binding.assetNameText.setText(R.string.asset_not_found);
            binding.assetDescriptionText.setVisibility(View.GONE);
            binding.assetPriceText.setVisibility(View.GONE);
            binding.assetImage.setImageResource(R.drawable.ic_image_placeholder);
        }
    }

    /**
     * ViewHolder for call messages
     */
    class CallViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageCallBinding binding;

        CallViewHolder(ItemMessageCallBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ChatMessage message) {
            Context context = binding.getRoot().getContext();
            if (context == null) return;

            try {
                // Set call icon based on type and status
                boolean isVideoCall = message.isVideoCall();
                String callStatus = message.getCallStatus();

                if (callStatus == null) callStatus = "missed";

                binding.callTypeIcon.setImageResource(
                        isVideoCall ? R.drawable.ic_videocam : R.drawable.ic_phone);

                // Set call status text
                int colorResId;
                int stringResId;

                switch (callStatus) {
                    case "missed":
                        stringResId = R.string.missed_call;
                        colorResId = R.color.error;
                        break;
                    case "declined":
                        stringResId = R.string.declined_call;
                        colorResId = R.color.warning;
                        break;
                    case "completed":
                        stringResId = R.string.call_duration;
                        colorResId = R.color.text_secondary;
                        binding.callStatusText.setText(String.format(
                                context.getString(stringResId),
                                message.getCallDuration() != null ? message.getCallDuration() : "0:00"));
                        binding.callStatusText.setTextColor(
                                context.getResources().getColor(colorResId));
                        binding.timeText.setText(formatTimestamp(message.getTimestamp()));
                        return;
                    default:
                        stringResId = R.string.call_ended;
                        colorResId = R.color.text_secondary;
                        break;
                }

                binding.callStatusText.setText(stringResId);
                binding.callStatusText.setTextColor(context.getResources().getColor(colorResId));
                binding.timeText.setText(formatTimestamp(message.getTimestamp()));
            } catch (Exception e) {
                android.util.Log.e("ChatAdapter", "Error binding call info: " + e.getMessage());
            }
        }
    }

    /**
     * DiffCallback for chat messages
     */
    private static class ChatMessageDiffCallback extends DiffUtil.ItemCallback<ChatMessage> {
        @Override
        public boolean areItemsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
            if (oldItem == null || newItem == null) return false;
            return oldItem.equals(newItem);
        }
    }
}