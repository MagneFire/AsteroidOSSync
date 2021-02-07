package org.asteroidos.sync.ble;

import java.util.HashMap;
import java.util.UUID;

/**
 * A BLE service is a module that can exchange data with the watch. It has to implement {@link IService}
 * and the additional BLE functionality from {@link IBleService}.
 */
public interface IBleService extends IService {

    enum Direction{
        RX,
        TX
    }

    public HashMap<UUID, Direction> getCharacteristicUUIDs();

    public UUID getServiceUUID();

    /**
     * <code>onBleReceive()</code> is called before a service is destroyed
     *
     * <pre>
     * &#064;Override
     * <b>public void</b> onReceive(UUID uuid, byte[] data) {
     *     //destroy Service
     * }
     * </pre>
     *
     * @param uuid {@link UUID} from {@link org.asteroidos.sync.utils.AsteroidUUIDS}
     * @param data payload for the {@link IBleService}
     *
     * @return <code>true</code> if the data was processed by the {@link IBleService},
     *     <code>false</code> if the data was intentionally not used (e.g. the {@link IBleService} does not implement
     *     a method to process the data)
     */
    public Boolean onBleReceive(UUID uuid, byte[] data);
}
