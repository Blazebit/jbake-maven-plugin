[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.blazebit/jbake-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.blazebit/jbake-maven-plugin)

JBake Maven Plugin
==========
A Maven plugin for building and running JBake sites.

This plugin is separate from [ingenieux's plugin](https://github.com/ingenieux/jbake-maven-plugin) although it was inspired by it.

The minimum requirements are Maven 3.0.0 and JDK 7.

What is it?
===========

A simple wrapper around JBake that supports building and running a site through Maven goals.

Features
==============

The plugin is a simple wrapper around JBake with support for

* Defining JBake properties per execution
* Building the site through the `jbake:build` goal
* Rebuild on changes by through the `jbake:watch` goal
* Rebuild and serve locally through the `jbake:serve` goal

How to use it?
==============
Just include the plugin in your build

```xml
<build>
	<plugins>
		<plugin>
			<groupId>com.blazebit</groupId>
			<artifactId>jbake-maven-plugin</artifactId>
			<version>1.0.0</version>
			<executions>
				<execution>
					<phase>generate-resources</phase>
					<goals>
						<goal>build</goal>
					</goals>
					<configuration>
						<properties>
							<site.url>http://localhost:8820/</site.url>
						</properties>
					</configuration>
				</execution>
			</executions>
		</plugin>
	</plugins>
</build>
```

You can override the port or IP address on which the site is served via system properties.

```bash
mvn jbake:serve -Djbake.port=1234 -Djbake.listenAddress=127.0.0.1
```

Licensing
=========

This distribution, as a whole, is licensed under the terms of the Apache
License, Version 2.0 (see LICENSE.txt).
