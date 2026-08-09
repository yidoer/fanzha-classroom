package cn.fanzha.classroom;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class CaseAdapter extends RecyclerView.Adapter<CaseAdapter.Holder> {
    public interface Listener {
        void onCaseClick(FraudCase item);
        void onFavoriteClick(FraudCase item);
        boolean isFavorite(FraudCase item);
    }
    private final Listener listener;
    private final List<FraudCase> items = new ArrayList<>();

    public CaseAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<FraudCase> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_case, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        FraudCase item = items.get(position);
        holder.category.setText("互动故事");
        holder.risk.setText("约 10 分钟");
        holder.title.setText(item.publicTitle());
        holder.summary.setText(item.publicTeaser());
        holder.source.setText("真假不预告 · 你的判断会改变关系与结局");
        holder.itemView.setOnClickListener(v -> listener.onCaseClick(item));
        boolean saved = listener.isFavorite(item);
        holder.favorite.setImageResource(saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
        holder.favorite.setContentDescription(saved ? "取消收藏" : "收藏故事");
        holder.favorite.setOnClickListener(v -> listener.onFavoriteClick(item));
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView category, risk, title, summary, source;
        final ImageButton favorite;
        Holder(View view) {
            super(view);
            category = view.findViewById(R.id.category);
            risk = view.findViewById(R.id.risk);
            title = view.findViewById(R.id.title);
            summary = view.findViewById(R.id.summary);
            source = view.findViewById(R.id.source);
            favorite = view.findViewById(R.id.favoriteButton);
        }
    }
}
