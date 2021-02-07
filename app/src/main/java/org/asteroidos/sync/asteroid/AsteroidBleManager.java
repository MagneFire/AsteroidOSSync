package org.asteroidos.sync.asteroid;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.asteroidos.sync.utils.AsteroidUUIDS;

import java.util.Objects;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.BuildConfig;
import no.nordicsemi.android.ble.data.Data;

public class AsteroidBleManager extends BleManager {

    @Nullable
    public BluetoothGattCharacteristic batteryCharacteristic;
    private BluetoothGattCharacteristic notificationUpdateCharacteristic;

    public AsteroidBleManager(@NonNull final Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected final BleManagerGattCallback getGattCallback() {
        return new AsteroidBleManagerGattCallback();
    }

    @Override
    public final void log(final int priority, @NonNull final String message) {
        if (BuildConfig.DEBUG || priority == Log.ERROR) {
            Log.println(priority, "MyBleManager", message);
        }
    }

    public final void abort() {
        cancelQueue();
    }


    @Override
    protected final void finalize() throws Throwable {
        super.finalize();
    }


    public final void setBatteryLevel(Data data) {
        System.out.println("DEBUG BATTERY: " + data.getByte(0) + "%");
        BatteryLevelEvent batteryLevelEvent = new BatteryLevelEvent();
        batteryLevelEvent.battery = Objects.requireNonNull(data.getByte(0)).intValue();
        //TODO Someting
    }

    public static class BatteryLevelEvent {
        public int battery = 0;
    }

    private class AsteroidBleManagerGattCallback extends BleManagerGattCallback {

        @Override
        public final boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
            final BluetoothGattService batteryService = gatt.getService(AsteroidUUIDS.BATTERY_SERVICE_UUID);
            final BluetoothGattService notificationService = gatt.getService(AsteroidUUIDS.NOTIFICATION_SERVICE_UUID);

            boolean supported = true;

            boolean notify = false;
            if (batteryService != null) {
                batteryCharacteristic = batteryService.getCharacteristic(AsteroidUUIDS.BATTERY_UUID);

                if (batteryCharacteristic != null) {
                    final int properties = batteryCharacteristic.getProperties();
                    notify = (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
                }
            }
            supported = (batteryCharacteristic != null && notify);

            if (notificationService != null) {
                notificationUpdateCharacteristic = notificationService.getCharacteristic(AsteroidUUIDS.NOTIFICATION_UPDATE_CHAR);

            }
            supported &= (notificationUpdateCharacteristic != null && notify);

            // Return true if all required services have been found
            return supported;
        }

        @Override
        protected final boolean isOptionalServiceSupported(@NonNull final BluetoothGatt gatt) {
            return super.isOptionalServiceSupported(gatt);
        }

        @Override
        protected final void initialize() {
            beginAtomicRequestQueue()
                    .add(requestMtu(256) // Remember, GATT needs 3 bytes extra. This will allow packet size of 244 bytes.
                            .with((device, mtu) -> log(Log.INFO, "MTU set to " + mtu))
                            .fail((device, status) -> log(Log.WARN, "Requested MTU not supported: " + status)))
                    .done(device -> log(Log.INFO, "Target initialized"))
                    .fail((device, status) -> Log.e("Init", device.getName() + " not initialized with error: " + status))
                    .enqueue();

            setNotificationCallback(batteryCharacteristic).with(((device, data) -> setBatteryLevel(data)));
            readCharacteristic(batteryCharacteristic).with(((device, data) -> setBatteryLevel(data)));
            enableNotifications(batteryCharacteristic).enqueue();

        }

        @Override
        protected final void onDeviceDisconnected() {
            batteryCharacteristic = null;
        }

    }

}