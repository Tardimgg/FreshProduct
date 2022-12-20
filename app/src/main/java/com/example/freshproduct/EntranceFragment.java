package com.example.freshproduct;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Observable;
import java.util.Observer;
import java.util.Optional;


public class EntranceFragment extends Fragment {

    public EntranceFragment() {
        // Required empty public constructor
    }

    private Observer authCompletedObserver;

    AuthFragment authFragment;
    RegistrationFragment registrationFragment;


    public static EntranceFragment newInstance() {

        return new EntranceFragment();
    }


    public void subscribeToCompleteAuth(Observer observer) {
        authCompletedObserver = observer;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authFragment = AuthFragment.newInstance();
        authFragment.subscribeToCompleteAuth(authCompletedObserver);
        authFragment.subscribeToNeedRegistration((observable, o) -> {

            if (registrationFragment == null) {
                registrationFragment = RegistrationFragment.newInstance();
                registrationFragment.subscribeToCompleteRegistration(authCompletedObserver);
            }
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            ft.replace(R.id.entrance, registrationFragment);
            ft.commit();

        });
        FragmentTransaction ft = getParentFragmentManager().beginTransaction();
        ft.add(R.id.entrance, authFragment);
        ft.commit();

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_entrance, container, false);
    }
}