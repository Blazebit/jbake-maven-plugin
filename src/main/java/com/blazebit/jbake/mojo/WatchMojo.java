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

/**
 * Builds a JBake site and watches for changes to rebuild.
 *
 * @author Christian Beikov
 */
@Mojo(name = "watch", requiresDirectInvocation = true, requiresProject = false)
public class WatchMojo extends BuildMojo {

    private static final long DEFAULT_SLEEP = 1000L;
    
    private boolean changed;
    private final WatcherService watcherService = new WatcherService();
    
    private void onChange() {
        changed = true;
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
                onChange();
            }

            @Override
            public void created(Path path) {
                onChange();
            }

            @Override
            public void deleted(Path path) {
                onChange();
            }

            @Override
            public void modified(Path path) {
                onChange();
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
                    if (changed) {
                        getLog().info("Refreshing");
                        bake();
                        changed = false;
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
