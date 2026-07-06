package com.example.quality.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.quality.util.LogUtil;

public class FragmentHome extends Fragment {
    private static final String TAG = "FragmentHome";

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        LogUtil.d(TAG, "onAttach");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtil.d(TAG, "onCreate");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LogUtil.d(TAG, "onCreateView");
        return inflater.inflate(com.example.quality.R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LogUtil.d(TAG, "onViewCreated");
    }

    @Override
    public void onStart() {
        super.onStart();
        LogUtil.d(TAG, "onStart");
    }

    @Override
    public void onResume() {
        super.onResume();
        LogUtil.d(TAG, "onResume");
    }

    @Override
    public void onPause() {
        super.onPause();
        LogUtil.d(TAG, "onPause");
    }

    @Override
    public void onStop() {
        super.onStop();
        LogUtil.d(TAG, "onStop");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LogUtil.d(TAG, "onDestroyView");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG, "onDestroy");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        LogUtil.d(TAG, "onDetach");
    }
}
