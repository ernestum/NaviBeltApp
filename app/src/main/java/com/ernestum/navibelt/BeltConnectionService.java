package com.ernestum.navibelt;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BeltConnectionService extends Service {
    public static final int DISCONNECTED = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;

    interface ConnectionChangeHandler {
        void connectionChanged(int newConnectionState);
    }

    Set<ConnectionChangeHandler> connectionChangeHandlers = new HashSet<ConnectionChangeHandler>();

    private void setConnectionState(int newState) {
        connectionState = newState;
        for(ConnectionChangeHandler h : connectionChangeHandlers)
            h.connectionChanged(newState);

    }

    private static final UUID DIRECTION_SERVICE_UUID = UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214");
    private static final UUID TARGET_DIRECTION_CHARACTERISTIC_UUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214");

    public BeltConnectionService() {
    }

    private BluetoothAdapter bluetoothAdapter;  // Initialized in onCreate
    private BluetoothLeScanner scanner;  // Initialized in scanForBelt
    private BluetoothGatt gatt;  // Initialized when there is the first scan result
    private BluetoothGattService gattService;  // Initialized when gatt services have been discovered
    private BluetoothGattCharacteristic targetDirectionCharacteristic; //Initialized when the gattService has been discovered

    private int connectionState = DISCONNECTED;

    public class BeltConnectionServiceBinder extends Binder {
        int getConnectionState() {
            return connectionState;
        }

        void setTargetAngle(int angle) {
            if (getConnectionState() == CONNECTED) {
                targetDirectionCharacteristic.setValue(new byte[]{(byte) ((angle / 360.) * 255)});
                gatt.writeCharacteristic(targetDirectionCharacteristic);
                Log.d("BLE", "Target angel set!");
            } else {
                Log.w("BLE", "Can not set target angle when not connected!");
            }
        }

        void registerConnectionChangeHandler(ConnectionChangeHandler handler) {
            connectionChangeHandlers.add(handler);
        }

        void unregisterConnectionChangeHandler(ConnectionChangeHandler handler) {
            connectionChangeHandlers.remove(handler);
        }
    }

    @Override
    public void onCreate() {
        startConnecting();
        registerReceiver(deviceStateChangeReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    private void startConnecting() {
        setConnectionState(CONNECTING);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null) {
            Log.d("ConnectionService","No bluetooth available on this device!");
            setConnectionState(DISCONNECTED);
        }

        requestBluetoothEnableIfDisabled();

        if(bluetoothAdapter.isEnabled()) {
            startScanForBelt();
        } else {
            // The deviceStateChangeReceiver will start the scan as soon as the adapter is enabled
        }
    }

    private boolean requestBluetoothEnableIfDisabled() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(enableBtIntent);
        }
        return true;
    }



    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            Log.d("ConnectionService", "Found device! " + result.toString());
            gatt = result.getDevice().connectGatt(BeltConnectionService.this.getBaseContext(), false, new BluetoothGattCallback() {

                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        gatt.discoverServices();
                        Log.d("BLE", "gatt connected!");
                    }
                    if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        setConnectionState(DISCONNECTED);
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);
                    Log.d("BLE", "Services discovered!");
                    gattService = gatt.getService(DIRECTION_SERVICE_UUID);
                    targetDirectionCharacteristic = gattService.getCharacteristic(TARGET_DIRECTION_CHARACTERISTIC_UUID);
                    setConnectionState(CONNECTED);
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicRead(gatt, characteristic, status);
                    // TODO: notify of new angle if it is the current angle characteristic
                }


                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    super.onCharacteristicChanged(gatt, characteristic);
                    // TODO: notify of new angle if it is the current angle characteristic
                }

            });


            scanner.stopScan(scanCallback);
        }
    };

    private void startScanForBelt() {

        if(!(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            String msg = "Got no permission to ACCESS_FINE_LOCATION. Go to the permission settings and set it to 'Always' for a quick fix.!";
            Log.d("BLE", msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            setConnectionState(DISCONNECTED);
            return;
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            Log.w("BLE", "Could not get a bluetooth scanner!");
            setConnectionState(DISCONNECTED);
            return;
        }

        ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        ScanFilter serviceFilter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(DIRECTION_SERVICE_UUID)).build();
        scanner.startScan(Arrays.asList(serviceFilter) , scanSettings, scanCallback);
        Log.d("BLE","Started Scan!");

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (connectionState != CONNECTED) {
                    scanner.stopScan(scanCallback);
                    Log.d("BLE", "Scanning stopped after 30s!");
                    setConnectionState(DISCONNECTED);
                }
            }
        }, 30000);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return new BeltConnectionServiceBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    private final BroadcastReceiver deviceStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    case BluetoothAdapter.STATE_OFF:
                        // Bluetooth has been turned off;
                        setConnectionState(DISCONNECTED);
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        // Bluetooth is turning off;
                        // TODO: disable all motors for graceful shutdown
                        break;
                    case BluetoothAdapter.STATE_ON:
                        // Bluetooth is on
                        startScanForBelt();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        // Bluetooth is turning on
                        break;
                }
            }
        }
    };

    @Override
    public void onDestroy() {
        unregisterReceiver(deviceStateChangeReceiver);
    }
}