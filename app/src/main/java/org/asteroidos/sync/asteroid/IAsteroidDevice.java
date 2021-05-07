package org.asteroidos.sync.asteroid;

import org.asteroidos.sync.connectivity.IConnectivityService;
import org.asteroidos.sync.connectivity.IServiceCallback;

import java.util.List;
import java.util.UUID;

public interface IAsteroidDevice {
    String name = "";
    String macAddress = "";
    int batteryPercentage = 0;
    boolean bonded = false;

    enum ConnectionState {
        STATUS_CONNECTED,
        STATUS_CONNECTING,
        STATUS_DISCONNECTED
    }

    /**
     * Can be used to determine if an {@link IAsteroidDevice} is {@link IAsteroidDevice.ConnectionState#STATUS_CONNECTED},
     * {@link IAsteroidDevice.ConnectionState#STATUS_CONNECTING} or {@link IAsteroidDevice.ConnectionState#STATUS_DISCONNECTED}.
     *
     * <pre>
     * &#064;Override
     * <b>public ConnectionState</b> getConnectionState() {
     * // determine connection state
     * // return {@link ConnectionState}
     * }
     * </pre>
     *
     * @return Current state of the {@link IAsteroidDevice} connection.
     */
    ConnectionState getConnectionState();

    void send(UUID characteristic, byte[] data, IConnectivityService service);

    void registerBleService(IConnectivityService service);
    void unregisterBleService(UUID serviceUUID);
    void registerCallback(UUID characteristicUUID, IServiceCallback callback);
    void unregisterCallback(UUID characteristicUUID);


    IConnectivityService getServiceByUUID(UUID uuid);
    List<IConnectivityService> getServices();
}
