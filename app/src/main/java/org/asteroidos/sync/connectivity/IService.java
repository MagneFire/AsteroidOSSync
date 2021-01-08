package org.asteroidos.sync.connectivity;

/**
 * Every Service of Sync has to implement the {@link IService} interface.
 * A service is a module that either processes information that is exchanged
 * with the watch or is interested in knowing when the watch is connected and
 * when the watch is disconnected,
 * ({@link IService#sync()}) is called when the watch is connected.
 * ({@link IService#unsync()}) is called when the watch is disconnected.
 */
public interface IService {

    /**
     * <pre>
     * &#064;Override
     * <b>public void</b> sync() {
     *     // watch is connected
     *     // your code goes here
     * }
     * </pre>
     */
    public void sync();

    /**
     * <pre>
     * &#064;Override
     * <b>public void</b> unsync() {
     *     // watch is disconnected
     *     // your code goes here
     * }
     * </pre>
     */
    public void unsync();

}
