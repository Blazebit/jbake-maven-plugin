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

import com.orientechnologies.orient.core.Orient;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jbake.app.ConfigUtil;
import org.jbake.app.Oven;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

/**
 * Builds a JBake site.
 *
 * @author Christian Beikov
 */
@Mojo(name = "build", requiresProject = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class BuildMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    /**
     * Location of the output directory.
     */
    @Parameter(property = "jbake.outputDirectory",
            defaultValue = "${project.build.directory}/${project.build.finalName}",
            required = true)
    protected File outputDirectory;

    /**
     * Location of the input directory.
     */
    @Parameter(property = "jbake.inputDirectory", defaultValue = "${project.basedir}/src/main/jbake",
            required = true)
    protected File inputDirectory;

    /**
     * Whether the cache should be cleared or not.
     */
    @Parameter(property = "jbake.clearCache", defaultValue = "false", required = true)
    protected boolean clearCache;
    
    /**
     * Properties that are passed to JBake which override the jbake.properties.
     */
    @Parameter
    protected Map<String, String> properties;
    
    private Oven oven;

    @Override
    public void execute() throws MojoExecutionException {
        setup();
        bake();
    }
    
    protected void bake() throws MojoExecutionException {
        setup();
        
        try {
            oven.bake();
        } catch (Throwable ex) {
            destroy();
            throw new MojoExecutionException("Failure when running: ", ex);
        }
    }
    
    protected void setup() throws MojoExecutionException {
        if (oven != null) {
            return;
        }
        
        try {
            Orient.instance().startup();
            this.oven = new Oven(inputDirectory, outputDirectory, createConfiguration(), clearCache);
            oven.setupPaths();
        } catch (Throwable ex) {
            destroy();
            throw new MojoExecutionException("Failure when running: ", ex);
        }
    }
    
    protected void destroy() {
        oven = null;
        
        try {
            Orient.instance().shutdown();
        } catch (Exception e) {
            getLog().warn("Error on shutdown", e);
        }
    }

    protected CompositeConfiguration createConfiguration() throws ConfigurationException {
        final CompositeConfiguration config = new CompositeConfiguration();

        if (properties != null) {
            config.addConfiguration(new MapConfiguration(properties));
        }
        config.addConfiguration(new MapConfiguration(project.getProperties()));
        config.addConfiguration(ConfigUtil.load(inputDirectory));
        
        if (getLog().isDebugEnabled()) {
            getLog().debug("Configuration:");

            Iterator<String> iter = config.getKeys();
            while (iter.hasNext()) {
                String key = iter.next();
                getLog().debug(key + ": " + config.getString(key));
            }
        }

        return config;
    }

}
