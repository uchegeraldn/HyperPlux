package com.imaginit.hyperplux.ui.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.databinding.ItemAssetBinding;
import com.imaginit.hyperplux.models.Asset;

import java.util.function.Consumer;

public class AssetAdapter extends ListAdapter<Asset, AssetAdapter.AssetViewHolder> {
    private final Consumer<Asset> onAssetClick;
    private final Consumer<Asset> onLikeClick;
    private final Consumer<Asset> onDislikeClick;
    private final Consumer<Asset> onShareClick;
    private final Consumer<Pair<Asset, View>> onOptionMenuClick;

    public AssetAdapter(Consumer<Asset> onAssetClick, Consumer<Asset> onLikeClick,
                        Consumer<Asset> onDislikeClick) {
        this(onAssetClick, onLikeClick, onDislikeClick, null, null);
    }

    public AssetAdapter(Consumer<Asset> onAssetClick, Consumer<Asset> onLikeClick,
                        Consumer<Asset> onDislikeClick, Consumer<Asset> onShareClick,
                        Consumer<Pair<Asset, View>> onOptionMenuClick) {
        super(DIFF_CALLBACK);
        this.onAssetClick = onAssetClick;
        this.onLikeClick = onLikeClick;
        this.onDislikeClick = onDislikeClick;
        this.onShareClick = onShareClick;
        this.onOptionMenuClick = onOptionMenuClick;
    }

    @NonNull
    @Override
    public AssetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAssetBinding binding = ItemAssetBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AssetViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AssetViewHolder holder, int position) {
        Asset asset = getItem(position);
        if (asset != null) {
            holder.bind(asset);
        }
    }

    class AssetViewHolder extends RecyclerView.ViewHolder {
        private final ItemAssetBinding binding;

        AssetViewHolder(ItemAssetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Asset asset) {
            try {
                Context context = binding.getRoot().getContext();
                if (context == null || asset == null) return;

                // Set basic info
                binding.assetName.setText(asset.getName() != null ? asset.getName() : "");
                binding.assetQuantity.setText(context.getString(R.string.quantity_format, asset.getQuantity()));

                // Set status
                setAssetStatus(asset, context);

                // Set metrics
                binding.assetViews.setText(context.getString(R.string.views_likes_dislikes_format,
                        asset.getViews(), asset.getLikes(), asset.getDislikes()));

                // Load image with error handling
                String imageUri = asset.getImageUri();
                if (!TextUtils.isEmpty(imageUri)) {
                    Glide.with(context.getApplicationContext())
                            .load(imageUri)
                            .apply(new RequestOptions()
                                    .placeholder(R.drawable.ic_image_placeholder)
                                    .error(R.drawable.ic_image_placeholder))
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .centerCrop()
                            .into(binding.assetImage);
                } else {
                    binding.assetImage.setImageResource(R.drawable.ic_image_placeholder);
                }

                // Set click listeners
                binding.getRoot().setOnClickListener(v -> {
                    if (onAssetClick != null) {
                        onAssetClick.accept(asset);
                    }
                });

                binding.likeButton.setOnClickListener(v -> {
                    if (onLikeClick != null) {
                        onLikeClick.accept(asset);
                    }
                });

                binding.dislikeButton.setOnClickListener(v -> {
                    if (onDislikeClick != null) {
                        onDislikeClick.accept(asset);
                    }
                });

                binding.shareButton.setOnClickListener(v -> {
                    if (onShareClick != null) {
                        onShareClick.accept(asset);
                    } else {
                        // Default behavior if no callback provided
                        asset.setShares(asset.getShares() + 1);
                    }
                });

                // Set options menu
                binding.moreButton.setOnClickListener(v -> {
                    if (onOptionMenuClick != null) {
                        onOptionMenuClick.accept(new Pair<>(asset, v));
                    } else {
                        // Default implementation
                        showAssetOptionsMenu(v, asset);
                    }
                });
            } catch (Exception e) {
                // Log exception to prevent crashes
                android.util.Log.e("AssetAdapter", "Error binding asset: " + e.getMessage());
            }
        }

        private void setAssetStatus(Asset asset, Context context) {
            try {
                String statusText;
                int statusColor;

                if (asset.isForSale()) {
                    statusText = context.getString(R.string.for_sale);
                    statusColor = ContextCompat.getColor(context, R.color.info);
                } else if (asset.isLoanedOut()) {
                    statusText = context.getString(R.string.loaned);
                    statusColor = ContextCompat.getColor(context, R.color.warning);
                } else if (asset.isBequest()) {
                    statusText = context.getString(R.string.in_will);
                    statusColor = ContextCompat.getColor(context, R.color.dark_gray);
                } else if (asset.isHidden()) {
                    statusText = context.getString(R.string.hidden);
                    statusColor = ContextCompat.getColor(context, R.color.dark_gray);
                } else {
                    statusText = context.getString(R.string.available);
                    statusColor = ContextCompat.getColor(context, R.color.success);
                }

                binding.assetStatus.setText(statusText);

                // Create a copy of the background drawable and set its color
                Drawable background = ContextCompat.getDrawable(context, R.drawable.status_indicator_background);
                if (background != null) {
                    background = background.mutate();
                    background.setTint(statusColor);
                    binding.assetStatus.setBackground(background);
                }
            } catch (Exception e) {
                // Log exception to prevent crashes
                android.util.Log.e("AssetAdapter", "Error setting asset status: " + e.getMessage());
            }
        }

        private void showAssetOptionsMenu(View v, Asset asset) {
            try {
                Context context = v.getContext();
                if (context == null) return;

                PopupMenu popupMenu = new PopupMenu(context, v);
                popupMenu.inflate(R.menu.asset_options_menu);
                popupMenu.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.action_edit) {
                        // Handle edit action
                        return true;
                    } else if (itemId == R.id.action_delete) {
                        // Handle delete action
                        return true;
                    } else if (itemId == R.id.action_share) {
                        // Handle share action
                        return true;
                    } else if (itemId == R.id.action_toggle_sale) {
                        // Toggle for sale status
                        return true;
                    }
                    return false;
                });
                popupMenu.show();
            } catch (Exception e) {
                // Log exception to prevent crashes
                android.util.Log.e("AssetAdapter", "Error showing options menu: " + e.getMessage());
            }
        }
    }

    public static class Pair<F, S> {
        public final F first;
        public final S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }

    private static final DiffUtil.ItemCallback<Asset> DIFF_CALLBACK = new DiffUtil.ItemCallback<Asset>() {
        @Override
        public boolean areItemsTheSame(@NonNull Asset oldItem, @NonNull Asset newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Asset oldItem, @NonNull Asset newItem) {
            // Compare relevant fields for UI updates with null-safety
            return oldItem != null && newItem != null &&
                    TextUtils.equals(oldItem.getName(), newItem.getName()) &&
                    oldItem.getQuantity() == newItem.getQuantity() &&
                    oldItem.getViews() == newItem.getViews() &&
                    oldItem.getLikes() == newItem.getLikes() &&
                    oldItem.getDislikes() == newItem.getDislikes() &&
                    oldItem.isForSale() == newItem.isForSale() &&
                    oldItem.isHidden() == newItem.isHidden() &&
                    oldItem.isLoanedOut() == newItem.isLoanedOut();
        }
    };
}