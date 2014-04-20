# Capsule

## A simple single-file container for JVM applications

Capsule is a dead-easy deployment package for standalone JVM applications. Capsule lets you package your entire application into a single jar file and run it like this `java -jar app.jar`. That's it. You don't need platform-specific startup scripts, and no JVM flags: the application capsule contains all the JVM configuration options. It supports native libraries, custom boot class-path, and Java agents. It can even automatically download maven dependencies if you choose not to embed them in the capsule. 

## How Capsule Works

When you include Capsule into your JAR file and set Capsule as the JAR's main class, Capsule reads various configuration values (like JVM arguments, environment variables, Maven dependencies and more) from the JAR's manifest. It then downloads all required Maven dependencies, if any, and optionally extracts the JAR's contents into a cache directory. Finally, it spawns another process to run your application as configured.

## What Capsule Doesn't Do

Capsule doesn't contain a JVM distribution, the application user would need to have a JRE installed. Java 9 is expected to have a mechanism for packaging stripped-down versions of the JVM.

## Alternatives to Capsule

* Zip file with startup scripts: requires user installation.
* Executanble (fat) JAR: licensing issues might prohibit this, embedded libraries might over-write each other's resources, and a startup script is still required to set up the JVM, Java agents etc.
* [One-Jar](http://one-jar.sourceforge.net/): might interfere with the application in subtle ways, and still startup scripts are required for the JVM arguments and Java agents.

## Usage Examples

Before we delve into the specifics of defining a Capsule distribution, let us look at a few different ways of packaging a capsule. The examples are snippets of [Gradle](http://www.gradle.org/) build files, but the same could be achieved with [Ant](http://ant.apache.org/) or [Maven](http://maven.apache.org/).

We'll assume that the application's `gradle.build` file applies the [`java`](http://www.gradle.org/docs/current/userguide/java_plugin.html) and `[application`](http://www.gradle.org/docs/current/userguide/application_plugin.html) plugins. Also, the build file declare the `capsule` configuration and contains the dependency `capsule 'co.paralleluniverse:capsule:0.1.0-SNAPSHOT'`.

The first example creates what may be calle a "full" capsule:

``` groovy
task fullCapsule(type: Jar, dependsOn: jar) {
    archiveName = "foo.jar"
    
    from jar // embed our application jar
    from { configurations.runtime } // embed dependencies
    
    from(configurations.capsule.collect { zipTree(it) }) { include 'Capsule.class' } // we just need the single Capsule class
    
    manifest { 
        attributes(
            'Main-Class'        : 'Capsule',
            'Application-Class' : mainClassName,
            'Java-Version'      : '1.8.0',
            'JVM-Args'          : run.jvmArgs.join(' '),
            'System-Properties' : run.systemProperties.collect { k,v -> "$k=$v" }.join(' '),
            'Java-Agents'       : configurations.quasar.iterator().next().getName()
        )
    }
}
```

We embed the application jar and all the dependency jars into the capsule jar (without extracting them). We also include the `Capsule` class in the jar.
Then, in the jar's manifest, we declare `Capsule` as the main class. This is the class that will be executed when we run `java -jar foo.jar`. The `Application-Class` attribute tells Capsule which class to run in the new JVM process, and we set it to the same value, `mainClass` used by the build's `run` task. The `Java-Version` attribute specifies the JVM version will be used to run the application. If this version is different from the Java version used to launch the capsule, Capsule will look for an appropriate JRE installation to use. We then copy the JVM arguments and system properties from build file's `run` task into the manifest, and finally we declare a Java agent used by [Quasar](https://github.com/puniverse/quasar).

When we run the capsule with `java -jar foo.jar`, its contents will be extracted into a cache directory (whose location can be customized).

This kind of capsule has the advantage of being completely self-contained, and it does not require online access to run. The downside is that it can be rather large, as all dependencies are stuffed into the jar.

The second kind of capsule does not embed the app's dependencies in the jar, but downloads them when first run:

``` groovy
task capsule(type: Jar, dependsOn: capsule) {
    archiveName = "foo.jar"
    from sourceSets.main.output // this way we don't need to extract

    from { configurations.capsule.collect { zipTree(it) } } // we need all of Capsule's classes
    
    manifest { 
        attributes(
            'Main-Class'        : 'Capsule',
            'Application-Class' : mainClassName,
            'Extract-Capsule'   : 'false',
            'Min-Java-Version'  : '1.8.0',
            'JVM-Args'          : run.jvmArgs.join(' '),
            'System-Properties' : run.systemProperties.collect { k,v -> "$k=$v" }.join(' '),
            'Java-Agents'       : getDependencies(configurations.quasar).iterator().next(),
            'Dependencies'      : getDependencies(configurations.runtime).join(' ')
        )
    }
}
```

This capsule doesn't embed the dependencies in the jar, so our application's classes can be simply placed in it unwrapped. Instead, the `Dependencies` attribute declares the application's dependencies (the `getDependencies` function translates Gradle dependencies to Capsule dependencies. Its definition can be found [here](XXXXXXX) and it may be copied verbatim to any build file). The first time we run `java -jar foo.jar`, the dependencies will be downloaded (by default from Maven Central, but other Maven repositories may be declared in the manifest),. The dependencies are placed in a cache directory shared by all capsules, so common ones like SLF4J or Guava will only be downloaded once.

Instead of specifying the dependencies and (optionally) the repositories directly in the manifest, if the capsule contains a `pom.xml` file in the jar root, it will be used to find the dependencies.

In order to support Maven dependencies, we needed to include all of Capsule's classes in the capsule jar rather than just the `Capsule` class. This will add about 2MB to the (packed) jar, but will save a lot more by not embedding all the dependencies.

## Usage


Mandatory:

* `Main-Class` : `Capsule`
* `Application-Class` - the only mandatory attribute

Optional:

* `Application-Name`
* `Application-Version`
* `Min-Java-Version`
* `Java-Version`
* `App-Class-Path` default: the launcher jar root and every jar file found in the launcher jar's root.
* `Environment-Variables` `$CAPSULE_DIR`
* `System-Properties`
* `JVM-Args`
* `Boot-Class-Path`
* `Boot-Class-Path-P`
* `Boot-Class-Path-A`
* `Library-Path-P`
* `Library-Path-A`
* `Java-Agents`


* `Repositories`
* `Dependencies`


`CAPSULE_CACHE_DIR`
`CAPSULE_CACHE_NAME`

    "capsule.reset"
    "capsule.version"
    "capsule.log"
    "capsule.tree"
 



External dependencies:




## Licensing Issues

As Capsule does not link in any way with any of the code bundled in the Jar file, and simply treats it as raw data, Capsule is no different from a self-extracting Zip file (especially as manually unzipping the jar's contents is extrmely easy). Capsule's own license, therefore, does not interfere with the licensing of the bundled software.

In particular, even though Capsule's license is incompatible with the GPL, it is permitted to distribute GPL programs packaged as capsules, as Capsule is simply a packaging medium and an activation script, and does not restrict access to the packaged GPL code. Capsule does not add any capability to, nor removes any from the bundled application.


[GPL](https://www.gnu.org/copyleft/gpl.html):

> A compilation of a covered work with other separate and independent works, which are not by their nature extensions of the covered work, and which are not combined with it such as to form a larger program, in or on a volume of a storage or distribution medium, is called an “aggregate” if the compilation and its resulting copyright are not used to limit the access or legal rights of the compilation's users beyond what the individual works permit. Inclusion of a covered work in an aggregate does not cause this License to apply to the other parts of the aggregate.

## Differnces from [One-Jar](http://one-jar.sourceforge.net/)

 * Might interfere with application (esp. those using tricky things)
 * Does not support Java instrumentation agents, or any command-line arguments
 * No support for maven dependencies

 Disadvantages:

 * Requires writing to the filesystem


 ## License

~~~
 Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 
 This program and the accompanying materials are licensed under the terms 
 of the Eclipse Public License v1.0 as published by the Eclipse Foundation.

     http://www.eclipse.org/legal/epl-v10.html
~~~