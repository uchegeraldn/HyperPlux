package com.imaginit.hyperplux.viewmodels;

import android.app.Application;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.annotation.NonNull;

import com.imaginit.hyperplux.repositories.AssetRepository;
import com.imaginit.hyperplux.repositories.UserRepository;

public class ViewModelFactory implements ViewModelProvider.Factory {
    private Application application;
    private AssetRepository assetRepository;
    private UserRepository userRepository;

    // Constructor with Application and AssetRepository
    public ViewModelFactory(Application application, AssetRepository assetRepository) {
        this.application = application;
        this.assetRepository = assetRepository;
    }

    // Constructor with Application and both repositories
    public ViewModelFactory(Application application, AssetRepository assetRepository, UserRepository userRepository) {
        this.application = application;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(AssetViewModel.class)) {
            return (T) new AssetViewModel(application, assetRepository);
        } else if (modelClass.isAssignableFrom(UserViewModel.class)) {
            if (userRepository == null) {
                throw new IllegalArgumentException("UserRepository is required for UserViewModel");
            }
            return (T) new UserViewModel(userRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}