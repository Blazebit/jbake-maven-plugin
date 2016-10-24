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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import spark.Spark;

/**
 * Builds and serves a JBake site locally.
 *
 * @author Christian Beikov
 */
@Mojo(name = "serve", requiresDirectInvocation = true, requiresProject = false)
public class ServeMojo extends WatchMojo {

    /**
     * The IP on which to serve the JBake site.
     */
    @Parameter(property = "jbake.listenAddress", defaultValue = "127.0.0.1")
    private String listenAddress;

    /**
     * The port on which to serve the JBake site.
     */
    @Parameter(property = "jbake.port", defaultValue = "8820")
    private Integer port;

    @Override
    public void execute() throws MojoExecutionException {
        // Shutdown hook just to be safe
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Spark.stop();
            }
        });
        
        outputDirectory.mkdirs();
        
        Spark.externalStaticFileLocation(outputDirectory.getPath());
        Spark.ipAddress(listenAddress);
        Spark.port(port);

        Spark.init();
        Spark.awaitInitialization();
        
        try {
            super.execute();
        } finally {
            Spark.stop();
        }
    }
}
