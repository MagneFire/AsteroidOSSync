package org.asteroidos.sync.asteroid;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.asteroidos.sync.ble.IBleService;
import org.asteroidos.sync.services.SynchronizationService;
import org.asteroidos.sync.utils.AsteroidUUIDS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.BuildConfig;
import no.nordicsemi.android.ble.data.Data;

public class AsteroidBleManager extends BleManager {
    SynchronizationService synchronizationService;
    public static final String TAG = AsteroidBleManager.class.toString();


    @Nullable
    public BluetoothGattCharacteristic batteryCharacteristic;
    private BluetoothGattCharacteristic notificationUpdateCharacteristic;
    HashMap<BluetoothGattCharacteristic, IBleService.Direction> gattChars;
    ArrayList<BluetoothGattService> gattServices;

    public AsteroidBleManager(@NonNull final Context context, SynchronizationService syncService) {
        super(context);
        synchronizationService = syncService;
        gattServices = new ArrayList<>();
        gattChars = new HashMap<>();
    }

    public void send(UUID characteristic, byte[] data) {
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
        BatteryLevelEvent batteryLevelEvent = new BatteryLevelEvent();
        batteryLevelEvent.battery = Objects.requireNonNull(data.getByte(0)).intValue();
        synchronizationService.handleBattery(batteryLevelEvent);
    }

    public final void informService(UUID uuid, byte[] data){
        synchronizationService.getServiceByUUID(uuid).onBleReceive(uuid, data);

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

            for (IBleService service : synchronizationService.getServices()) {

                BluetoothGattService bluetoothGattService = gatt.getService(service.getServiceUUID());
                HashMap<UUID, IBleService.Direction> characteristics = service.getCharacteristicUUIDs();

                if (characteristics == null)  {
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

            if (notificationService != null) {
                notificationUpdateCharacteristic = notificationService.getCharacteristic(AsteroidUUIDS.NOTIFICATION_UPDATE_CHAR);

            }
            supported &= (notificationUpdateCharacteristic != null && notify);
            Log.d(TAG, "FOUND? " + supported);

            // Return true if all required services have been found
            return supported;
        }

        /*@Override
        protected final boolean isOptionalServiceSupported(@NonNull final BluetoothGatt gatt) {
            return super.isOptionalServiceSupported(gatt);
        }*/

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

            /*gattChars.forEach((service, direction) -> {
                if (direction == IBleService.Direction.RX) {

                } else if (direction == IBleService.Direction.TX) {

                }
            });*/

            gattServices.forEach((service) -> {
            });
            // Let all services now that we are connected.
            synchronizationService.getServices().forEach((IBleService::sync));
        }

        @Override
        protected final void onDeviceDisconnected() {
            batteryCharacteristic = null;
            synchronizationService.getServices().forEach((IBleService::unsync));
        }

    }

}