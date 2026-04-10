package com.example.level_1_app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

class DeviceListAdapter extends ArrayAdapter<DeviceItem> {

    // --- Step 1: callback interface + field + setter ---
    public interface OnConnectClickListener {
        void onConnectClick(DeviceItem item);
    }

    private OnConnectClickListener connectClickListener;

    public void setOnConnectClickListener(OnConnectClickListener listener) {
        this.connectClickListener = listener;
    }

    private static class ViewHolder {
        TextView deviceName;
        Button btnConnect;
        ImageView imgBluetooth;
    }

    DeviceListAdapter(Context context, ArrayList<DeviceItem> items) {
        super(context, 0, items);
    }

    @Override
    public boolean isEnabled(int position) {
        DeviceItem item = getItem(position);
        return item.device != null;   // disable headers
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.listview_row, parent, false);

            holder = new ViewHolder();
            holder.deviceName = convertView.findViewById(R.id.tvDeviceLabel);
            holder.btnConnect = convertView.findViewById(R.id.btnConnect);
            holder.imgBluetooth = convertView.findViewById(R.id.imgBluetooth);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        DeviceItem item = getItem(position);

        if (item != null) {
            if (item.device == null) {
                // header
                holder.deviceName.setText(item.text);
                holder.btnConnect.setVisibility(View.GONE);
                holder.imgBluetooth.setAlpha(0.4f);
            } else {
                // actual device
                holder.deviceName.setText(item.text);
                holder.btnConnect.setVisibility(View.VISIBLE);
                holder.imgBluetooth.setAlpha(1f);

                // --- Step 2: trigger callback on button click ---
                holder.btnConnect.setOnClickListener(v -> {
                    if (connectClickListener != null) {
                        connectClickListener.onConnectClick(item);
                    }
                });
            }
        }
        return convertView;
    }

}

