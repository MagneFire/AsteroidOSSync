package org.asteroidos.sync.asteroid;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.asteroidos.sync.connectivity.IConnectivityService;
import org.asteroidos.sync.connectivity.IServiceCallback;
import org.asteroidos.sync.services.SynchronizationService;
import org.asteroidos.sync.utils.AsteroidUUIDS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.data.Data;

public class AsteroidBleManager extends BleManager {
    public static final String TAG = AsteroidBleManager.class.toString();
    @Nullable
    public BluetoothGattCharacteristic batteryCharacteristic;
    SynchronizationService synchronizationService;
    HashMap<BluetoothGattCharacteristic, IConnectivityService.Direction> gattChars;
    ArrayList<BluetoothGattService> gattServices;
    public HashMap<UUID, IServiceCallback> recvCallbacks;
    public HashMap<UUID, BluetoothGattCharacteristic> sendingCharacteristics;

    public AsteroidBleManager(@NonNull final Context context, SynchronizationService syncService) {
        super(context);
        synchronizationService = syncService;
        gattServices = new ArrayList<>();
        recvCallbacks = new HashMap<>();
    }

    public final void send(UUID characteristic, byte[] data) {
        writeCharacteristic(sendingCharacteristics.get(characteristic), data).enqueue();
    }

    public final void refresh() {
        recvCallbacks.forEach((characteristic, callback) -> {
            Log.d("REGISTERING", characteristic.toString() + " " + callback.toString());
            BluetoothGattCharacteristic characteristic1 = new BluetoothGattCharacteristic(characteristic, BluetoothGattCharacteristic.PROPERTY_NOTIFY,BluetoothGattCharacteristic.PERMISSION_READ);
            setNotificationCallback(characteristic1).with((device, data) -> callback.call(data.getValue()));
            enableNotifications(characteristic1).with((device, data) -> callback.call(data.getValue())).enqueue();
        });

    }

    @NonNull
    @Override
    protected final BleManagerGattCallback getGattCallback() {
        return new AsteroidBleManagerGattCallback();
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
            if(sendingCharacteristics == null)
                sendingCharacteristics = new HashMap<>();

            for (IConnectivityService service : synchronizationService.getServices()) {

                BluetoothGattService bluetoothGattService = gatt.getService(service.getServiceUUID());
                List<UUID> sendUuids = new ArrayList<>();
                service.getCharacteristicUUIDs().forEach((uuid, direction) -> {
                    if (direction == IConnectivityService.Direction.TO_WATCH)
                        sendUuids.add(uuid);
                    else
                       recvCallbacks.forEach((uuid1, callback) -> recvCallbacks.put(uuid1, callback));
                });
                System.out.println(sendUuids.toString());

                for (UUID uuid: sendUuids) {
                    BluetoothGattCharacteristic characteristic = bluetoothGattService.getCharacteristic(uuid);
                    sendingCharacteristics.put(uuid, characteristic);
                    bluetoothGattService.addCharacteristic(characteristic);
                }
            }

            supported = (batteryCharacteristic != null && notify);

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

            // Let all services know that we are connected.
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