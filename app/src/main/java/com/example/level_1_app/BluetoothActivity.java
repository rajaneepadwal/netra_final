package com.example.level_1_app;//declares package name

import android.Manifest;//to import manifest class
import android.bluetooth.BluetoothAdapter;//adapter
import android.bluetooth.BluetoothDevice;//get deviceName, deviceAddress
import android.bluetooth.BluetoothManager;//system service manager
import android.bluetooth.BluetoothSocket;//for connection channels
import android.content.BroadcastReceiver;//system events listener
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
//import android.widget.ProgressBar;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
//import com.google.android.material.floatingactionbutton.FloatingActionButton;

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
    private ProgressOverlay progressOverlay;

    private ListView listView;
//    private ProgressBar progressBar;
//    private TextView scanningText;
    private ImageButton btnRefresh;
    private ActivityResultLauncher<Intent> bluetoothLauncher;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;
    private static final String TAG = "BT_Connection";
    private static final UUID ESP32_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private Handler handler = new Handler(Looper.getMainLooper());
    private Thread connectThread;
    private ExecutorService executorService;

    private boolean connected = false;
    private boolean connecting = false;
    private boolean pairingInProgress = false;
    private TextView tvStatus;

    private BluetoothDevice targetDevice;
    private String deviceAddress, deviceName;

    private static final int CONNECTION_TIMEOUT_MS = 15000; // 15 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);//setting UI as xml file
        connectionManager = BluetoothConnectionManager.getInstance();//object of manager class


        listView = findViewById(R.id.listViewDevices);
//        progressBar = findViewById(R.id.progressBar);
//        scanningText = findViewById(R.id.scanningText);
        btnRefresh = findViewById(R.id.btnRefresh);
        tvStatus = findViewById(R.id.tvStatus);

        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        devices = new ArrayList<>();
        adapter = new DeviceListAdapter(this, devices);

        listView.setAdapter(adapter);
        adapter.setOnConnectClickListener(item -> {
            progressOverlay.show();
            progressOverlay.updateProgress(10);
            connectToDevice(item.device);

        });

        btnRefresh.setOnClickListener(v -> {
            Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show();
            checkAndSetupBluetooth();
        });

        // Back button functionality
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            connectionManager.disconnect();//closes the socket
            cleanup();//stop discovery unregister receivers shuts down threads
//        new Intent(BluetoothActivity.this, IntroActivity.class);
            startActivity(new Intent(BluetoothActivity.this, IntroActivity.class));//back to intro screen
            finish();
        });

        // Initialize executor service
        executorService = Executors.newSingleThreadExecutor();

        View overlay = findViewById(R.id.progressOverlay);
        progressOverlay = new ProgressOverlay(overlay);
        //progressbar animation

        // Launcher for turning on Bluetooth
        bluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                        startDiscoverySafe();
                    } else {
                        Toast.makeText(
                                this,
                                "Bluetooth is required to scan devices",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
        checkAndSetupBluetooth();
    }

    private void checkAndSetupBluetooth() {
        if (bluetoothAdapter == null) {
            Toast.makeText(
                    this,
                    "Bluetooth not supported on this device",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        requestAllPermissions();
    }

    private void requestAllPermissions() {
        ArrayList<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
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
            // Android 6–11
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

        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        this,
                        "Permissions are required to continue",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
        }

        checkBluetoothState();
    }

    private void checkBluetoothState() {
        if (bluetoothAdapter == null) return;

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent =
                    new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            bluetoothLauncher.launch(enableBtIntent);
            return;
        }

        startDiscoverySafe();
    }

    private void startDiscoverySafe() {
        updateStatus(BtState.SCANNING, null);

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Log.d(TAG, "startDiscoverySafe: started.");

        // Clear old devices
        devices.clear();
        adapter.notifyDataSetChanged();

        // Optional header
//        devices.add(new DeviceItem("=== AVAILABLE DEVICES ===", null, false));
        adapter.notifyDataSetChanged();

        // Cancel any ongoing discovery
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        bluetoothAdapter.startDiscovery();
    }

    private final BroadcastReceiver pairingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "pairingReceiver: onReceive started.");

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
                    Log.d(TAG, "Device is pairing...");
                    break;

                case BluetoothDevice.BOND_BONDED:
                    pairingInProgress = false;
                    Log.d(TAG, "Device paired successfully. Starting connection...");
                    updateStatus(BtState.CONNECTING, safeName(device));

                    handler.postDelayed(() -> {
                        if (bluetoothAdapter.isDiscovering()) {
                            bluetoothAdapter.cancelDiscovery();
                        }
                        connect(); // Start actual socket connection
                    }, 500);
                    break;

                case BluetoothDevice.BOND_NONE:
                    pairingInProgress = false;
                    updateStatus(BtState.FAILED, safeName(device));
                    Log.e(TAG, "Pairing failed.");

                    Toast.makeText(
                            context,
                            "Pairing failed. Please try again.",
                            Toast.LENGTH_SHORT
                    ).show();
                    break;
            }
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "connectToDevice: device is null");
            return;
        }

        if (pairingInProgress || connecting || connected) {
            Log.w(TAG, "connectToDevice: Already in progress");
            Toast.makeText(this, "Connection already in progress", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "connectToDevice: Initiating connection...");

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        targetDevice = device;
        deviceName = safeName(device);
        deviceAddress = device.getAddress();

        // Stop discovery before pairing/connecting
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            Log.d(TAG, "Device already paired. Connecting directly...");
            updateStatus(BtState.CONNECTING, deviceName);
            connect();
        } else {
            Log.d(TAG, "Device not paired. Starting pairing...");
            pairingInProgress = true;
            updateStatus(BtState.PAIRING, deviceName);
            device.createBond();
        }
    }

    private void connect() {
        if (connecting || connected || targetDevice == null) {
            Log.w(TAG, "connect: Invalid state");
            return;
        }

        connecting = true;
        Log.d(TAG, "connect: Starting connection thread...");

        connectThread = new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    connecting = false;
                    Log.e(TAG, "Missing BLUETOOTH_CONNECT permission");
                    return;
                }

                Log.d(TAG, "Creating RFCOMM socket...");
                socket = targetDevice.createRfcommSocketToServiceRecord(ESP32_UUID);

                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

                Log.d(TAG, "Attempting socket connection with timeout...");

                // Execute connection with timeout
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
                    Log.e(TAG, "Connection timeout after " + CONNECTION_TIMEOUT_MS + "ms");
                    future.cancel(true);
                    throw new IOException("Connection timeout");
                } catch (Exception e) {
                    Log.e(TAG, "Connection error: " + e.getMessage());
                    throw new IOException("Connection error: " + e.getMessage());
                }

                if (!connectionSuccess || !socket.isConnected()) {
                    throw new IOException("Socket connection failed");
                }

                Log.d(TAG, "Socket connected. Getting streams...");
                in = socket.getInputStream();
                out = socket.getOutputStream();

                // Verify streams are valid
                if (in == null || out == null) {
                    throw new IOException("Failed to get input/output streams");
                }

                // Connection successful
                connected = true;
                connecting = false;

                Log.d(TAG, "Connection successful!");
                updateStatus(BtState.CONNECTED, deviceName);
               connectionManager.setConnection(
                        socket,
                        in,
                        out,
                        deviceName,
                        deviceAddress
                );
                startReading();
                sendData("START\n");

                runOnUiThread(() -> {
                    Toast.makeText(
                            this,
                            "Connected to " + deviceName,
                            Toast.LENGTH_SHORT
                    ).show();

                    // Small delay before opening menu to ensure UI updates
                    handler.postDelayed(() -> {
                        openMenuActivity();
                    }, 500);
                });

            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage(), e);

                connecting = false;
                connected = false;

                // Clean up socket
                try {
                    if (socket != null) socket.close();
                } catch (IOException ignored) {
                    Log.e(TAG, "Error closing socket: " + ignored.getMessage());
                }

                updateStatus(BtState.FAILED, deviceName);

                runOnUiThread(() -> {
                    String errorMsg = "Connection failed";
                    if (e.getMessage() != null) {
                        if (e.getMessage().contains("timeout")) {
                            errorMsg = "Connection timeout. Device may be out of range.";
                        } else if (e.getMessage().contains("refused")) {
                            errorMsg = "Connection refused by device.";
                        } else {
                            errorMsg = "Connection failed: " + e.getMessage();
                        }
                    }

                    Toast.makeText(
                            this,
                            errorMsg,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });

        connectThread.start();
    }

    private void openMenuActivity() {
        Log.d(TAG, "openMenuActivity: Checking socket state...");

        // STRICT CHECK — only navigate if socket is truly connected
//        if (socket == null || !socket.isConnected()) {
//            Log.w(TAG, "Socket not connected. Staying on BluetoothActivity.");
//            return;
//        }
//
//        if (!connected) {
//            Log.w(TAG, "Connected flag false. Staying on BluetoothActivity.");
//            return;
//        }
        if (!connectionManager.isConnected()) {
            Log.w(TAG, "ConnectionManager reports not connected.");
            return;
        }


        Log.d(TAG, "Socket connected. Navigating to MenuActivity.");

        Intent intent = new Intent(this, MenuActivity.class);
        intent.putExtra("DEVICE_NAME", deviceName);
        intent.putExtra("DEVICE_ADDRESS", deviceAddress);
        startActivity(intent);
    }

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) return;

            BluetoothDevice device;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            } else {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            }

            if (device == null) return;

            // Avoid duplicates
            for (DeviceItem item : devices) {
                if (item.device != null &&
                        item.device.getAddress().equals(device.getAddress())) {
                    return;
                }
            }

            devices.add(new DeviceItem(safeName(device), device, true));
            adapter.notifyDataSetChanged();
        }
    };

    @Override
    protected void onStart() {
        super.onStart();

        registerReceiver(
                pairingReceiver,
                new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        );

        registerReceiver(
                discoveryReceiver,
                new IntentFilter(BluetoothDevice.ACTION_FOUND)
        );
    }

    @Override
    protected void onStop() {
        super.onStop();

        try {
            unregisterReceiver(pairingReceiver);
        } catch (Exception ignored) {}

        try {
            unregisterReceiver(discoveryReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    private void cleanup() {
        stopReading();
        Log.d(TAG, "cleanup: Cleaning up resources...");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            try {
                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter.cancelDiscovery();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error cancelling discovery: " + e.getMessage());
            }
        }

        // Interrupt connection thread if running
        if (connectThread != null && connectThread.isAlive()) {
            connectThread.interrupt();
        }

        // Shutdown executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        if (!connectionManager.isConnected()) {
            closeConnection();
        }
    }

    private void closeConnection() {
        Log.d(TAG, "closeConnection: Closing connection...");

        try {
            if (in != null) {
                in.close();
                in = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing input stream: " + e.getMessage());
        }

        try {
            if (out != null) {
                out.close();
                out = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing output stream: " + e.getMessage());
        }

        try {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket: " + e.getMessage());
        }

        connected = false;
        connecting = false;
    }

    private String safeName(BluetoothDevice device) {
        if (device == null) return "Unknown device";

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return "Unknown device";
        }

        String name = device.getName();
        return (name != null && !name.trim().isEmpty())
                ? name
                : "Unknown device";
    }
    private Thread readThread;
    private volatile boolean keepReading = false;

    // Optional listener if you want callbacks
    public interface OnDataReceivedListener {
        void onDataReceived(String data);
    }

    private OnDataReceivedListener dataListener;

    /**
     * Start listening for incoming Bluetooth data.
     * Call ONLY after successful connection.
     */
    private void startReading() {
        if (in == null || socket == null || readThread != null) return;

        keepReading = true;

        readThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            Log.d(TAG, "Bluetooth read thread started");

            try {
                while (keepReading && socket.isConnected()) {
                    bytes = in.read(buffer); // BLOCKS until data arrives

                    if (bytes > 0) {
                        String received = new String(buffer, 0, bytes).trim();
                        Log.d(TAG, "Received: " + received);

                        if (dataListener != null) {
                            runOnUiThread(() ->
                                    dataListener.onDataReceived(received)
                            );
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Read thread stopped: " + e.getMessage());
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
    public synchronized void sendData(String data) {
        if (out == null || socket == null || !socket.isConnected()) {
            Log.e(TAG, "sendData: Not connected");
            return;
        }

        try {
            out.write(data.getBytes());
            out.flush();
            Log.d(TAG, "Sent: " + data);
        } catch (IOException e) {
            Log.e(TAG, "Send failed: " + e.getMessage());
        }
    }

}
