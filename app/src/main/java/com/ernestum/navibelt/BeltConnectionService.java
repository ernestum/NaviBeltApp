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

import java.util.Arrays;
import java.util.UUID;

public class BeltConnectionService extends Service {
    private final UUID service_uuid = UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214");
    private final UUID direction_characteristic_uuid = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214");
    public BeltConnectionService() {
    }

    BluetoothAdapter bluetoothAdapter;
    BluetoothGatt gatt;

    @Override
    public void onCreate() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        requestBluetoothEnableIfDisabled();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter.isEnabled()) {
            startScanForBelt();
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
            gatt = result.getDevice().connectGatt(BeltConnectionService.this.getBaseContext(), false, new BluetoothGattCallback() {

                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);
                    gatt.discoverServices();
                    Log.d("BLE", "gatt connected!");
                    // TODO: notify of connection loss if we disconnected
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);
                    Log.d("BLE", "Services discovered!");
                    BluetoothGattService service = gatt.getService(service_uuid);
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(direction_characteristic_uuid);
                    characteristic.setValue(new byte[] {1});
                    gatt.writeCharacteristic(characteristic);
                    Log.d("BLE", "Characeristic written!");
                    // TODO: notify that we are now connected!
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
            Log.d("belt", "got no permission!");
        } else {
            Log.d("belt", "got permission!");
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();

        ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        ScanFilter serviceFilter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(service_uuid)).build();
        scanner.startScan(Arrays.asList(serviceFilter) , scanSettings, scanCallback);
        Log.d("ConnectionService","Started Scan!");

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                scanner.stopScan(scanCallback);
                Log.d("ConnectionService", "Scanning stopped after 30s!");
            }
        }, 30000);
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
                        // TODO: notify user that we disconnected
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