package com.imaginit.hyperplux;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.imaginit.hyperplux.database.AppDatabase;
import com.imaginit.hyperplux.databinding.ActivityMainBinding;
import com.imaginit.hyperplux.repositories.AssetRepository;
import com.imaginit.hyperplux.repositories.UserRepository;
import com.imaginit.hyperplux.viewmodels.AssetViewModel;
import com.imaginit.hyperplux.viewmodels.UserViewModel;
import com.imaginit.hyperplux.viewmodels.ViewModelFactory;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener authListener;

    // ViewModels
    private AssetViewModel assetViewModel;
    private UserViewModel userViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance();

        // Setup Navigation
        setupNavigation();

        // Initialize ViewModels
        initViewModels();

        // Setup Auth state listener
        setupAuthListener();
    }

    private void setupNavigation() {
        try {
            // Retrieve the NavController
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
            if (navHostFragment != null) {
                navController = navHostFragment.getNavController();

                // Set up AppBarConfiguration
                appBarConfiguration = new AppBarConfiguration.Builder(
                        R.id.homeFragment,
                        R.id.assetListFragment,
                        R.id.marketplaceFragment,
                        R.id.profileFragment)
                        .build();

                // Connect ActionBar with NavController
                NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

                // Connect BottomNavigationView with NavController
                NavigationUI.setupWithNavController(binding.bottomNavView, navController);

                // Hide bottom navigation on auth screens and add asset screen
                navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                    if (destination.getId() == R.id.loginFragment ||
                            destination.getId() == R.id.signUpFragment ||
                            destination.getId() == R.id.addEditAssetFragment) {
                        binding.bottomNavView.setVisibility(View.GONE);
                    } else {
                        binding.bottomNavView.setVisibility(View.VISIBLE);
                    }

                    Log.d(TAG, "Navigation to: " + destination.getLabel());
                });
            } else {
                Log.e(TAG, "NavHostFragment is null. Check activity_main.xml.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up navigation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initViewModels() {
        try {
            // Initialize repositories
            AppDatabase database = AppDatabase.getDatabase(this);
            AssetRepository assetRepository = new AssetRepository(
                    database.assetDao(),
                    database.userDao(),
                    database.assetTransactionDao());
            UserRepository userRepository = new UserRepository(database.userDao());

            // Create ViewModel factory
            ViewModelFactory factory = new ViewModelFactory(assetRepository, userRepository);

            // Get ViewModels
            assetViewModel = new ViewModelProvider(this, factory).get(AssetViewModel.class);
            userViewModel = new ViewModelProvider(this, factory).get(UserViewModel.class);

            // Observe authentication state
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser != null) {
                // Load data if user is already signed in
                refreshUserData();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ViewModels: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Refresh user data from repositories
     */
    private void refreshUserData() {
        // Load assets with a slight delay to ensure database is ready
        binding.getRoot().postDelayed(() -> {
            assetViewModel.loadAssets();
            userViewModel.refreshCurrentUser();
        }, 500);
    }

    private void setupAuthListener() {
        authListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                // User is signed in
                Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());

                // Refresh user data
                refreshUserData();

                // Navigate to home safely if we're on an auth screen
                if (navController != null && navController.getCurrentDestination() != null) {
                    int currentDestId = navController.getCurrentDestination().getId();
                    if (currentDestId == R.id.loginFragment || currentDestId == R.id.signUpFragment) {
                        // Navigate to home safely after a short delay
                        binding.getRoot().postDelayed(() -> {
                            try {
                                navController.navigate(R.id.homeFragment);
                            } catch (Exception e) {
                                Log.e(TAG, "Navigation error: " + e.getMessage());
                            }
                        }, 100);
                    }
                }
            } else {
                // User is signed out
                Log.d(TAG, "onAuthStateChanged:signed_out");

                // Only navigate to login if we're not already there and nav controller is ready
                if (navController != null && navController.getCurrentDestination() != null) {
                    int currentDestId = navController.getCurrentDestination().getId();
                    if (currentDestId != R.id.loginFragment && currentDestId != R.id.signUpFragment) {
                        try {
                            navController.navigate(R.id.loginFragment);
                        } catch (Exception e) {
                            Log.e(TAG, "Navigation error: " + e.getMessage());
                        }
                    }
                }
            }
        };
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }

    @Override
    protected void onStart() {
        super.onStart();
        auth.addAuthStateListener(authListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (authListener != null) {
            auth.removeAuthStateListener(authListener);
        }
    }
}