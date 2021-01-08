package org.asteroidos.sync.ble;

/**
 * Every Service of Sync has to implement the {@link IService} interface.
 * A service is a module that can be loaded ({@link IService#sync()}) after the connection is established and
 * will get destroyed ({@link IService#unsync()}) when the connection is terminated.
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
