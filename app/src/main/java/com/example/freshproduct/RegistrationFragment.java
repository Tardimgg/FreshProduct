package com.example.freshproduct;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Observer;

public class RegistrationFragment extends Fragment {

    public RegistrationFragment() {
        registrationCompleted = new SimpleObservable();
        // Required empty public constructor
    }

    private SimpleObservable registrationCompleted;



    public void subscribeToCompleteRegistration(Observer observer) {
        registrationCompleted.addObserver(observer);
    }

    public static RegistrationFragment newInstance() {
        return new RegistrationFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_registration, container, false);
    }
}