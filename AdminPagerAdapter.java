package com.example.laporan2;

import static java.security.AccessController.getContext;

import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class AdminPagerAdapter extends FragmentStateAdapter {
    private List<Fragment> fragments = new ArrayList<>();

    public AdminPagerAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment fragment;
        switch (position) {
            case 0:
                fragment = new ProcessedDataFragment();
                break;
            case 1:
                fragment = new NewDataFragment();
                break;
            case 2:
                fragment = new EditedDataFragment();
                break;
            default:
                fragment = new ProcessedDataFragment();
        }

        // Pastikan fragment belum ada di list sebelum menambahkannya
        if (!fragments.contains(fragment)) {
            fragments.add(fragment);
        }

        return fragment;
    }

    @Override
    public int getItemCount() {
        return 3;
    }

    // Tambahkan method untuk refresh semua fragment
    public void refreshAllFragments() {
        for (Fragment fragment : fragments) {
            if (fragment instanceof RefreshableFragment) {
                ((RefreshableFragment) fragment).refreshData();
            }
        }
    }
}
