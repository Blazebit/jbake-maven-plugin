package com.blazebit.jbake.mojo.watcher;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Christian Beikov
 */
public class WatcherService extends Thread {

    private static final Logger LOG = Logger.getLogger(WatcherService.class.getName());
    private static final long DEFAULT_SLEEP = 100L;

    private final WatcherTimerService timerService = new WatcherTimerService();

    private final List<WatchDir> watchers = new CopyOnWriteArrayList<WatchDir>();
    private volatile boolean running = true;

    public void addListener(Path path, WatcherListener listener) {
        if (!running) {
            return;
        }

        WatchDir watchDir = null;
        try {
            watchDir = new WatchDir(timerService, path, listener, true, true);
            watchers.add(watchDir);
        } catch (IOException e) {
            logException(e.getCause());
            removeListener(path, listener);
        } finally {
            if (!running && watchDir != null) {
                watchers.remove(watchDir);
                close(watchDir);
            }
        }
    }

    public void removeListener(Path path, WatcherListener listener) {
        Iterator<WatchDir> iter = watchers.iterator();

        while (iter.hasNext()) {
            WatchDir watchDir = iter.next();

            if (path.equals(watchDir.getRootDir()) && listener.equals(watchDir.getListener())) {
                watchers.remove(watchDir);
                close(watchDir);
                break;
            }
        }
    }

    private void close(WatchDir watchDir) {
        watchDir.close();
    }

    public void init() {
        setName("WatcherService");
        start();
    }

    public void shutdown() {
        running = false;
        interrupt();

        Iterator<WatchDir> iter = watchers.iterator();

        while (iter.hasNext()) {
            WatchDir watchDir = iter.next();
            close(watchDir);
        }

        watchers.clear();
    }

    @Override
    public void run() {
        while (running) {
            try {
                sleep(DEFAULT_SLEEP);
                processEvents();
            } catch (InterruptedException e) {
                // Ignore these rare temporary event
            }
        }
    }

    public void processEvents() {
        try {
            for (WatchDir entry : watchers) {
                entry.processEvents();
            }
        } catch (ClosedWatchServiceException e) {
            // Ignore these rare temporary event
        }
    }

    private void logException(Throwable e) {
        LOG.log(Level.SEVERE, "An error occurred in the watcher service!", e);
    }
}
