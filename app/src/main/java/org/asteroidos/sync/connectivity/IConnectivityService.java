package org.asteroidos.sync.connectivity;

import java.util.HashMap;
import java.util.UUID;

/**
 * A connectivity service is a module that can exchange data with the watch. It has to implement {@link IService}
 * and additional functions regarding connectivity from {@link IConnectivityService}.
 */
public interface IConnectivityService extends IService {

    enum Direction{
        RX,
        TX
    }

    public HashMap<UUID, Direction> getCharacteristicUUIDs();

    public UUID getServiceUUID();

    /**
     * <code>onReceive()</code> is called when data from the watch is received.
     *
     * <pre>
     * &#064;Override
     * <b>public void</b> onReceive(UUID uuid, byte[] data) {
     *     //handle data
     * }
     * </pre>
     *
     * @param uuid {@link UUID} from {@link org.asteroidos.sync.utils.AsteroidUUIDS}
     * @param data payload for the {@link IConnectivityService}
     *
     * @return <code>true</code> if the data was processed by the {@link IConnectivityService},
     *     <code>false</code> if the data was intentionally not used (e.g. the {@link IConnectivityService} does not implement
     *     a method to process the data)
     */
    public Boolean onReceive(UUID uuid, byte[] data);
}
