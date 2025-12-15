package com.example.level_1_app;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;

public class BluetoothConnectionManager {

    private static final String TAG = "BT_Manager";
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
        Log.d(TAG, "Disconnecting");

        connected = false;
        stopReadThread();

        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}

        in = null;
        out = null;
        socket = null;
        deviceName = null;
        deviceAddress = null;
        dataListener = null;

        Log.d(TAG, "Disconnected");
    }

    private void handleConnectionLost() {
        connected = false;

        if (dataListener != null) {
            mainHandler.post(() ->
                    dataListener.onConnectionLost()
            );
        }
    }

    // ---------------- CALLBACK INTERFACE ----------------

    public interface DataReceivedListener {
        void onDataReceived(String data);
        void onConnectionLost();
    }
}
