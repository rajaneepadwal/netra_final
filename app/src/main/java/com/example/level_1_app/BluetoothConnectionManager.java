package com.example.level_1_app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothConnectionManager {

    private static final String TAG = "BT_Manager";
    private static final String PREFS_NAME = "NetraBluetoothPrefs";
    private static final String KEY_LAST_DEVICE_ADDRESS = "last_device_address";
    private static final String KEY_LAST_DEVICE_NAME = "last_device_name";

    // Standard UUID for SPP (Serial Port Profile)
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static BluetoothConnectionManager instance;

    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;
    private volatile boolean connected = false;

    private String deviceName;
    private String deviceAddress;

    private DataReceivedListener dataListener;
    private Thread readThread;
    private final Handler mainHandler;

    private BluetoothConnectionManager() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized BluetoothConnectionManager getInstance() {
        if (instance == null) {
            instance = new BluetoothConnectionManager();
        }
        return instance;
    }

    public synchronized void setConnection(BluetoothSocket socket,
                                           InputStream in,
                                           OutputStream out,
                                           String deviceName,
                                           String deviceAddress) {

        // Stop any existing reader
        stopReadThread();

        this.socket = socket;
        this.in = in;
        this.out = out;
        this.deviceName = deviceName;
        this.deviceAddress = deviceAddress;
        this.connected = true;

        Log.d(TAG, "Connected to " + deviceName + " [" + deviceAddress + "]");

        startReadingData();
    }

    // NEW: Save last connected device to SharedPreferences
    public void saveLastConnectedDevice(Context context) {
        if (deviceAddress != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_LAST_DEVICE_ADDRESS, deviceAddress)
                    .putString(KEY_LAST_DEVICE_NAME, deviceName)
                    .apply();
            Log.d(TAG, "Saved last device: " + deviceName + " [" + deviceAddress + "]");
        }
    }

    // NEW: Get last connected device address
    public String getLastDeviceAddress(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_DEVICE_ADDRESS, null);
    }

    // NEW: Get last connected device name
    public String getLastDeviceName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_DEVICE_NAME, "Unknown Device");
    }

    // NEW: Reconnect to last device
    public void reconnectToLastDevice(Context context, ReconnectCallback callback) {
        String lastAddress = getLastDeviceAddress(context);

        if (lastAddress == null) {
            mainHandler.post(() -> callback.onReconnectFailed("No previous device found"));
            return;
        }

        Log.d(TAG, "Attempting to reconnect to: " + lastAddress);

        new Thread(() -> {
            try {
                BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

                if (btAdapter == null || !btAdapter.isEnabled()) {
                    mainHandler.post(() -> callback.onReconnectFailed("Bluetooth is disabled"));
                    return;
                }

                // Get the remote device
                BluetoothDevice device = btAdapter.getRemoteDevice(lastAddress);

                // Cancel discovery to improve connection speed
                if (btAdapter.isDiscovering()) {
                    btAdapter.cancelDiscovery();
                }

                // Create socket
                BluetoothSocket tempSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);

                // Attempt connection (blocking call, ~12 sec timeout)
                tempSocket.connect();

                // Get streams
                InputStream tempIn = tempSocket.getInputStream();
                OutputStream tempOut = tempSocket.getOutputStream();

                // Set connection
                setConnection(tempSocket, tempIn, tempOut, device.getName(), device.getAddress());
                saveLastConnectedDevice(context);

                mainHandler.post(() -> callback.onReconnectSuccess());
                Log.d(TAG, "Reconnection successful");

            } catch (IOException e) {
                Log.e(TAG, "Reconnection failed: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onReconnectFailed("Cannot connect to previous device"));

            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid device address: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onReconnectFailed("Invalid device address"));

            } catch (SecurityException e) {
                Log.e(TAG, "Bluetooth permission denied: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onReconnectFailed("Bluetooth permission denied"));
            }
        }).start();
    }

    public boolean isConnected() {
        return connected && socket != null && socket.isConnected();
    }

    public BluetoothSocket getSocket() {
        return socket;
    }

    public InputStream getInputStream() {
        return in;
    }

    public OutputStream getOutputStream() {
        return out;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public void setDataReceivedListener(DataReceivedListener listener) {
        this.dataListener = listener;
    }

    // ---------------- READ THREAD ----------------

    private void startReadingData() {
        readThread = new Thread(() -> {
            byte[] buffer = new byte[1024];

            Log.d(TAG, "Read thread started");

            try {
                while (connected && socket != null && socket.isConnected()) {
                    int bytes = in.read(buffer); // BLOCKING READ

                    if (bytes > 0) {
                        String data = new String(buffer, 0, bytes);
                        Log.d(TAG, "Received: " + data);

                        if (dataListener != null) {
                            String finalData = data;
                            mainHandler.post(() ->
                                    dataListener.onDataReceived(finalData)
                            );
                        }
                    }
                }
            } catch (Exception e) {
                if (connected) {
                    Log.e(TAG, "Read error", e);
                    connected = false;

                    if (dataListener != null) {
                        mainHandler.post(() ->
                                dataListener.onConnectionLost()
                        );
                    }
                }
            }

            Log.d(TAG, "Read thread stopped");
        });

        readThread.start();
    }

    private synchronized void stopReadThread() {
        if (readThread != null && readThread.isAlive()) {
            readThread.interrupt();
            readThread = null;
        }
    }

    // ---------------- WRITE ----------------

    public synchronized boolean sendData(String data) {
        if (!isConnected() || out == null) {
            Log.e(TAG, "Send failed: not connected");
            return false;
        }

        try {
            out.write(data.getBytes());
            out.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Send error", e);
            handleConnectionLost();
            return false;
        }
    }

    public synchronized boolean sendBytes(byte[] data) {
        if (!isConnected() || out == null) {
            Log.e(TAG, "Send bytes failed: not connected");
            return false;
        }

        try {
            out.write(data);
            out.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Send bytes error", e);
            handleConnectionLost();
            return false;
        }
    }

    // ---------------- DISCONNECT ----------------

    public synchronized void disconnect() {
        connected = false;

        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}

        stopReadThread();

        socket = null;
        in = null;
        out = null;
        deviceName = null;
        deviceAddress = null;
        dataListener = null;
    }


    private void handleConnectionLost() {
        connected = false;

        if (dataListener != null) {
            mainHandler.post(() ->
                    dataListener.onConnectionLost()
            );
        }
    }

    // ---------------- CALLBACK INTERFACES ----------------

    public interface DataReceivedListener {
        void onDataReceived(String data);
        void onConnectionLost();
    }

    public interface ReconnectCallback {
        void onReconnectSuccess();
        void onReconnectFailed(String errorMessage);
    }
}
