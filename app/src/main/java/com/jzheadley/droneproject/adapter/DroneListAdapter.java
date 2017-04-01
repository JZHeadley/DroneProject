package com.jzheadley.droneproject.adapter;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jzheadley.droneproject.R;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class DroneListAdapter extends RecyclerView.Adapter<DroneListAdapter.ViewHolder> {

    private static final String TAG = "DroneListAdapter";
    private List<ARDiscoveryDeviceService> drones;

    public DroneListAdapter() {
        drones = new ArrayList<ARDiscoveryDeviceService>();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_drone_row, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ARDiscoveryDeviceService service = drones.get(position);
        holder.textView.setText(service.getName());
    }

    @Override
    public int getItemCount() {
        return drones.size();
    }

    public void updateList(List<ARDiscoveryDeviceService> newDrones) {
        this.drones = newDrones;
        notifyDataSetChanged();
        Log.d(TAG, "updateList: A New item has been added to the list and we have " + getItemCount() + " drones now");
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.drone_row_txt)
        TextView textView;

        public ViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
