package cn.fanzha.classroom;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class CaseAdapter extends RecyclerView.Adapter<CaseAdapter.Holder> {
    public interface Listener {
        void onCaseClick(FraudCase item);
        void onFavoriteClick(FraudCase item);
        boolean isFavorite(FraudCase item);
        boolean isCleared(FraudCase item);
    }
    private final Listener listener;
    private final List<FraudCase> items = new ArrayList<>();
    private final List<String> states = new ArrayList<>();

    public CaseAdapter(Listener listener) { this.listener = listener; }

    /** Diffed against a snapshot of the visual state, so only changed rows rebind. */
    public void submit(List<FraudCase> newItems) {
        final List<FraudCase> before = new ArrayList<>(items);
        final List<String> beforeState = new ArrayList<>(states);
        final List<String> afterState = new ArrayList<>();
        for (FraudCase item : newItems) afterState.add(stateOf(item));
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return before.size(); }
            @Override public int getNewListSize() { return newItems.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                return before.get(oldPos).id.equals(newItems.get(newPos).id);
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                return beforeState.get(oldPos).equals(afterState.get(newPos));
            }
        });
        items.clear();
        items.addAll(newItems);
        states.clear();
        states.addAll(afterState);
        diff.dispatchUpdatesTo(this);
    }

    /** Everything a row actually draws, folded into one comparable key. */
    private String stateOf(FraudCase item) {
        return item.id + "|" + item.publicTitle() + "|" + item.publicShelf()
                + "|" + item.branchLabel() + "|" + item.durationLabel()
                + "|" + listener.isFavorite(item) + "|" + listener.isCleared(item);
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_case, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        FraudCase item = items.get(position);
        holder.category.setText(item.publicShelf());
        holder.risk.setText(item.durationLabel());
        holder.title.setText(item.publicTitle());
        holder.summary.setText(item.publicTeaser());
        holder.source.setText(item.branchLabel());
        holder.badge.setVisibility(listener.isCleared(item) ? View.VISIBLE : View.GONE);
        View.OnClickListener openStory = v -> listener.onCaseClick(item);
        holder.itemView.setOnClickListener(openStory);
        holder.category.setOnClickListener(openStory);
        holder.risk.setOnClickListener(openStory);
        holder.title.setOnClickListener(openStory);
        holder.summary.setOnClickListener(openStory);
        holder.source.setOnClickListener(openStory);
        holder.badge.setOnClickListener(openStory);
        boolean saved = listener.isFavorite(item);
        holder.favorite.setImageResource(saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
        holder.favorite.setContentDescription(saved ? "取消收藏" : "收藏故事");
        holder.favorite.setOnClickListener(v -> listener.onFavoriteClick(item));
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView category, risk, title, summary, source, badge;
        final ImageButton favorite;
        Holder(View view) {
            super(view);
            category = view.findViewById(R.id.category);
            risk = view.findViewById(R.id.risk);
            title = view.findViewById(R.id.title);
            summary = view.findViewById(R.id.summary);
            source = view.findViewById(R.id.source);
            badge = view.findViewById(R.id.clearedBadge);
            favorite = view.findViewById(R.id.favoriteButton);
        }
    }
}
