package com.example.freshproduct;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.view.KeyEvent;

import com.example.freshproduct.addProduct.AddProductFragment;
import com.example.freshproduct.addProduct.CompletingAddProductListener;
import com.example.freshproduct.dataBase.Product;
import com.example.freshproduct.dataBase.RoomDB;
import com.example.freshproduct.listProducts.ListProductsFragment;
import com.example.freshproduct.listProducts.NeedsAddProductListener;
import com.example.freshproduct.listProducts.NeedsChangeProductListener;
import com.example.freshproduct.notificationService.NotificationReceiver;
import com.example.freshproduct.notificationService.NotificationTime;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements NeedsAddProductListener, CompletingAddProductListener, NeedsChangeProductListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);



        Objects.requireNonNull(getSupportActionBar()).hide();

        CurrentUser.getInstance(this);

        if (savedInstanceState == null) {
//            Fragment listProducts = ListProductsFragment.newInstance(this);
            AuthFragment listProducts = AuthFragment.newInstance();
            listProducts.subscribeToCompleteAuth((obs, ob) -> completingAuth());
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.main_fragment, listProducts);
            ft.commit();
        }

    }

    @Override
    public void requiredAddProductEvent() {
        new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("Способ ввода")
                .setMessage("Выберете способ ввода продуктов")
                .setPositiveButton("Сканирование", (dialogInterface, i) -> {
                    RoomDB.permissionSync.set(false);

                    Fragment listProducts = AddProductFragment.newInstance(true, MainActivity.this);
                    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                    ft.replace(R.id.main_fragment, listProducts);
                    ft.commit();
                })
                .setNeutralButton("Вручную", (dialogInterface, i) -> {
                    RoomDB.permissionSync.set(false);

                    Fragment listProducts = AddProductFragment.newInstance(false, MainActivity.this);
                    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                    ft.replace(R.id.main_fragment, listProducts);
                    ft.commit();
                }).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.main_fragment);
            if (currentFragment instanceof ListProductsFragment) {
                return super.onKeyDown(keyCode, event);
            }
            if (currentFragment instanceof AuthFragment) {
                ((AuthFragment) currentFragment).setLogInMode();
                return false;
            }

            openMainMenu();
//            RoomDB.setPermissionSync(true);

//            Fragment listProducts = ListProductsFragment.newInstance(this, this);
//            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
//            ft.replace(R.id.main_fragment, listProducts);
//            ft.commit();
            return false;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public void completingAddProduct() {
        openMainMenu();
    }

    private void openMainMenu() {
        RoomDB.permissionSync.set(true);
        Fragment listProducts = ListProductsFragment.newInstance(this, this);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.main_fragment, listProducts);
        ft.commit();
    }

    private void completingAuth() {
        // анимашки

        CurrentUser currentUser = CurrentUser.getInstance(this);

        if (currentUser.getLogin() != null && currentUser.getPassword() != null) {
            RoomDB.startAutoSync(this);
        }

        openMainMenu();

        NotificationReceiver.createNotifications(this, new ArrayList<>(Arrays.asList(
                new NotificationTime(13, 0),
                new NotificationTime(18, 0),
                new NotificationTime(22, 0)
        )));

    }

    @Override
    public void requiredChangeProductEvent(Product product) {
        ProductChangeFragment changeFragment = ProductChangeFragment.newInstance(product);
        changeFragment.subscribeToCompleteChange((observable, o) -> openMainMenu());

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.main_fragment, changeFragment);
        ft.commit();
    }
}
