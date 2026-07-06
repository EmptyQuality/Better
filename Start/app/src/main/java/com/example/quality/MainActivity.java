package com.example.quality;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

import com.example.quality.fragment.FragmentEnjoy;
import com.example.quality.fragment.FragmentHome;
import com.example.quality.fragment.FragmentMore;
import com.example.quality.util.LogUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    BottomNavigationView bottomNavigationView;
    FragmentHome HomeFragment = new FragmentHome();
    FragmentEnjoy EnjoyFragment = new FragmentEnjoy();
    FragmentMore MoreFragment = new FragmentMore();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtil.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, HomeFragment).commit();

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                if (item.getItemId() == R.id.house) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.container, HomeFragment).commit();
                    return true;
                } else if (item.getItemId() == R.id.enjoy) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.container, EnjoyFragment).commit();
                    return true;
                } else if (item.getItemId() == R.id.more) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.container, MoreFragment).commit();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        LogUtil.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtil.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogUtil.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogUtil.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG, "onDestroy");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        LogUtil.d(TAG, "onRestart");
    }
}