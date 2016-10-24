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
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;

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
    @Parameter(property = "jbake.listenAddress", defaultValue = "0.0.0.0")
    private String listenAddress;

    /**
     * The port on which to serve the JBake site.
     */
    @Parameter(property = "jbake.port", defaultValue = "8820")
    private Integer port;

    @Override
    public void execute() throws MojoExecutionException {
        final Server server = new Server();
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setHost(listenAddress);
        connector.setPort(port);
        server.setConnectors(new Connector[]{ connector });
        
        ResourceHandler externalResourceHandler = new ResourceHandler();
        externalResourceHandler.setResourceBase(outputDirectory.getPath());
        externalResourceHandler.setWelcomeFiles(new String[] { "index.html" });
        
        HandlerCollection handlers = new HandlerCollection();
        handlers.setHandlers(new Handler[] { externalResourceHandler });
        server.setHandler(handlers);

        outputDirectory.mkdirs();
        
        try {
            server.start();
        } catch (Exception ex) {
            throw new MojoExecutionException("Could not start server!", ex);
        }
        
        try {
            super.execute();
        } finally {
            try {
                server.stop();
            } catch (Exception ex) {
                getLog().warn("Error on stopping server", ex);
            }
        }
    }
}
