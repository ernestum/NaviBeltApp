package com.ernestum.navibelt;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import com.ernestum.navibelt.databinding.FragmentManualControlBinding;

public class ManualControlFragment extends Fragment {

    FragmentManualControlBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(!(getContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
        } else {
            startConnectionService();
        }
    }
    
    private void disableTheGUI() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.quickChoiceFront.setEnabled(false);
                binding.quickChoiceBack.setEnabled(false);
                binding.quickChoiceLeft.setEnabled(false);
                binding.quickChoiceRight.setEnabled(false);
                binding.directionSlider.setEnabled(false);
            }
        });
    }
    
    private void enableTheGUI() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.quickChoiceFront.setEnabled(true);
                binding.quickChoiceBack.setEnabled(true);
                binding.quickChoiceLeft.setEnabled(true);
                binding.quickChoiceRight.setEnabled(true);
                binding.directionSlider.setEnabled(true);
            }
        });
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentManualControlBinding.inflate(inflater, container, false);
        disableTheGUI();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.directionSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                setDirection(seekBar.getProgress());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        binding.quickChoiceBack.setOnClickListener(view1 -> setDirection(180));
        binding.quickChoiceFront.setOnClickListener(view1 -> setDirection(0));
        binding.quickChoiceLeft.setOnClickListener(view1 -> setDirection(270));
        binding.quickChoiceRight.setOnClickListener(view1 -> setDirection(90));
    }

    private void setDirection(int direction) {
        binding.directionSlider.setProgress(direction);
        binding.directionDegreeDisplay.setText("" + direction);

        if (beltConnectionService != null) {
            beltConnectionService.setTargetAngle(direction);
        }
    }

    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            beltConnectionService = (BeltConnectionService.BeltConnectionServiceBinder) service;

            beltConnectionService.registerConnectionChangeHandler(new BeltConnectionService.ConnectionChangeHandler() {
                @Override
                public void connectionChanged(int newConnectionState) {
                    if(newConnectionState == BeltConnectionService.CONNECTED) {
                        enableTheGUI();
                    } else {
                        disableTheGUI();
                    }
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            beltConnectionService = null;
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1001: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && (getContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || getContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                    startConnectionService();
                }
            }
        }
    }

    private void startConnectionService() {
        Intent intent = new Intent(getActivity(), BeltConnectionService.class);
        getActivity().bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    BeltConnectionService.BeltConnectionServiceBinder beltConnectionService;

}