package com.example.level_1_app;

import android.bluetooth.BluetoothDevice;

class DeviceItem {
    String text;
    BluetoothDevice device;
    boolean isPaired;

    DeviceItem(String text, BluetoothDevice device, boolean isPaired) {
        this.text = text;
        this.device = device;
        this.isPaired = isPaired;
    }
}
