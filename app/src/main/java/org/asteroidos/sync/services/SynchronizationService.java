/*
 * Copyright (C) 2016 - Florent Revest <revestflo@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.asteroidos.sync.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.asteroidos.sync.MainActivity;
import org.asteroidos.sync.R;
import org.asteroidos.sync.asteroid.AsteroidBleManager;
import org.asteroidos.sync.asteroid.IAsteroidDevice;
import org.asteroidos.sync.connectivity.IConnectivityService;
import org.asteroidos.sync.connectivity.IService;
import org.asteroidos.sync.connectivity.IServiceCallback;
import org.asteroidos.sync.connectivity.MediaService;
import org.asteroidos.sync.connectivity.NotificationService;
import org.asteroidos.sync.connectivity.ScreenshotService;
import org.asteroidos.sync.connectivity.SilentModeService;
import org.asteroidos.sync.connectivity.TimeService;
import org.asteroidos.sync.connectivity.WeatherService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import no.nordicsemi.android.ble.observer.ConnectionObserver;

public class SynchronizationService extends Service implements IAsteroidDevice, ConnectionObserver {
    public static final String TAG = SynchronizationService.class.toString();
    public static final int MSG_CONNECT = 1;
    public static final int MSG_DISCONNECT = 2;
    public static final int MSG_SET_LOCAL_NAME = 3;
    public static final int MSG_SET_STATUS = 4;
    public static final int MSG_SET_BATTERY_PERCENTAGE = 5;
    public static final int MSG_REQUEST_BATTERY_LIFE = 6;
    public static final int MSG_SET_DEVICE = 7;
    public static final int MSG_UPDATE = 8;
    public static final int MSG_UNSET_DEVICE = 9;
    public static final int STATUS_CONNECTED = 1;
    public static final int STATUS_DISCONNECTED = 2;
    public static final int STATUS_CONNECTING = 3;

    private static final String NOTIFICATION_CHANNEL_ID = "synchronizationservice_channel_id_01";
    final Messenger mMessenger = new Messenger(new SynchronizationHandler(this));
    private final int NOTIFICATION = 2725;
    public BluetoothDevice mDevice;
    public int batteryPercentage = 0;
    List<IConnectivityService> bleServices;
    List<IService> nonBleServices;
    private NotificationManager mNM;
    private int mState = STATUS_DISCONNECTED;
    private Messenger replyTo;
    private SharedPreferences mPrefs;
    private AsteroidBleManager mBleMngr;

    final void handleConnect() {
        if (mBleMngr == null) {
            mBleMngr = new AsteroidBleManager(getApplicationContext(), SynchronizationService.this);
            mBleMngr.setConnectionObserver(this);
        }
        if (mState == STATUS_CONNECTED || mState == STATUS_CONNECTING) return;

        mPrefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String defaultDevMacAddr = mPrefs.getString(MainActivity.PREFS_DEFAULT_MAC_ADDR, "");
        if (defaultDevMacAddr.equals("")) return;
        String defaultLocalName = mPrefs.getString(MainActivity.PREFS_DEFAULT_LOC_NAME, "");
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(defaultDevMacAddr);
        device.createBond();
        mBleMngr.connect(device)
                .useAutoConnect(true)
                .timeout(100000)
                .retry(3, 200)
                .done(device1 -> Log.d(TAG, "Connected to " + device1.getName()))
                .fail((device2, error) -> Log.e(TAG, "Failed to connect to " + device.getName() +
                        " with error code: " + error))
                .enqueue();
    }

    final void handleDisconnect() {
        if (mBleMngr == null) return;
        if (mState == STATUS_DISCONNECTED) return;

        bleServices.forEach(IService::unsync);
        mBleMngr.abort();
        mBleMngr.disconnect().enqueue();
    }

    final void handleSetDevice(BluetoothDevice device) {
        SharedPreferences.Editor editor = mPrefs.edit();
        Log.d(TAG, "handleSetDevice: " + device.toString());
        editor.putString(MainActivity.PREFS_DEFAULT_MAC_ADDR, device.getAddress());
        mDevice = device;
        String name = mDevice.getName();
        try {
            Message answer = Message.obtain(null, MSG_SET_LOCAL_NAME);
            answer.obj = name;
            replyTo.send(answer);
            replyTo.send(Message.obtain(null, MSG_SET_STATUS, mState, 0));
        } catch (RemoteException | NullPointerException ignored) {
        }
        editor.putString(MainActivity.PREFS_DEFAULT_LOC_NAME, name);
        editor.apply();
    }

    final void handleUpdate() {
        if (mDevice != null) {
            try {
                replyTo.send(Message.obtain(null, MSG_SET_STATUS, mState, 0));
            } catch (RemoteException | NullPointerException ignored) {
            }
        }
    }

    final public void unsyncServices() {
        bleServices.forEach(IService::unsync);
        nonBleServices.forEach(IService::unsync);
    }

    final public void syncServices() {
        bleServices.forEach(IService::sync);
        nonBleServices.forEach(IService::sync);
    }

    @Override
    public final ConnectionState getConnectionState() {
        ConnectionState state = null;
        switch (mState) {
            case (STATUS_CONNECTED):
                state = ConnectionState.STATUS_CONNECTED;
                break;
            case (STATUS_CONNECTING):
                state = ConnectionState.STATUS_CONNECTING;
                break;
            case (STATUS_DISCONNECTED):
                state = ConnectionState.STATUS_DISCONNECTED;
                break;
        }
        return state;
    }

    @Override
    public final void send(UUID characteristic, byte[] data, IConnectivityService service) {
        mBleMngr.send(characteristic, data);
        Log.d(characteristic.toString(), Arrays.toString(data));
    }

    @Override
    public final void registerBleService(IConnectivityService service) {
        boolean success = bleServices.add(service);
        mBleMngr.refresh();
        Log.d(TAG, "BLE Service registered: " + success + service.getServiceUUID());
    }

    @Override
    public final void unregisterBleService(UUID serviceUUID) {
        for (IConnectivityService service : bleServices) {
            if (service.getServiceUUID().equals(serviceUUID)) {
                bleServices.remove(service);
                Log.d(TAG, "BLE Service unregistered: " + service.getServiceUUID());
            }
        }

    }

    @Override
    public final void registerCallback(UUID characteristicUUID, IServiceCallback callback) {
        mBleMngr.recvCallbacks.put(characteristicUUID, callback);
    }

    @Override
    public final void unregisterCallback(UUID characteristicUUID) {
        mBleMngr.recvCallbacks.remove(characteristicUUID);
    }

    @Override
    public final IConnectivityService getServiceByUUID(UUID uuid) {
        for (IConnectivityService service : bleServices) {
            if (service.getServiceUUID().equals(uuid)) {
                return service;
            } else {
                AtomicBoolean isService = new AtomicBoolean(false);
                service.getCharacteristicUUIDs().forEach((uuid1, direction) -> {
                    if (uuid1.equals(uuid)) {
                        isService.set(true);
                    }
                });
                if (isService.get())
                    return service;
            }
        }
        return null;
    }

    @Override
    public final List<IConnectivityService> getServices() {
        return bleServices;
    }

    @Override
    public final void onDeviceConnected(@NonNull BluetoothDevice device) {
        mState = STATUS_CONNECTED;
        updateNotification();
    }

    @Override
    public void onDeviceFailedToConnect(@NonNull BluetoothDevice device, int reason) {

    }

    @Override
    public final void onDeviceReady(@NonNull BluetoothDevice device) {
        mState = STATUS_CONNECTED;
        updateNotification();
        syncServices();
        AsteroidBleManager.BatteryLevelEvent bevent = new AsteroidBleManager.BatteryLevelEvent();
        bevent.battery = batteryPercentage;
        handleBattery(bevent);
    }

    @Override
    public final void onDeviceDisconnecting(@NonNull BluetoothDevice device) {
        mState = STATUS_CONNECTED;
        updateNotification();
    }

    @Override
    public final void onDeviceDisconnected(@NonNull BluetoothDevice device, int reason) {
        mState = STATUS_DISCONNECTED;
        updateNotification();
        unsyncServices();
    }

    @Override
    public void onCreate() {
        bleServices = new ArrayList<>();
        nonBleServices = new ArrayList<>();

        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Synchronization Service", NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setDescription("Connection status");
            notificationChannel.setVibrationPattern(new long[]{0L});
            notificationChannel.setShowBadge(false);
            mNM.createNotificationChannel(notificationChannel);
        }


        mPrefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String defaultDevMacAddr = mPrefs.getString(MainActivity.PREFS_DEFAULT_MAC_ADDR, "");
        String defaultLocalName = mPrefs.getString(MainActivity.PREFS_DEFAULT_LOC_NAME, "");

        if (mBleMngr == null) {
            mBleMngr = new AsteroidBleManager(getApplicationContext(), SynchronizationService.this);
            mBleMngr.setConnectionObserver(this);
        }

        if (!(defaultDevMacAddr.equals(""))) {
            mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(defaultDevMacAddr);
        }

        if (nonBleServices.isEmpty())
            nonBleServices.add(new SilentModeService(getApplicationContext()));

        if (bleServices.isEmpty()) {
            // Register Services
            new MediaService(getApplicationContext(), this);
            new NotificationService(getApplicationContext(), this);
            new WeatherService(getApplicationContext(), this);
            new ScreenshotService(getApplicationContext(), this);
            new TimeService(getApplicationContext(), this);
        }

        handleConnect();
        updateNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void updateNotification() {
        handleUpdate();
        String status = getString(R.string.disconnected);
        if (mDevice != null) {
            if (mState == STATUS_CONNECTING)
                status = getString(R.string.connecting_formatted, mDevice.getName());
            else if (mState == STATUS_CONNECTED)
                status = getString(R.string.connected_formatted, mDevice.getName());
        }

        if (mDevice != null) {
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentTitle(getText(R.string.app_name))
                    .setContentText(status)
                    .setContentIntent(contentIntent)
                    .setOngoing(true)
                    .setPriority(Notification.PRIORITY_MIN)
                    .setShowWhen(false)
                    .build();

            mNM.notify(NOTIFICATION, notification);
            startForeground(NOTIFICATION, notification);
        }
    }

    @Override
    public void onDestroy() {
        if (mDevice != null)
            mDevice = null;
        mBleMngr.disconnect();

        mNM.cancel(NOTIFICATION);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }


    @Override
    public void onDeviceConnecting(@NonNull BluetoothDevice device) {
        mState = STATUS_CONNECTING;
        updateNotification();
    }

    private void handleUnSetDevice() {
        SharedPreferences.Editor editor = mPrefs.edit();
        if (mState != STATUS_DISCONNECTED) {
            mBleMngr.disconnect().enqueue();
        }
        mDevice = null;
        editor.putString(MainActivity.PREFS_DEFAULT_LOC_NAME, "");
        editor.putString(MainActivity.PREFS_DEFAULT_MAC_ADDR, "");
        editor.putString(MainActivity.PREFS_NAME, "");
        editor.apply();
    }

    public void handleBattery(AsteroidBleManager.BatteryLevelEvent battery) {
        Log.d(TAG, "handleBattery: " + battery.battery + "%");
        batteryPercentage = battery.battery;
        try {
            if (replyTo != null)
                replyTo.send(Message.obtain(null, MSG_SET_BATTERY_PERCENTAGE, batteryPercentage, 0));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    static private class SynchronizationHandler extends Handler {
        private final SynchronizationService mService;

        SynchronizationHandler(SynchronizationService service) {
            mService = service;
        }

        @Override
        public void handleMessage(Message msg) {
            mService.replyTo = msg.replyTo;

            switch (msg.what) {
                case MSG_CONNECT:
                    mService.handleConnect();
                    break;
                case MSG_DISCONNECT:
                    mService.handleDisconnect();
                    break;
                case MSG_REQUEST_BATTERY_LIFE:
                    try {
                        mService.replyTo
                                .send(Message.obtain(null, MSG_SET_BATTERY_PERCENTAGE, mService.batteryPercentage, 0));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    mService.handleUpdate();
                    break;
                case MSG_SET_DEVICE:
                    mService.handleSetDevice((BluetoothDevice) msg.obj);
                    break;
                case MSG_UNSET_DEVICE:
                    mService.handleUnSetDevice();
                    break;
                case MSG_UPDATE:
                    mService.handleUpdate();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

}
