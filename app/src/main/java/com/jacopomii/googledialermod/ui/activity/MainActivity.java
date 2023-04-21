package com.jacopomii.googledialermod.ui.activity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;
import com.jacopomii.googledialermod.ICoreRootService;
import com.jacopomii.googledialermod.R;
import com.jacopomii.googledialermod.databinding.ActivityMainBinding;
import com.jacopomii.googledialermod.service.CoreRootService;
import com.topjohnwu.superuser.ipc.RootService;
import com.topjohnwu.superuser.nio.FileSystemManager;

public class MainActivity extends AppCompatActivity {
    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding mBinding;

    private boolean mCoreRootServiceBound = false;
    private ServiceConnection mCoreRootServiceConnection;
    private ICoreRootService mCoreRootServiceIpc;
    private FileSystemManager mCoreRootServiceFSManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The savedInstanceState must not be used, otherwise the views (and the fragments contained
        // by this activity) are restored before the RootService is started, causing NPE.
        super.onCreate(null);

        // Start CoreRootService connection
        Intent intent = new Intent(this, CoreRootService.class);
        mCoreRootServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                try {
                    // Set references to the remote coreRootService
                    mCoreRootServiceBound = true;
                    mCoreRootServiceIpc = ICoreRootService.Stub.asInterface(service);
                    mCoreRootServiceFSManager = FileSystemManager.getRemote(mCoreRootServiceIpc.getFileSystemService());

                    // Update UI
                    mBinding = ActivityMainBinding.inflate(getLayoutInflater());
                    setContentView(mBinding.getRoot());

                    setSupportActionBar(mBinding.toolbar);

                    DrawerLayout drawer = mBinding.drawerLayout;
                    NavigationView navigationView = mBinding.navView;
                    mAppBarConfiguration = new AppBarConfiguration.Builder(
                            R.id.nav_suggested_mods,
                            R.id.nav_boolean_mods,
                            R.id.nav_revert_mods,
                            R.id.nav_information
                    ).setOpenableLayout(drawer).build();

                    NavController navController = Navigation.findNavController(MainActivity.this, R.id.nav_host_fragment);
                    NavigationUI.setupActionBarWithNavController(MainActivity.this, navController, mAppBarConfiguration);
                    NavigationUI.setupWithNavController(navigationView, navController);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mCoreRootServiceBound = false;
                mCoreRootServiceIpc = null;
                mCoreRootServiceFSManager = null;
            }
        };
        RootService.bind(intent, mCoreRootServiceConnection);
    }

    public FileSystemManager getCoreRootServiceFSManager() {
        return mCoreRootServiceFSManager;
    }

    public ICoreRootService getCoreRootServiceIpc() {
        return mCoreRootServiceIpc;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void onBackPressed() {
        if (mBinding.drawerLayout.isOpen())
            mBinding.drawerLayout.close();
        else
            finishAffinity();
    }

    @Override
    protected void onDestroy() {
        if (mCoreRootServiceBound)
            RootService.unbind(mCoreRootServiceConnection);
        super.onDestroy();
    }
}