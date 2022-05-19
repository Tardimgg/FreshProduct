package com.example.freshproduct.notificationService;

import java.io.Serializable;

public class NotificationTime implements Serializable {

    public final int hour;
    public final int minutes;

    public NotificationTime(int hour, int minutes) {
        this.hour = hour;
        this.minutes = minutes;
    }

}
