package com.example.freshproduct.notificationService;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.freshproduct.MainActivity;
import com.example.freshproduct.R;
import com.example.freshproduct.dataBase.Product;
import com.example.freshproduct.dataBase.RoomDB;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

public class NotificationReceiver extends BroadcastReceiver {

    private static String CHANNEL_ID = "Product channel";
    private static final int NOTIFY_ID = 101;

    private static final String WORKING_TIME = "WORKING_TIME";
    private static final String WORKING_TIME_BUNDLE = "WORKING_TIME_BUNDLE";


    @Override
    public void onReceive(Context context, Intent intent) {
        ArrayList<NotificationTime> workingTime = new ArrayList<>();
        if (intent != null) {
            Bundle bundle = intent.getParcelableExtra(WORKING_TIME_BUNDLE);
            if (bundle != null) {
                Serializable serializable = bundle.getSerializable(WORKING_TIME);
                if (serializable != null) {
                    workingTime.addAll((List<NotificationTime>) serializable);
                }
            }
        }
        Intent intentToStart = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intentToStart, PendingIntent.FLAG_IMMUTABLE);

        RoomDB.getInstance(context)
                .productDao()
                .getAllOnce()
                .subscribeOn(Schedulers.newThread())
                .subscribe(new DisposableSingleObserver<List<Product>>() {
                    @Override
                    public void onSuccess(List<Product> products) {

                        Date currentDate = new Date();

                        for (Product product : products) {

                            double delta = Math.floor((double) ((product.expirationDate - currentDate.getTime()) / 1000 / 60 / 60) / 24);
                            if (delta < 5) {

                                NotificationCompat.Builder builder =
                                        new NotificationCompat.Builder(context, CHANNEL_ID)
                                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                                .setContentTitle("Осталось дней: " + (int) delta)
                                                .setContentText(product.productSubtitle)
                                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                                .setContentIntent(pendingIntent);

                                NotificationManagerCompat notificationManager =
                                        NotificationManagerCompat.from(context);
                                notificationManager.notify(NOTIFY_ID + (int) product.uid, builder.build());
                            }
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                });


        Intent nextIntent = new Intent(context, NotificationReceiver.class);
        Bundle bundle = new Bundle();
        bundle.putSerializable(WORKING_TIME, workingTime);
        nextIntent.putExtra(WORKING_TIME_BUNDLE, bundle);


        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(context, 0, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        alarmManager.set(AlarmManager.RTC_WAKEUP, getNextDate(workingTime).getTime(), nextPendingIntent);
    }

    private Date getNextDate(ArrayList<NotificationTime> workingTime) {
        if (workingTime.size() != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            int currentYear = calendar.get(Calendar.YEAR);
            int currentMonth = calendar.get(Calendar.MONTH);
            int currentDay = calendar.get(Calendar.DAY_OF_MONTH);

            Date currentDate = new Date();

            Date previousWorkingDate = new GregorianCalendar(currentYear,
                    currentMonth,
                    currentDay,
                    workingTime.get(0).hour,
                    workingTime.get(0).minutes
            ).getTime();

            for (int i = 1; i < workingTime.size(); ++i) {
                Date workingDate = new GregorianCalendar(currentYear,
                        currentMonth,
                        currentDay,
                        workingTime.get(i).hour,
                        workingTime.get(i).minutes
                ).getTime();

                if (workingDate.getTime() >= currentDate.getTime() && previousWorkingDate.getTime() < currentDate.getTime()) {
                    return workingDate;
                } else if (workingDate.getTime() > currentDate.getTime() && previousWorkingDate.getTime() > currentDate.getTime()) {
                    return previousWorkingDate;
                }
                previousWorkingDate = workingDate;
            }

            calendar.setTimeInMillis(calendar.getTimeInMillis() + 24 * 60 * 60 * 1000);
            return calendar.getTime();
        }
        return null;
    }

    public static void createNotifications(Context context, ArrayList<NotificationTime> data) {
        Collections.sort(data, (f, s) -> {
            if (f.hour == s.hour) {
                return Integer.compare(f.minutes, s.minutes);
            }
            return Integer.compare(f.hour, s.hour);
        });
        createNotificationChannel(context);

        Intent intent = new Intent(context, NotificationReceiver.class);
        Bundle bundle = new Bundle();
        bundle.putSerializable(WORKING_TIME, data);
        intent.putExtra(WORKING_TIME_BUNDLE, bundle);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());

        alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
    }

    private static void createNotificationChannel(Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.app_name);
            String description = context.getString(R.string.delete);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

}