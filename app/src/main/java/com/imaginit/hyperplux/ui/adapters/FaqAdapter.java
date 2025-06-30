package com.imaginit.hyperplux.ui.adapters;

import android.animation.ValueAnimator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.imaginit.hyperplux.databinding.ItemFaqBinding;

import java.util.function.Consumer;

public class FaqAdapter extends ListAdapter<FaqAdapter.FaqItem, FaqAdapter.FaqViewHolder> {

    private static final String TAG = "FaqAdapter";
    private final Consumer<Integer> onFaqItemClicked;

    /**
     * Creates a new FAQ adapter
     *
     * @param onFaqItemClicked Callback to be invoked when an FAQ item is clicked
     */
    public FaqAdapter(Consumer<Integer> onFaqItemClicked) {
        super(new FaqDiffCallback());
        this.onFaqItemClicked = onFaqItemClicked;
    }

    @NonNull
    @Override
    public FaqViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        try {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            ItemFaqBinding binding = ItemFaqBinding.inflate(inflater, parent, false);
            return new FaqViewHolder(binding);
        } catch (Exception e) {
            Log.e(TAG, "Error creating ViewHolder: " + e.getMessage());
            // Fallback in case of error
            View fallbackView = new View(parent.getContext());
            return new FaqViewHolder(ItemFaqBinding.inflate(LayoutInflater.from(parent.getContext())));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull FaqViewHolder holder, int position) {
        try {
            FaqItem item = getItem(position);
            if (item != null) {
                holder.bind(item);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error binding ViewHolder: " + e.getMessage());
        }
    }

    class FaqViewHolder extends RecyclerView.ViewHolder {
        private final ItemFaqBinding binding;
        private ValueAnimator heightAnimator;

        FaqViewHolder(ItemFaqBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            // Set click listener for expanding/collapsing
            binding.questionLayout.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    FaqItem item = getItem(position);
                    if (item != null) {
                        // Toggle expanded state
                        item.setExpanded(!item.isExpanded());
                        toggleExpansion(item.isExpanded());

                        // Notify callback
                        if (onFaqItemClicked != null) {
                            onFaqItemClicked.accept(position);
                        }
                    }
                }
            });
        }

        private void toggleExpansion(boolean expand) {
            try {
                // Cancel any running animation
                if (heightAnimator != null && heightAnimator.isRunning()) {
                    heightAnimator.cancel();
                }

                binding.expandIcon.animate()
                        .rotation(expand ? 180 : 0)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .setDuration(300)
                        .start();

                if (expand) {
                    // Show answer with animation
                    binding.answerLayout.setVisibility(View.VISIBLE);
                    binding.answerLayout.setAlpha(0f);
                    binding.answerLayout.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .start();

                    // Animate answer height
                    binding.answerLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    int targetHeight = binding.answerLayout.getMeasuredHeight();
                    heightAnimator = ValueAnimator.ofInt(0, targetHeight);
                    heightAnimator.addUpdateListener(animation -> {
                        if (binding.answerLayout != null && binding.answerLayout.getLayoutParams() != null) {
                            binding.answerLayout.getLayoutParams().height = (int) animation.getAnimatedValue();
                            binding.answerLayout.requestLayout();
                        }
                    });
                    heightAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                    heightAnimator.setDuration(300);
                    heightAnimator.start();
                } else {
                    // Hide answer with animation
                    binding.answerLayout.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .withEndAction(() -> {
                                if (binding.answerLayout != null) {
                                    binding.answerLayout.setVisibility(View.GONE);
                                }
                            })
                            .start();

                    // Animate answer height
                    int initialHeight = binding.answerLayout.getMeasuredHeight();
                    heightAnimator = ValueAnimator.ofInt(initialHeight, 0);
                    heightAnimator.addUpdateListener(animation -> {
                        if (binding.answerLayout != null && binding.answerLayout.getLayoutParams() != null) {
                            binding.answerLayout.getLayoutParams().height = (int) animation.getAnimatedValue();
                            binding.answerLayout.requestLayout();
                        }
                    });
                    heightAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                    heightAnimator.setDuration(300);
                    heightAnimator.start();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in animation: " + e.getMessage());

                // Fallback to simple visibility toggle without animation
                binding.expandIcon.setRotation(expand ? 180 : 0);
                binding.answerLayout.setVisibility(expand ? View.VISIBLE : View.GONE);
            }
        }

        void bind(FaqItem item) {
            try {
                binding.questionText.setText(item.getQuestion() != null ? item.getQuestion() : "");
                binding.answerText.setText(item.getAnswer() != null ? item.getAnswer() : "");

                // Set initial state
                boolean isExpanded = item.isExpanded();
                binding.expandIcon.setRotation(isExpanded ? 180 : 0);
                binding.answerLayout.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                binding.answerLayout.setAlpha(isExpanded ? 1f : 0f);

                // Reset height if collapsed
                if (!isExpanded && binding.answerLayout.getLayoutParams() != null) {
                    binding.answerLayout.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error binding FAQ item: " + e.getMessage());
                binding.questionText.setText("Error displaying FAQ item");
                binding.answerText.setText("");
            }
        }

        public void onViewRecycled() {
            // Cancel any running animations when view is recycled
            if (heightAnimator != null && heightAnimator.isRunning()) {
                heightAnimator.cancel();
            }

            // Reset heights to prevent issues when views are recycled
            if (binding.answerLayout.getLayoutParams() != null) {
                binding.answerLayout.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull FaqViewHolder holder) {
        super.onViewRecycled(holder);
        holder.onViewRecycled();
    }

    /**
     * Model class for FAQ items
     */
    public static class FaqItem {
        private String question;
        private String answer;
        private boolean expanded;

        public FaqItem(String question, String answer) {
            this.question = question;
            this.answer = answer;
            this.expanded = false;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }

        public boolean isExpanded() {
            return expanded;
        }

        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
        }
    }

    /**
     * DiffUtil callback for FAQ items
     */
    private static class FaqDiffCallback extends DiffUtil.ItemCallback<FaqItem> {
        @Override
        public boolean areItemsTheSame(@NonNull FaqItem oldItem, @NonNull FaqItem newItem) {
            // Use question as identifier (or implement a proper ID in the model)
            return oldItem.getQuestion() != null &&
                    oldItem.getQuestion().equals(newItem.getQuestion());
        }

        @Override
        public boolean areContentsTheSame(@NonNull FaqItem oldItem, @NonNull FaqItem newItem) {
            return oldItem.getQuestion() != null &&
                    oldItem.getQuestion().equals(newItem.getQuestion()) &&
                    oldItem.getAnswer() != null &&
                    oldItem.getAnswer().equals(newItem.getAnswer()) &&
                    oldItem.isExpanded() == newItem.isExpanded();
        }
    }
}