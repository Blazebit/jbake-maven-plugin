/*
 * Copyright 2016 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blazebit.jbake.mojo.watcher;

import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author Christian Beikov
 */
public class WatcherTimerService {

    private static final Logger LOG = Logger.getLogger(WatcherTimerService.class.getName());
    private static final long DEFAULT_TIMEOUT = 400L;

    private final Timer timer = new Timer("WatcherTimerService");
    private final ConcurrentMap<WatchDir, WatcherTimerTask> queuedRefreshTasks = new ConcurrentHashMap<WatchDir, WatcherTimerTask>();
    private volatile boolean running = true;

    public void shutdown() {
        running = false;
        timer.cancel();

        Iterator<WatcherTimerTask> iter = queuedRefreshTasks.values().iterator();

        while (iter.hasNext()) {
            WatcherTimerTask task = iter.next();
            task.await();
        }

        queuedRefreshTasks.clear();
    }

    /**
     * Queues a refresh. Re-queues unscheduled existing refreshes if possible.
     *
     * @param watchDir
     * @param listener
     */
    public void queue(WatchDir watchDir, WatcherListener listener) {
        if (!running) {
            return;
        }
        final WatcherTimerTask task = new WatcherTimerTask(watchDir, listener);
        final WatcherTimerTask previousTask = queuedRefreshTasks.putIfAbsent(watchDir, task);
        if (previousTask == null) {
            LOG.finest("Scheduled refresh");
            timer.schedule(task, DEFAULT_TIMEOUT);
        } else {
            // Try rescheduling the previous task
            if (previousTask.cancel()) {
                LOG.finest("Canceled and rescheduled refresh");
                // Queued task is canceled and then rescheduled
                timer.schedule(task, DEFAULT_TIMEOUT);
            } else {
                // If not successful, schedule this task
                LOG.finest("Additionally scheduled refresh");
                if (!queuedRefreshTasks.replace(watchDir, previousTask, task)) {
                    // Since queuing is single-threaded this replace must always succeed, but just to be safe
                    queuedRefreshTasks.put(watchDir, task);
                }
                // Since the timer is single-threaded there is no need for a lock per WatchDir
                timer.schedule(task, DEFAULT_TIMEOUT);
            }
        }
    }

    /**
     * Re-queues a refresh or awaits it.
     *
     * @param watchDir
     * @return true if queued refresh was requeued, false if none existed
     */
    public boolean requeue(WatchDir watchDir, WatcherListener listener) {
        if (!running) {
            return false;
        }

        final WatcherTimerTask task = queuedRefreshTasks.get(watchDir);
        if (task == null) {
            return false;
        }

        if (task.cancel()) {
            LOG.finest("Requeued refresh");
            // Queued task is canceled and then rescheduled
            final WatcherTimerTask newTask = new WatcherTimerTask(watchDir, listener);
            if (!queuedRefreshTasks.replace(watchDir, task, newTask)) {
                // Since queuing is single-threaded this replace must always succeed, but just to be safe
                queuedRefreshTasks.put(watchDir, newTask);
            }
            timer.schedule(newTask, DEFAULT_TIMEOUT);
            return true;
        }

        LOG.finest("Awaiting refresh");
        task.await();
        LOG.finest("Awaited refresh");
        return false;
    }

    private class WatcherTimerTask extends TimerTask {

        private final WatchDir watchDir;
        private final WatcherListener listener;
        private final Object lock = new Object();

        public WatcherTimerTask(WatchDir watchDir, WatcherListener listener) {
            this.watchDir = watchDir;
            this.listener = listener;
        }

        @Override
        public boolean cancel() {
            boolean prevented = super.cancel();
            return prevented;
        }

        @Override
        public void run() {
            synchronized (lock) {
                try {
                    LOG.finest("Refreshing");
                    listener.refresh();
                } catch (RuntimeException ex) {
                    logException(ex);
                } finally {
                    // Remove the runnable from the queue
                    queuedRefreshTasks.remove(watchDir, this);
                    LOG.finest("Refreshed");
                }
            }
        }

        public void await() {
            // The task is currently running, so wait until it's done
            // TODO: maybe do a timed wait and reschedule refresh if time exceeded
            synchronized (lock) {
                return;
            }
        }

        private void logException(Throwable e) {
            LOG.log(Level.SEVERE, "An error occurred in the watcher timer service!", e);
        }

    }
}
