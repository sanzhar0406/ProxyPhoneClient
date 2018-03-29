package com.example.sanzharaubakir.localvpn;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity  {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";
    private static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // for port number check
        //IpcService.Initialize(this);

        final TextView serverAddress = (TextView) findViewById(R.id.address);
        final TextView serverPort = (TextView) findViewById(R.id.port);
        final TextView sharedSecret = (TextView) findViewById(R.id.secret);

        final SharedPreferences prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        serverAddress.setText(prefs.getString(Prefs.SERVER_ADDRESS, ""));
        serverPort.setText(prefs.getString(Prefs.SERVER_PORT, ""));
        sharedSecret.setText(prefs.getString(Prefs.SHARED_SECRET, ""));

        findViewById(R.id.connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.edit()
                        .putString(Prefs.SERVER_ADDRESS, serverAddress.getText().toString())
                        .putString(Prefs.SERVER_PORT, serverPort.getText().toString())
                        .putString(Prefs.SHARED_SECRET, sharedSecret.getText().toString())
                        .apply();
                if (!isEnabled()){
                    startActivity(new Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS));
                }
                Intent intent = new Intent(MainActivity.this, NotificationsMonitoringService.class);
                ServiceConnection connection = new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                        Log.d(TAG, "Service connected");
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName componentName) {
                        Log.d(TAG, "Service disconnected");
                    }
                };
                startService(intent);
                bindService(intent, connection, BIND_AUTO_CREATE);

                intent = VpnService.prepare(MainActivity.this);
                if (intent != null) {
                    Log.d(TAG, "intent is null");
                    startActivityForResult(intent, 0);
                } else {
                    Log.d(TAG, "on activity result");
                    onActivityResult(0, RESULT_OK, null);
                }
            }
        });
        findViewById(R.id.disconnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                startService(getServiceIntent().setAction(LocalVPNService.ACTION_DISCONNECT));
                //IpcService.getInstance().forceExit();
                //stopService(new Intent(MainActivity.this, OSMonitorService.class));
                //stopService(new Intent(MainActivity.this, NotiMonitorService.class));
                //stopService(new Intent(MainActivity.this, ScreenMonitorService.class));
            }
        });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (result == RESULT_OK) {
            Log.d(TAG, "starting service");
            startService(getServiceIntent().setAction(LocalVPNService.ACTION_CONNECT));
            //if (!CoreUtil.isServiceRunning(this))
            //    startService(new Intent(this, OSMonitorService.class));

            //startService(new Intent(MainActivity.this, NotiMonitorService.class));
            //startService(new Intent(MainActivity.this, ScreenMonitorService.class));
        }
    }

    private Intent getServiceIntent() {
        return new Intent(this, LocalVPNService.class);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isFinishing()) {
            //IpcService.getInstance().disconnect();
            //unregisterReceiver(nReceiver,filter);
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
    private boolean isEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(),
                ENABLED_NOTIFICATION_LISTENERS);
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (int i = 0; i < names.length; i++) {
                final ComponentName cn = ComponentName.unflattenFromString(names[i]);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
