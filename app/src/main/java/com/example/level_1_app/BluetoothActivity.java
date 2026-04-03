package com.example.level_1_app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BluetoothActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private DeviceListAdapter adapter;
    private BluetoothConnectionManager connectionManager;
    private ArrayList<DeviceItem> devices;

    private ListView listView;

    private ImageButton btnRefresh;
    private ActivityResultLauncher<Intent> bluetoothLauncher;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;
    private final Object sendLock = new Object();
    private static final String TAG = "BT_Connection";
    private static final UUID ESP32_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private Handler handler = new Handler(Looper.getMainLooper());
    private Thread connectThread;
    private ExecutorService executorService;

    private volatile boolean connected = false;
    private volatile boolean connecting = false;
    private volatile boolean pairingInProgress = false;
    private volatile boolean isScanning = false;
    private TextView tvStatus;

    private BluetoothDevice targetDevice;
    private String deviceAddress, deviceName;

    private static final int CONNECTION_TIMEOUT_MS = 15000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);
        connectionManager = BluetoothConnectionManager.getInstance();

        listView = findViewById(R.id.listViewDevices);
        btnRefresh = findViewById(R.id.btnRefresh);
        tvStatus = findViewById(R.id.tvStatus);

        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        devices = new ArrayList<>();
        adapter = new DeviceListAdapter(this, devices);
        listView.setAdapter(adapter);

        executorService = Executors.newSingleThreadExecutor();

        adapter.setOnConnectClickListener(item -> {
            connectToDevice(item.device);
        });

        btnRefresh.setOnClickListener(v -> {
            if (isScanning) {
                Toast.makeText(this, "Scanning in progress...", Toast.LENGTH_SHORT).show();
                return;
            }
            startDiscoverySafe();
        });

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            connectionManager.disconnect();
            cleanup();
            startActivity(new Intent(BluetoothActivity.this, IntroActivity.class));
            finish();
        });

        bluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                        Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Bluetooth is required", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        checkAndSetupBluetooth();
    }

    private void checkAndSetupBluetooth() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        requestAllPermissions();
    }

    private void requestAllPermissions() {
        ArrayList<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }

            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }

            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        } else {
            checkBluetoothState();
        }
    }

    private enum BtState {
        IDLE,
        SCANNING,
        PAIRING,
        CONNECTING,
        CONNECTED,
        FAILED
    }

    private BtState currentState = BtState.IDLE;

    private void updateStatus(BtState state, String deviceName) {
        currentState = state;

        runOnUiThread(() -> {
            String text;

            switch (state) {
                case SCANNING:
                    text = "Scanning for devices...";
                    btnRefresh.setEnabled(false);
                    break;

                case PAIRING:
                    text = "Pairing with " + deviceName + "...";
                    btnRefresh.setEnabled(false);
                    break;

                case CONNECTING:
                    text = "Connecting to " + deviceName + "...";
                    btnRefresh.setEnabled(false);
                    break;

                case CONNECTED:
                    text = "Connected to " + deviceName + " ✓";
                    btnRefresh.setEnabled(false);
                    break;

                case FAILED:
                    text = "Connection failed. Try again.";
                    btnRefresh.setEnabled(true);
                    break;

                default:
                    text = "Tap refresh to scan";
                    btnRefresh.setEnabled(true);
            }

            tvStatus.setText(text);
        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != PERMISSION_REQUEST_CODE) return;

        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show();
                return;
            }
        }

        checkBluetoothState();
    }

    private void checkBluetoothState() {
        if (bluetoothAdapter == null) {
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            bluetoothLauncher.launch(enableBtIntent);
            return;
        }

        updateStatus(BtState.IDLE, null);
    }

    private void startDiscoverySafe() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Scan permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        isScanning = true;
        updateStatus(BtState.SCANNING, null);

        devices.clear();
        adapter.notifyDataSetChanged();

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        boolean started = bluetoothAdapter.startDiscovery();

        if (!started) {
            isScanning = false;
            updateStatus(BtState.IDLE, null);
            Toast.makeText(this, "Failed to start scan", Toast.LENGTH_SHORT).show();
        }
    }

    private final BroadcastReceiver pairingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }

            BluetoothDevice device;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            } else {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            }

            if (device == null || targetDevice == null ||
                    !device.getAddress().equals(targetDevice.getAddress())) {
                return;
            }

            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            int bondState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE
            );

            switch (bondState) {
                case BluetoothDevice.BOND_BONDING:
                    pairingInProgress = true;
                    updateStatus(BtState.PAIRING, safeName(device));
                    break;

                case BluetoothDevice.BOND_BONDED:
                    pairingInProgress = false;
                    updateStatus(BtState.CONNECTING, safeName(device));

                    handler.postDelayed(() -> {
                        if (bluetoothAdapter.isDiscovering()) {
                            bluetoothAdapter.cancelDiscovery();
                        }
                        connect();
                    }, 500);
                    break;

                case BluetoothDevice.BOND_NONE:
                    pairingInProgress = false;
                    updateStatus(BtState.FAILED, safeName(device));
                    Toast.makeText(context, "Pairing failed", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        Intent intent = new Intent(this, MenuActivity.class);
        startActivity(intent);
        if (device == null) {
            return;
        }

        if (pairingInProgress || connecting || connected) {
            Toast.makeText(this, "Connection in progress", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        targetDevice = device;
        deviceName = safeName(device);
        deviceAddress = device.getAddress();

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        isScanning = false;

        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            updateStatus(BtState.CONNECTING, deviceName);
            connect();
        } else {
            pairingInProgress = true;
            updateStatus(BtState.PAIRING, deviceName);
            device.createBond();
        }
    }

    private void connect() {
        if (connecting || connected || targetDevice == null) {
            return;
        }

        connecting = true;

        connectThread = new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    connecting = false;
                    return;
                }

                socket = targetDevice.createRfcommSocketToServiceRecord(ESP32_UUID);

                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

                Future<Boolean> future = executorService.submit(() -> {
                    try {
                        socket.connect();
                        return socket.isConnected();
                    } catch (IOException e) {
                        Log.e(TAG, "Socket connect failed: " + e.getMessage());
                        return false;
                    }
                });

                Boolean connectionSuccess = false;
                try {
                    connectionSuccess = future.get(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    throw new IOException("Connection timeout");
                } catch (Exception e) {
                    throw new IOException("Connection error: " + e.getMessage());
                }

                if (!connectionSuccess || !socket.isConnected()) {
                    throw new IOException("Connection failed");
                }

                in = socket.getInputStream();
                out = socket.getOutputStream();

                if (in == null || out == null) {
                    throw new IOException("Failed to get streams");
                }

                connected = true;
                connecting = false;

                updateStatus(BtState.CONNECTED, deviceName);
                connectionManager.setConnection(socket, in, out, deviceName, deviceAddress);
                connectionManager.saveLastConnectedDevice(this);

                startReading();
                sendData("START\n");

                runOnUiThread(() -> {
                    Toast.makeText(this, "Connected to " + deviceName, Toast.LENGTH_SHORT).show();
                    handler.postDelayed(() -> {
                        openMenuActivity();
                    }, 500);
                });

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage());

                connecting = false;
                connected = false;

                try {
                    if (socket != null) {
                        socket.close();
                    }
                } catch (IOException ignored) {
                }

                updateStatus(BtState.FAILED, deviceName);

                runOnUiThread(() -> {
                    String errorMsg = "Connection failed";
                    if (e.getMessage() != null) {
                        if (e.getMessage().contains("timeout")) {
                            errorMsg = "Connection timeout";
                        } else if (e.getMessage().contains("refused")) {
                            errorMsg = "Connection refused";
                        }
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                });
            }
        });

        connectThread.start();
    }

    private void openMenuActivity() {
        if (!connectionManager.isConnected()) {
            return;
        }

        Intent intent = new Intent(this, MenuActivity.class);
        intent.putExtra("DEVICE_NAME", deviceName);
        intent.putExtra("DEVICE_ADDRESS", deviceAddress);
        startActivity(intent);
    }

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    device = intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                } else {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                }

                if (device == null) return;

                for (DeviceItem item : devices) {
                    if (item.device != null &&
                            item.device.getAddress().equals(device.getAddress())) {
                        return;
                    }
                }

                devices.add(new DeviceItem(safeName(device), device, true));
                adapter.notifyDataSetChanged();

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                isScanning = false;
                updateStatus(BtState.IDLE, null);

                if (devices.isEmpty()) {
                    Toast.makeText(context, "No devices found", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();

        registerReceiver(
                pairingReceiver,
                new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        );

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(pairingReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(discoveryReceiver); } catch (Exception ignored) {}
        cleanup();
    }

    private void cleanup() {
        stopReading();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (Exception e) {
            }
        }

        if (connectThread != null && connectThread.isAlive()) {
            connectThread.interrupt();
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }

        if (!connectionManager.isConnected()) {
            closeConnection();
        }
    }

    private void closeConnection() {
        try { if (in != null)  { in.close();     in = null;  } } catch (IOException ignored) {}
        try { if (out != null) { out.close();    out = null; } } catch (IOException ignored) {}
        try { if (socket != null) { socket.close(); socket = null; } } catch (IOException ignored) {}
        connected = false;
        connecting = false;
    }


    private String safeName(BluetoothDevice device) {
        if (device == null) return "Unknown";

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return device.getAddress();
        }

        String name = device.getName();
        if (name != null && !name.trim().isEmpty()) {
            return name;
        } else {
            return device.getAddress();
        }
    }

    private Thread readThread;
    private volatile boolean keepReading = false;

    public interface OnDataReceivedListener {
        void onDataReceived(String data);
    }


    private synchronized void startReading() {
        if (in == null || socket == null || readThread != null) return;

        keepReading = true;

        readThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            try {
                while (keepReading && socket.isConnected()) {
                    bytes = in.read(buffer);

                    if (bytes > 0) {
                        String received = new String(buffer, 0, bytes).trim();

                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Read failed: " + e.getMessage());
            }
        });

        readThread.start();
    }

    private void stopReading() {
        keepReading = false;

        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }
    }

    public  void sendData(String data) {
        synchronized (sendLock) {
            if (out == null || socket == null || !socket.isConnected()) {
                return;
            }

            try {
                out.write(data.getBytes());
                out.flush();
            } catch (IOException e) {
                Log.e(TAG, "Send failed: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!BluetoothConnectionManager.getInstance().isConnected()) {
            connected = false;
            connecting = false;
            pairingInProgress = false;
            targetDevice = null;
            deviceName = null;
            deviceAddress = null;

            updateStatus(BtState.IDLE, null);
        }
    }
}
