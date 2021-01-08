package org.asteroidos.sync.ble;

/**
 * Every BLE Service of Sync has to implement the {@link IService} interface.
 */
public interface IService {

    /**
     * <code>sync()</code> is called on service creation
     *
     * <pre>
     * &#064;Override
     * <b>public void</b> sync() {
     *     //init Service
     * }
     * </pre>
     */
    public void sync();

    /**
     * <code>unsync()</code> is called before a service is destroyed
     *
     * <pre>
     * &#064;Override
     * <b>public void</b> unsync() {
     *     //destroy Service
     * }
     * </pre>
     */
    public void unsync();

}
