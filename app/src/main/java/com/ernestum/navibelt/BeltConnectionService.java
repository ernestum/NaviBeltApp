package com.ernestum.navibelt;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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

import java.util.Arrays;

public class BeltConnectionService extends Service {
    public BeltConnectionService() {
    }

    BluetoothAdapter bluetoothAdapter;

    @Override
    public void onCreate() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        requestBluetoothEnableIfDisabled();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter.isEnabled()) {
            startScanningForBelt();
//            connectToBelt();
        }

        registerReceiver(deviceStateChangeReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    private boolean requestBluetoothEnableIfDisabled() {
        if (bluetoothAdapter == null) {  // TODO: this check belongs somewhere else!
            Log.d("ConnectionService","No bluetooth available on this device!");
            return false;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(enableBtIntent);
        }
        return true;
    }


    private BluetoothLeScanner scanner;

    ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            Log.d("ConnectionService", "Found device! " + result.toString());
        }
    };

    private void startScanningForBelt() {

        if(!(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            Log.d("belt", "got no permission!");
        } else {
            Log.d("belt", "got permission!");
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();

//        ScanSettings scanSettings = new ScanSettings.Builder().build();
//        ScanFilter serviceFilter = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString("22d40000-458f-44cc-b199-2d6ae6c69984")).build();

//        scanner.startScan(Arrays.asList(serviceFilter) , scanSettings, scanCallback);
        scanner.startScan(scanCallback);
        Log.d("ConnectionService","Started Scan!");

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                scanner.stopScan(scanCallback);
                Log.d("ConnectionService", "Scanning stopped after 30s!");
            }
        }, 30000);

    }

    private void connectToBelt() {
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice("7e:22:eb:93:f6:06");
        } catch (IllegalArgumentException exception) {
            Log.d("ConnectionService", "Device not found with provided address.");
            return;
        }
        Log.d("ConnectionService", "Connected to belt!");

    }


    public class LocalBinder extends Binder {
        BeltConnectionService getService() {
            // Return this instance of LocalService so clients can call public methods
            return BeltConnectionService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    private final BroadcastReceiver deviceStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    case BluetoothAdapter.STATE_OFF:
                        // Bluetooth has been turned off;
                        // TODO: handle this
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        // Bluetooth is turning off;
                        break;
                    case BluetoothAdapter.STATE_ON:
                        // Bluetooth is on
                        startScanningForBelt();
//                        connectToBelt();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        // Bluetooth is turning on
                        // TODO: disable all motors for graceful shutdown
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