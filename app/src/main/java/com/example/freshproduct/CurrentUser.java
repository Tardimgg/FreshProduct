package com.example.freshproduct;

import android.content.Context;
import android.content.SharedPreferences;

public class CurrentUser {

    private SharedPreferences sharedPreferences;

    private static CurrentUser currentUser;

    public static synchronized CurrentUser getInstance(Context context) {
        if (currentUser == null) {
            currentUser = new CurrentUser();
            currentUser.sharedPreferences = context.getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE);
        }
        return currentUser;
    }

    public static synchronized CurrentUser getInstance() {
        if (currentUser == null) {
            throw new IllegalArgumentException("missing context");
        }
        return currentUser;
    }

    public String getLogin() {
        return sharedPreferences.getString("login", null);
    }

    public String getPassword() {
        return sharedPreferences.getString("password", null);
    }

    public void setLogin(String login) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("login", login);
        editor.apply();
    }

    public void setPassword(String password) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("password", password);
        editor.apply();
    }
}
