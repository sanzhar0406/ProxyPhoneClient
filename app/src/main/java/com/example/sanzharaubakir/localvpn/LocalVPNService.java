package com.example.sanzharaubakir.localvpn;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by sanzharaubakir on 26.03.18.
 */

public class LocalVPNService extends VpnService implements Handler.Callback{
    private static final String TAG = LocalVPNService.class.getSimpleName();

    public static final String ACTION_CONNECT = "com.example.android.toyvpn.START";
    public static final String ACTION_DISCONNECT = "com.example.android.toyvpn.STOP";
    public static final String ACTION_ENABLE_APPEND_PACKAGE = "com.example.android.toyvpn.ENABLE_APPEND_PACKAGE";
    public static final String ACTION_DISABLE_APPEND_PACKAGE = "com.example.android.toyvpn.DISABLE_APPEND_PACKAGE";

    private Handler mHandler;

    public static boolean appendPackageName = false;

    private static class Connection extends Pair<Thread, ParcelFileDescriptor> {
        public Connection(Thread thread, ParcelFileDescriptor pfd) {
            super(thread, pfd);
        }
    }

    private final AtomicReference<Thread> mConnectingThread = new AtomicReference<>(); // for multithreading
    private final AtomicReference<Connection> mConnection = new AtomicReference<>();

    private AtomicInteger mNextConnectionId = new AtomicInteger(1);

    private PendingIntent mConfigureIntent;

    @Override
    public void onCreate() {
        Log.d(TAG, "OnCreate");
        // The handler is only used to show messages.
        if (mHandler == null) {
            mHandler = new Handler(this);
        }

        // Create the intent to "configure" the connection (just start ToyVpnClient).
        mConfigureIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "action - " + intent.getAction());
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnect();
            return START_NOT_STICKY;
        } else if (ACTION_CONNECT.equals(intent.getAction())) {
            connect();
            return START_STICKY;
        } else if (ACTION_ENABLE_APPEND_PACKAGE.equals(intent.getAction())) {
            return START_STICKY;
        } else if (ACTION_DISABLE_APPEND_PACKAGE.equals(intent.getAction())) {
            return START_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        disconnect();
    }

    @Override
    public boolean handleMessage(Message message) {
        Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
        if (message.what != R.string.disconnected) {
            updateForegroundNotification(message.what);
        }
        return true;
    }

    private void connect() {
        // Become a foreground service. Background services can be VPN services too, but they can
        // be killed by background check before getting a chance to receive onRevoke().
        updateForegroundNotification(R.string.connecting);
        mHandler.sendEmptyMessage(R.string.connecting);
        Log.d(TAG, "connect function()");
        // Extract information from the shared preferences.
        final SharedPreferences prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        final String server = prefs.getString(Prefs.SERVER_ADDRESS, "");
        final byte[] secret = prefs.getString(Prefs.SHARED_SECRET, "").getBytes();
        final int port;
        try {
            port = Integer.parseInt(prefs.getString(Prefs.SERVER_PORT, ""));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Bad port: " + prefs.getString(Prefs.SERVER_PORT, null), e);
            return;
        }

        // Kick off a connection.
        startConnection(new LocalVpnConnection(
                this, mNextConnectionId.getAndIncrement(), server, port, secret));
    }

    private void startConnection(final LocalVpnConnection connection) {
        // Replace any existing connecting thread with the  new one.
        final Thread thread = new Thread(connection, "LocalVpnThread");
        setConnectingThread(thread);

        // Handler to mark as connected once onEstablish is called.
        connection.setConfigureIntent(mConfigureIntent);
        connection.setOnEstablishListener(new LocalVpnConnection.OnEstablishListener() {
            public void onEstablish(ParcelFileDescriptor tunInterface) {
                mHandler.sendEmptyMessage(R.string.connected);

                mConnectingThread.compareAndSet(thread, null);
                setConnection(new Connection(thread, tunInterface));
            }
        });
        thread.start();
    }

    private void setConnectingThread(final Thread thread) {
        final Thread oldThread = mConnectingThread.getAndSet(thread);
        if (oldThread != null) {
            oldThread.interrupt();
        }
    }

    private void setConnection(final Connection connection) {
        final Connection oldConnection = mConnection.getAndSet(connection);
        if (oldConnection != null) {
            try {
                oldConnection.first.interrupt();
                oldConnection.second.close();
            } catch (IOException e) {
                Log.e(TAG, "Closing VPN interface", e);
            }
        }
    }

    private void disconnect() {
        mHandler.sendEmptyMessage(R.string.disconnected);
        setConnectingThread(null);
        setConnection(null);
        stopForeground(true);
    }

    private void updateForegroundNotification(final int message) {
        startForeground(1, new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentText(getString(message))
                .setContentIntent(mConfigureIntent)
                .build());
    }
}
