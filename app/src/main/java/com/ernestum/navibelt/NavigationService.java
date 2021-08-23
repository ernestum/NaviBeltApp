package com.ernestum.navibelt;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

public class NavigationService extends Service {

    private FusedLocationProviderClient fusedLocationClient;
    private ArrayList<NavigationStateHandler> navigationStateHandlers = new ArrayList<>();
    private LatLng destination = null;
    private BeltConnectionService.BeltConnectionServiceBinder beltConnectionService;
    private SensorManager sensorManager;
    private LocationResult latestLocationResult = null;
    private float[] latestRotation = null;

    LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            if (locationResult == null) return;
            latestLocationResult = locationResult;
            onPoseUpdate();
        }
    };

    private void onPoseUpdate() {
        if(latestLocationResult == null || latestRotation == null) return;

        for(NavigationStateHandler handler : navigationStateHandlers)
            handler.onPoseUpdate(latestLocationResult, latestRotation);


          // TODO figure out the correct mapping here!
        float currentYaw = getYawFromRotation(latestRotation);
        float bearingToDestination = getBearingToDestination();


        float targetAngle = normalizeAngle(bearingToDestination - currentYaw);
        for(NavigationStateHandler handler : navigationStateHandlers)
            handler.onTargetDirectionUpdate(targetAngle);
        if(beltConnectionService != null) beltConnectionService.setTargetAngle((int) targetAngle);

        boolean isDestinationReached = false; // TODO: check if goal is reached
        if(isDestinationReached) {
            destination = null;
            stopLocationUpdates();
            stopRotationUpdates();
            if(beltConnectionService != null) beltConnectionService.setEnableMotors(false);
            for(NavigationStateHandler handler : navigationStateHandlers)
                handler.onDestinationReached();
        }
    }

    private float getBearingToDestination() {
        Location destLocation = new Location("mapmarker");
        destLocation.setLatitude(destination.latitude);
        destLocation.setLongitude(destination.longitude);

        Location curLocation = latestLocationResult.getLastLocation();

        float bearing = curLocation.bearingTo(destLocation);
        return normalizeAngle(bearing);
    }

    private static float normalizeAngle(float a) {
            if (a >= 360) return normalizeAngle(a - 360.f);
            if (a < 0) return normalizeAngle(a + 360.f);
            return a;
    }

    private static float getYawFromRotation(float[] rotation) {
        float[] r = new float[9];
        SensorManager.getRotationMatrixFromVector(r, rotation);
        float[] orientation = new float[3];
        SensorManager.getOrientation(r, orientation);
        return (float) Math.toDegrees(orientation[0]);
    }

    SensorEventListener rotationListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                latestRotation = event.values;
                onPoseUpdate();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };

    private ServiceConnection beltServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            beltConnectionService = (BeltConnectionService.BeltConnectionServiceBinder) iBinder;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            beltConnectionService = null;
        }
    };

    interface NavigationStateHandler {
        void onNavigationStarted(LatLng destination);
        void onNavigationAborted();
        void onDestinationReached();
        void onPoseUpdate(LocationResult location, float[] orientation);
        void onTargetDirectionUpdate(float direction);
    }

    class NavigationBinder extends Binder {

        void startNavigationTo(LatLng destination) {
            NavigationService.this.destination = destination;
            startLocationUpdates();
            startRotationUpdates();
            if(beltConnectionService != null) beltConnectionService.setEnableMotors(true);

            for(NavigationStateHandler handler : navigationStateHandlers)
                handler.onNavigationStarted(destination);

        }

        void abortNavigation() {
            stopLocationUpdates();
            stopRotationUpdates();
            destination = null;
            if(beltConnectionService != null) beltConnectionService.setEnableMotors(false);

            for(NavigationStateHandler handler : navigationStateHandlers)
                handler.onNavigationAborted();
        }

        void addNavigationHandler(NavigationStateHandler handler) {
            navigationStateHandlers.add(handler);
        }

        void removeNavigationHandler(NavigationStateHandler handler) {
            navigationStateHandlers.remove(handler);
        }

        LatLng getDestination() {
            return destination;
        }
    }


    public NavigationService() {
    }

    @SuppressLint("MissingPermission")  // TODO: for now we just manually give permissions
    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void startRotationUpdates() {
        sensorManager.registerListener(rotationListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME);
    }

    private void stopRotationUpdates() {
        sensorManager.unregisterListener(rotationListener);
    }


    @Override
    public void onCreate() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Intent intent = new Intent(this, BeltConnectionService.class);
        this.bindService(intent, beltServiceConnection, Context.BIND_AUTO_CREATE);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return new NavigationBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
        // TODO: here we should probably remove the correct navigation state handlers
    }
}