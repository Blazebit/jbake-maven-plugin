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
package com.blazebit.jbake.mojo;

import com.blazebit.jbake.mojo.watcher.WatcherListener;
import com.blazebit.jbake.mojo.watcher.WatcherService;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Builds a JBake site and watches for changes to rebuild.
 *
 * @author Christian Beikov
 */
@Mojo(name = "watch", requiresDirectInvocation = true, requiresProject = false)
public class WatchMojo extends BuildMojo {

    private static final long DEFAULT_SLEEP = 1000L;
    
    private Status status = Status.OK;
    private final WatcherService watcherService = new WatcherService();
    private final Set<String> configFiles = new HashSet<String>(Arrays.asList(
        "custom.properties",
        "jbake.properties",
        "default.properties"
    ));
    
    private static enum Status {
        OK,
        CHANGED,
        CONFIG_CHANGED;
    }
    
    private void onChange(Path path) {
        if (path == null || configFiles.contains(path.toString())) {
            status = Status.CONFIG_CHANGED;
        } else {
            status = Status.CHANGED;
        }
    }
    
    @Override
    public void execute() throws MojoExecutionException {
        setup();
        
        // Shutdown hook just to be safe
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                watcherService.shutdown();
            }
        });
        
        watcherService.addListener(inputDirectory.toPath(), new WatcherListener() {
            
            @Override
            public void refreshQueued() {
                // We don't really care about the queuing event
            }

            @Override
            public void refresh() {
                onChange(null);
            }

            @Override
            public void created(Path path) {
                onChange(path);
            }

            @Override
            public void deleted(Path path) {
                onChange(path);
            }

            @Override
            public void modified(Path path) {
                onChange(path);
            }
        });
        
        // Initial baking
        bake();
        
        getLog().info(
                "Watching for changes in: " + inputDirectory.getPath());
        getLog().info("Stop with Ctrl + C");

        try {
            while (true) {
                try {
                    Thread.sleep(DEFAULT_SLEEP);
                    watcherService.processEvents();
                    if (status != Status.OK) {
                        getLog().info("Refreshing");
                        if (status == Status.CONFIG_CHANGED) {
                            rebuild();
                        }
                        
                        bake();
                        status = Status.OK;
                    }
                } catch (InterruptedException e) {
                    // Ctrl + C received
                    return;
                }
            }
        } catch (Throwable ex) {
            throw new MojoExecutionException("Error while baking", ex);
        } finally {
            getLog().info("Shutting down...");
            watcherService.shutdown();
        }
    }
}
