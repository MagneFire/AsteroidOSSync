package org.asteroidos.sync.asteroid;

import org.asteroidos.sync.ble.IBleService;

import java.util.UUID;

public interface IAsteroidDevice {
    public String name = "";
    public String macAddress = "";
    public int batteryPercentage = 0;
    public boolean bonded = false;

    public enum ConnectionState {
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
     * //determine connection state
     * //return {@link ConnectionState}
     * }
     * </pre>
     *
     * @return Current state of the {@link IAsteroidDevice} connection.
     */
    public ConnectionState getConnectionState();

    public void sendToDevice(UUID characteristic, byte[] data, IBleService service);

}
