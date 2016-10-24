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

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author Christian Beikov
 */
@SuppressWarnings("restriction")
public class WatchDir {

    private static final Logger LOG = Logger.getLogger(WatchDir.class.getName());
    private static final WatchEvent.Kind<?>[] watchEventKinds = new WatchEvent.Kind<?>[]{
        StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.OVERFLOW};
    private static final WatchEvent.Modifier[] watchModifiers;

    private final WatcherTimerService timerService;
    private final Path rootDir;
    private final WatchService watcher;
    private final WatcherListener listener;
    private final Map<WatchKey, Path> keys;
    private final boolean recursive;
    private final boolean skipHidden;

    static {
        if (System.getProperty("os.name").startsWith("Windows")) {
            // On Windows we use the native file tree watching to avoid lock problems in Windows Explorer
            watchModifiers = new WatchEvent.Modifier[]{com.sun.nio.file.ExtendedWatchEventModifier.FILE_TREE};
        } else {
            watchModifiers = new WatchEvent.Modifier[0];
        }
    }

    WatchDir(WatcherTimerService timerService, Path dir, WatcherListener listener, boolean recursive, boolean skipHidden) throws IOException {
        this.timerService = timerService;
        this.rootDir = dir;
        this.watcher = dir.getFileSystem().newWatchService();
        this.listener = listener;
        this.keys = new HashMap<WatchKey, Path>();
        this.recursive = recursive;
        this.skipHidden = skipHidden;

        if (recursive) {
            registerAll(dir);
        } else {
            register(dir);
        }
    }

    void processEvents() throws ClosedWatchServiceException {
        // poll for key to be signaled
        WatchKey key;
        while ((key = watcher.poll()) != null) {
            Path dir = keys.get(key);
            if (dir == null) {
                LOG.severe("WatchKey not recognized: " + key);
                continue;
            }

            OUTER:
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (StandardWatchEventKinds.OVERFLOW == kind) {
                    try {
                        listener.refreshQueued();
                        // Queue a refresh after a timeout
                        timerService.queue(this, listener);
                    } catch (RuntimeException ex) {
                        logException(ex);
                    }
                } else {
                    // Skip event if queued refresh was re-queued
                    if (timerService.requeue(this, listener)) {
                        continue;
                    }
                    // Context for directory entry event is the file name of entry
                    @SuppressWarnings("unchecked")
                    Path name = ((WatchEvent<Path>) event).context();

                    if (skipHidden) {
                        for (int i = 0; i < name.getNameCount(); i++) {
                            if (name.getName(i).toString().charAt(0) == '.') {
                                continue OUTER;
                            }
                        }
                    }

                    Path child = dir.resolve(name);

                    try {
                        if (StandardWatchEventKinds.ENTRY_CREATE == kind) {
                            // if directory is created, and watching recursively, then
                            // register it and its sub-directories
                            if (recursive && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                                registerRecursive(child);
                            }
                            listener.created(name);
                        } else if (StandardWatchEventKinds.ENTRY_DELETE == kind) {
                            listener.deleted(name);
                        } else if (StandardWatchEventKinds.ENTRY_MODIFY == kind) {
                            // Directory modify events are actually unnecessary when doing recursive watching
                            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                                LOG.log(Level.FINE, "Skipped modify event for directory: " + name);
                            } else {
                                listener.modified(name);
                            }
                        }
                    } catch (RuntimeException ex) {
                        logException(ex);
                    }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    public void close() {
        try {
            watcher.close();
        } catch (IOException e) {
            logException(e);
        }
    }

    public Path getRootDir() {
        return rootDir;
    }

    public WatcherListener getListener() {
        return listener;
    }

    private void registerRecursive(Path dir) {
        // Only needed on non-windows platforms
        if (watchModifiers.length == 0) {
            try {
                registerAll(dir);
            } catch (IOException ex) {
                logException(ex);
            }
        }
    }

    private void register(Path dir) throws IOException {
        if (!skipHidden || dir.getFileName().toString().charAt(0) != '.') {
            WatchKey key = dir.register(watcher, watchEventKinds, watchModifiers);
            keys.put(key, dir);
        }
    }

    private void registerAll(final Path start) throws IOException {
        if (watchModifiers.length == 1) {
            register(start);
        } else {
            Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    register(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void logException(Throwable e) {
        LOG.log(Level.SEVERE, "An error occurred in the watcher service!", e);
    }
}
