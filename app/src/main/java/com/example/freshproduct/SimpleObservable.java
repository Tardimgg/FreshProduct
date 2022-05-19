package com.example.freshproduct;

import java.util.Observable;

public class SimpleObservable extends Observable {

    @Override
    public void notifyObservers(){
        setChanged();
        super.notifyObservers();
    }

    @Override
    public void notifyObservers(Object arg) {
        setChanged();
        super.notifyObservers(arg);
    }
}
