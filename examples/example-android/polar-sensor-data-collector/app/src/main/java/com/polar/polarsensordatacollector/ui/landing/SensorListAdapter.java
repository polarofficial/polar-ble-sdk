package com.polar.polarsensordatacollector.ui.landing;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.polar.polarsensordatacollector.R;
import com.polar.sdk.api.model.PolarDeviceInfo;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SensorListAdapter extends RecyclerView.Adapter<SensorListAdapter.ViewHolder> {

    final List<PolarDeviceInfo> dataItems;
    private final ItemSelected itemSelected;

    public SensorListAdapter(List<PolarDeviceInfo> dataItems, ItemSelected selected) {
        this.dataItems = dataItems;
        this.itemSelected = selected;
    }

    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.device_list_item, parent, false);
        return new ViewHolder(v, itemSelected);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.name.setText(dataItems.get(position).getName());
        holder.rssi.setText(dataItems.get(position).getRssi() + "dbm");
        final Context context = holder.itemView.getContext();
        if (dataItems.get(position).isConnectable()) {
            holder.background.setBackgroundColor(context.getColor(R.color.color_connected));
        } else if (!dataItems.get(position).isConnectable()) {
            holder.background.setBackgroundColor(context.getColor(R.color.color_not_connectable));
        } else {
            holder.background.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    public int getItemCount() {
        return dataItems.size();
    }

    public interface ItemSelected {
        void itemSelected(@Nullable PolarDeviceInfo info);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final TextView name;
        private final TextView rssi;
        private final ItemSelected itemSelected;
        private final LinearLayout background;

        public ViewHolder(View v, ItemSelected itemSelected) {
            super(v);
            v.setOnClickListener(this);
            this.itemSelected = itemSelected;
            this.name = v.findViewById(R.id.device_name);
            this.rssi = v.findViewById(R.id.device_rssi);
            this.background = v.findViewById(R.id.device_item_background);
        }

        @Override
        public void onClick(View view) {
            itemSelected.itemSelected(SensorListAdapter.this.dataItems.get(this.getBindingAdapterPosition()));
        }
    }
}
