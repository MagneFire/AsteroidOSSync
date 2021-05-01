package org.asteroidos.sync.asteroid;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.asteroidos.sync.connectivity.IConnectivityService;
import org.asteroidos.sync.services.SynchronizationService;
import org.asteroidos.sync.utils.AsteroidUUIDS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.BuildConfig;
import no.nordicsemi.android.ble.data.Data;

public class AsteroidBleManager extends BleManager {
    public static final String TAG = AsteroidBleManager.class.toString();
    @Nullable
    public BluetoothGattCharacteristic batteryCharacteristic;
    SynchronizationService synchronizationService;
    HashMap<BluetoothGattCharacteristic, IConnectivityService.Direction> gattChars;
    ArrayList<BluetoothGattService> gattServices;

    public AsteroidBleManager(@NonNull final Context context, SynchronizationService syncService) {
        super(context);
        synchronizationService = syncService;
        gattServices = new ArrayList<>();
        gattChars = new HashMap<>();
    }

    public final void send(UUID characteristic, byte[] data) {
        gattChars.forEach((service, direction) -> {
            if (service.getUuid().equals(characteristic)) {
                writeCharacteristic(service, data).enqueue();
            }
        });
    }

    @NonNull
    @Override
    protected final BleManagerGattCallback getGattCallback() {
        return new AsteroidBleManagerGattCallback();
    }

    @Override
    public final void log(final int priority, @NonNull final String message) {
        if (BuildConfig.DEBUG || priority == Log.ERROR) {
            Log.println(priority, TAG, message);
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
        BatteryLevelEvent batteryLevelEvent = new BatteryLevelEvent();
        batteryLevelEvent.battery = Objects.requireNonNull(data.getByte(0)).intValue();
        synchronizationService.handleBattery(batteryLevelEvent);
    }

    public final void informService(UUID uuid, byte[] data) {
        synchronizationService.getServiceByUUID(uuid).onReceive(uuid, data);
    }

    public static class BatteryLevelEvent {
        public int battery = 0;
    }

    private class AsteroidBleManagerGattCallback extends BleManagerGattCallback {

        @Override
        public final boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
            final BluetoothGattService batteryService = gatt.getService(AsteroidUUIDS.BATTERY_SERVICE_UUID);

            boolean supported = true;

            boolean notify = false;
            if (batteryService != null) {
                batteryCharacteristic = batteryService.getCharacteristic(AsteroidUUIDS.BATTERY_UUID);

                if (batteryCharacteristic != null) {
                    final int properties = batteryCharacteristic.getProperties();
                    notify = (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
                }
            }

            for (IConnectivityService service : synchronizationService.getServices()) {

                BluetoothGattService bluetoothGattService = gatt.getService(service.getServiceUUID());
                HashMap<UUID, IConnectivityService.Direction> characteristics = service.getCharacteristicUUIDs();

                if (characteristics == null) {
                    Log.d(TAG, "CHAR NULL! " + characteristics + " " + service.getServiceUUID());
                    continue;
                } else {
                    Log.d(TAG, "CHAR " + characteristics + " " + service.getServiceUUID());
                }

                characteristics.forEach((uuid, direction) -> {
                    BluetoothGattCharacteristic characteristic = bluetoothGattService.getCharacteristic(uuid);
                    gattChars.put(characteristic, direction);
                    bluetoothGattService.addCharacteristic(characteristic);
                });

                gattServices.add(bluetoothGattService);
            }

            supported = (batteryCharacteristic != null && notify);

            Log.d(TAG, "FOUND? " + supported);

            // Return true if all required services have been found
            return supported;
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
            readCharacteristic(batteryCharacteristic).with(((device, data) -> setBatteryLevel(data))).enqueue();
            enableNotifications(batteryCharacteristic).enqueue();

            gattChars.forEach((characteristic, direction) -> {
                System.out.println(characteristic.toString() + ": " + direction.name());
                if (direction == IConnectivityService.Direction.RX) {
                    setNotificationCallback(characteristic).with((device, data)
                            -> informService(characteristic.getUuid(), data.getValue()));

                    //sometimes crashes with null pointer or sends random data
                    enableNotifications(characteristic).with((device, data)
                            -> informService(characteristic.getUuid(), data.getValue())).enqueue();
                } else if (direction == IConnectivityService.Direction.TX) {

                }
            });

            // Let all services now that we are connected.
            try {
                synchronizationService.syncServices();
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        @Override
        protected final void onDeviceDisconnected() {
            synchronizationService.unsyncServices();
            batteryCharacteristic = null;
            gattServices.clear();
            gattChars.clear();
            gattServices.clear();
            gattChars.clear();
        }
    }
}