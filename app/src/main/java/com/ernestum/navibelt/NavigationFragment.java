package com.ernestum.navibelt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.Arrays;

public class NavigationFragment extends Fragment implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {
    private NavigationService.NavigationBinder navigationService;
    private GoogleMap map;
    private Marker destinationMarker;
    private Circle positionMarker;
    private Marker directionMarker;


    private ServiceConnection navigationServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            navigationService = (NavigationService.NavigationBinder) iBinder;

            navigationService.addNavigationHandler(new NavigationService.NavigationStateHandler() {
                @Override
                public void onNavigationStarted(LatLng destination) {
                    destinationMarker.setPosition(destination);
                    destinationMarker.setVisible(true);
                }

                @Override
                public void onNavigationAborted() {
                    destinationMarker.setVisible(false);
                }

                @Override
                public void onDestinationReached() {
                    destinationMarker.setVisible(false);
                }

                @Override
                public void onPoseUpdate(LocationResult location, float[] rotation) {
                    if(map != null) {
                        Location currentLoc = location.getLastLocation();
                        LatLng currentlatlong = new LatLng(currentLoc.getLatitude(), currentLoc.getLongitude());

                        positionMarker.setCenter(currentlatlong);
                        positionMarker.setRadius(currentLoc.getAccuracy());
                        positionMarker.setVisible(true);

                        directionMarker.setPosition(currentlatlong);

                        float[] orientationMat = new float[9];
                        SensorManager.getRotationMatrixFromVector(orientationMat, rotation);
                        float[] orientation = new float[3];
                        SensorManager.getOrientation(orientationMat, orientation);
                        Log.d("NAVIGATION", Arrays.toString(orientation));

                        directionMarker.setRotation((float) Math.toDegrees(orientation[0]));
                        directionMarker.setVisible(true);
                    }
                }

                @Override
                public void onTargetDirectionUpdate(float direction) {

                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            navigationService = null;
            positionMarker.setVisible(false);
            destinationMarker.setVisible(false);
        }
    };


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * If Google Play services is not installed on the device, the user will be prompted to
     * install it inside the SupportMapFragment. This method will only be triggered once the
     * user has installed Google Play services and returned to the app.
     */
    @SuppressLint("MissingPermission")  // TODO: right now we manually grant all permissions in the settings
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        positionMarker = map.addCircle(new CircleOptions().center(new LatLng(0, 0)));
        positionMarker.setVisible(false);
        destinationMarker = map.addMarker(new MarkerOptions().position(new LatLng(0, 0)));
        destinationMarker.setVisible(false);
        directionMarker = map.addMarker(new MarkerOptions().position(new LatLng(0, 0)));
        directionMarker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.ic_direction_indicator));
        directionMarker.setVisible(false);

        LocationServices.getFusedLocationProviderClient(getContext()).getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                googleMap.moveCamera(CameraUpdateFactory.zoomTo(15));
                googleMap.moveCamera(CameraUpdateFactory.newLatLng(
                        new LatLng(location.getLatitude(),
                        location.getLongitude())));
            }
        });
        googleMap.setOnMapLongClickListener(this);

    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        if(navigationService != null) navigationService.startNavigationTo(latLng);
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_navigation, container, false);
    }



    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        Intent intent = new Intent(getContext(), NavigationService.class);
        getContext().bindService(intent, navigationServiceConnection, Context.BIND_AUTO_CREATE);
    }
}