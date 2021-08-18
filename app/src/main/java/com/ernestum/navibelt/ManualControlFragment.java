package com.ernestum.navibelt;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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

    }
    @Override
    public void onStart() {
        super.onStart();

        // TODO: or place this in onCreate?
        Intent intent = new Intent(getActivity(), BeltConnectionService.class);
        getActivity().bindService(intent, connection, Context.BIND_AUTO_CREATE);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentManualControlBinding.inflate(inflater, container, false);
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
    }

    private ServiceConnection connection = new ServiceConnection() {



        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            beltConnectionService = ((BeltConnectionService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            beltConnectionService = null;
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    BeltConnectionService beltConnectionService;

}