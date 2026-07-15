package com.siteblocker.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.siteblocker.app.R;
import com.siteblocker.app.data.AllowedSite;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter for displaying the list of allowed sites.
 * Uses DiffUtil for efficient updates and includes fade-in animations.
 */
public class SiteListAdapter extends RecyclerView.Adapter<SiteListAdapter.SiteViewHolder> {

    private List<AllowedSite> sites = new ArrayList<>();
    private final OnSiteDeleteListener deleteListener;

    public interface OnSiteDeleteListener {
        void onDelete(AllowedSite site);
    }

    public SiteListAdapter(OnSiteDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public SiteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_site, parent, false);
        return new SiteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SiteViewHolder holder, int position) {
        AllowedSite site = sites.get(position);
        holder.bind(site);

        // Entrance animation
        holder.itemView.startAnimation(
                AnimationUtils.loadAnimation(holder.itemView.getContext(), R.anim.fade_in_up));
    }

    @Override
    public int getItemCount() {
        return sites.size();
    }

    /**
     * Update the list with new data using DiffUtil for smooth transitions.
     */
    public void updateSites(List<AllowedSite> newSites) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return sites.size();
            }

            @Override
            public int getNewListSize() {
                return newSites.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return sites.get(oldItemPosition).getId() == newSites.get(newItemPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                AllowedSite oldSite = sites.get(oldItemPosition);
                AllowedSite newSite = newSites.get(newItemPosition);
                return oldSite.getDomain().equals(newSite.getDomain())
                        && oldSite.isActive() == newSite.isActive();
            }
        });

        this.sites = new ArrayList<>(newSites);
        result.dispatchUpdatesTo(this);
    }

    /**
     * ViewHolder for a single site item.
     */
    class SiteViewHolder extends RecyclerView.ViewHolder {

        private final TextView domainText;
        private final TextView labelText;
        private final ImageView deleteButton;

        SiteViewHolder(@NonNull View itemView) {
            super(itemView);
            domainText = itemView.findViewById(R.id.siteDomain);
            labelText = itemView.findViewById(R.id.siteLabel);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }

        void bind(AllowedSite site) {
            domainText.setText(site.getDomain());

            String label = site.getLabel();
            if (label != null && !label.isEmpty() && !label.equals(site.getDomain())) {
                labelText.setText(label);
                labelText.setVisibility(View.VISIBLE);
            } else {
                labelText.setVisibility(View.GONE);
            }

            deleteButton.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onDelete(site);
                }
            });
        }
    }
}
