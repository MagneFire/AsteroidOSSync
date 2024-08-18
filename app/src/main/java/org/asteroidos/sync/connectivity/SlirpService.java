/*
 * AsteroidOSSync
 * Copyright (c) 2023 AsteroidOS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.asteroidos.sync.connectivity;

import android.content.Context;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import org.asteroidos.sync.asteroid.IAsteroidDevice;
import org.asteroidos.sync.utils.AsteroidUUIDS;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SlirpService implements IConnectivityService {

    private final IAsteroidDevice mDevice;

    private final Context mCtx;

    private volatile int mtu;

    private final ByteBuffer rx = ByteBuffer.allocateDirect(1500);

    private final ByteBuffer tx = ByteBuffer.allocateDirect(1500);
    final Object lock = new Object();

    public SlirpService(Context ctx, IAsteroidDevice device) {
        mDevice = device;
        mCtx = ctx;

        Thread slirpThread = new Thread(this::slirpThread);

        mtu = mDevice.getMtu();

        startNative(mtu - 3);

        mDevice.registerCallback(AsteroidUUIDS.SLIRP_INCOMING_CHAR, data -> {
//            resetMtu();
//            Log.d("SlirpService", "Sending (BLE -> slirp) " + data.length + " bytes");

            synchronized (lock) {
                tx.clear();
                tx.put(data);
                vdeSend(tx, 0, data.length);
            }
        });

        slirpThread.start();
    }

    private void slirpThread() {
        FileDescriptor fd = getVdeFd();
        StructPollfd pollfd = new StructPollfd();
        pollfd.fd = fd;
        pollfd.events = (short) OsConstants.POLLIN;
        StructPollfd[] pollfds = new StructPollfd[]{pollfd};
        while (true) {
            try {
                if (Os.poll(pollfds, 1500) == 0) {
                    continue;
                }

                synchronized (lock) {
                    rx.clear();
                    long read = vdeRecv(rx, 0, mtu - 3);
                    assert read <= (mtu - 3);
                    if (read > 0) {
                        byte[] data = new byte[(int) read];
                        rx.get(data);
                        mDevice.send(AsteroidUUIDS.SLIRP_OUTGOING_CHAR, data, SlirpService.this);
                    } else {
                        Log.e("SlirpService", "Read error: " + read);
                    }
                }
            } catch (Exception e) {
                Log.e("SlirpService", "Poller exception", e);
            }
        }
    }

    private void startNative(int mtu) {
        initNative(mtu - 14);

        vdeAddFwd(false, "0.0.0.0", 45722, "10.0.2.3", 22);
        vdeAddFwd(false, "0.0.0.0", 45723, "10.0.2.3", 23);
        vdeAddFwd(false, "0.0.0.0", 55555, "10.0.2.3", 55555);
        vdeAddFwd(false, "0.0.0.0", 55556, "10.0.2.3", 55556);
    }

    private void resetMtu() {
        int newMtu = mDevice.getMtu();
        if (mtu != newMtu) {
            Log.d("SlirpService", "MTU updating " + mtu + " -> " + newMtu);
            synchronized (lock)  {
                finalizeNative();
                startNative(mtu - 3);

                mtu = newMtu;
            }
            Log.d("SlirpService", "MTU updated");
        }
    }

    @Override
    public void sync() {
    }

    @Override
    public void unsync() {
    }

    @Override
    public HashMap<UUID, Direction> getCharacteristicUUIDs() {
        HashMap<UUID, Direction> chars = new HashMap<>();
        chars.put(AsteroidUUIDS.SLIRP_OUTGOING_CHAR, Direction.TO_WATCH);
        chars.put(AsteroidUUIDS.SLIRP_INCOMING_CHAR, Direction.FROM_WATCH);
        return chars;
    }

    @Override
    protected void finalize() throws Throwable {
        finalizeNative();
        super.finalize();
    }

    static {
        System.loadLibrary("sync");
    }

    @Override
    public UUID getServiceUUID() {
        return AsteroidUUIDS.SLIRP_SERVICE_UUID;
    }

    @SuppressWarnings({"unused", "FieldMayBeFinal"}) // used internally by the JNI part
    private long mySlirp = 0;

    private native int vdeAddUnixFwd(String path, String ip, int port);

    private native int vdeAddFwd(boolean udp, String hostip, int hostport, String ip, int port);

    private native void initNative(int mtu);

    private native void finalizeNative();

    private native long vdeRecv(ByteBuffer buffer, long offset, long count);

    private native long vdeSend(ByteBuffer buffer, long offset, long count);

    private native FileDescriptor getVdeFd();
}
