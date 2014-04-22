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

The resulting jar has the following structure:

``` txt
Capsule.class
foo.jar
|__ app.jar
|__ dep1.jar
|__ dep2.jar
\__ MANIFEST
    \__ MANIFEST.MF
```

When we run the capsule with `java -jar foo.jar`, its contents will be extracted into a cache directory (whose location can be customized).

This kind of capsule has the advantage of being completely self-contained, and it does not require online access to run. The downside is that it can be rather large, as all dependencies are stuffed into the jar.

The second kind of capsule does not embed the app's dependencies in the jar, but downloads them when first run:

``` groovy
task capsule(type: Jar, dependsOn: classes) {
    archiveName = "foo.jar"
    from sourceSets.main.output // this way we don't need to extract

    from { configurations.capsule.collect { zipTree(it) } } // we need all of Capsule's classes
    
    manifest { 
        attributes(
            'Main-Class'        : 'Capsule',
            'Application-Class' : mainClassName,
            'Extract-Capsule'   : 'false', // don't extract capsule to the filesystem
            'Min-Java-Version'  : '1.8.0',
            'JVM-Args'          : run.jvmArgs.join(' '),
            'System-Properties' : run.systemProperties.collect { k,v -> "$k=$v" }.join(' '),
            'Java-Agents'       : getDependencies(configurations.quasar).iterator().next(),
            'Dependencies'      : getDependencies(configurations.runtime).join(' ')
        )
    }
}
```

The resulting jar has the following structure:

``` txt
foo.jar
|__ Capsule.class
|__ capsule/
|    \__ [capsule classes]
|__ com/
|   \__ acme/
|       \__ [app classes]    
\__ MANIFEST/
    \__ MANIFEST.MF
```

This capsule doesn't embed the dependencies in the jar, so our application's classes can be simply placed in it unwrapped. Instead, the `Dependencies` attribute declares the application's dependencies (the `getDependencies` function translates Gradle dependencies to Capsule dependencies. Its definition can be found [here](XXXXXXX) and it may be copied verbatim to any build file). The first time we run `java -jar foo.jar`, the dependencies will be downloaded (by default from Maven Central, but other Maven repositories may be declared in the manifest). The dependencies are placed in a cache directory shared by all capsules, so common ones like SLF4J or Guava will only be downloaded once. Also, because the app's classes are placed directly in the jar, and the dependencies are loaded to a shared cache, the capsule does not need to be extracted to the filesystem at all, hene the manifest says `Extract-Capsule : false`.

Instead of specifying the dependencies and (optionally) the repositories directly in the manifest, if the capsule contains a `pom.xml` file in the jar root, it will be used to find the dependencies.

In order to support Maven dependencies, we needed to include all of Capsule's classes in the capsule jar rather than just the `Capsule` class. This will add about 2MB to the (compressed) jar, but will save a lot more by not embedding all the dependencies.

## User Guide

Most applications will need to specify two attributes in the capsule's manifest:

``` txt
Main-Class : Capsule
Application-Class : [the applications's main class]
```

These attributes are sufficient to build a capsule, but there are many other configuration options, which will be explained below.

### Launching the Capsule

As mentioned before, `java -jar app.jar [app arguments]` will launch the application.

The `java -Dcapsule.version -jar app.jar` command will print the version of Capsule used, and then exit without launching the app.

Adding `-Dcapsule.log=verbose` before `-jar` will print information about Capsule's action.

### Application ID

The application ID is used to find the capsule's application cache, where the capsule's contents will be extracted if necessary. If the manifest has an `Application-Name`, it will be the application's ID, combined with the `Application-Version` attribute, if found. If there is no `Application-Name` attribute, and a `pom.xml` file is found in the jar's root, the ID will be formed by joining the POM's groupId, ArtifactId, and version properties. If there is no pom file, the `Application-Class` attribute will serve as the application name.

### Java Version

By default, the application will run using the same JVM used to run the capsule itself, i.e., the JVM used to run `java -jar app.jar`. If the `Min-Java-Version` attribute is specified in the manifest (e.g. 1.7.0_50 or 1.8.0), it will be used to test the JVM's version. If the JVM is not of the required version, an exception will be throw, and the app will not run.

The `Java-Version` attribute can specify a JVM version that may be different from the one used to launch the capsule. If that attribute is found, Capsule will make an effort to find a JVM installation that matches the requested version. Capsule will use the highest update version that matches the requested one, i.e. if `Java-Version` is `1.7.0`, and there are two Java 7 installations, one `1.7.0_40` and one `1.7.0_50`, then `1.7.0_50` will be used. `Java-Version` may specify either a higher or a lower version than the one used to launch the capsule. For example, if Java 7 is used to launch the capsule and `Java-Version` is `1.6.0`, then Capsule will attempt to find a Java 6 installation and use it to run the app (or fail to launch the app if it can't). Similarly if `1.8.0` is requested.

Whatever the `Min-Java-Version` or `Java-Version` attributes specify, launching the capsule with the `capsule.java.home` system property, will use whatever Java installation is specified by the property, for example: `java -Dcapsule.java.home=/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home -jar app.jar`.

Running `java -Dcapsule.jvms -jar app.jar` will list all Java installations Capsule can find, and then quit without launching the app.

### Capsule's Cache

By default, Capsule will extract the capsule jar's contents -- except for class files placed directly in the jar -- into a cache directory. If the
`Extract-Capsule` manifest attribute is set to `false`, the jar will not be extracted. The value of the `Extract-Capsule` attribute, can be overriden with the `capsule.extract` system property, as in `java -Dcapsule.extract=true -jar app.jar`.

The capsule will be extracted once. Following run will compare the jar's modification date with that of the cache, and will re-write the cache only if the capsule jar is younger. You can force re-extraction of the capsule with `-Dcapsule.reset=true`.

The location of the cache, as well as the location of the jar are communicated to the application through the `capsule.dir` and `capsule.jar` system properties respectively. Capsule defines these properties automatically, and the application may use them, for example, to find extracted resources. In addition, those two filesystem paths can be used within the manifest itself to set various values (system proeprties, environment variables, etc) by referencing them with `$CAPSULE_DIR` and `$CAPSULE_JAR` respectively.

Capsule's cache is found, by default, at `~/.capsule/` on Unix/Linux/Mac OS machines, and at `%USERPROFILE%\AppData\Local\capsule\` on windows. The application caches are placed in the `apps/APP_ID` subdirectory of the cache, while the shared dependency cache is at the `deps` subdirectory. The location of the Capsule cache can be changed with environment variables: setting `CAPSULE_CACHE_NANE` determines the name of the topmost Capsule cache dir (i.e. "capsule" by default), while `CAPSULE_CACHE_DIR` can be used to set a precise path for the cache (e.g. `/tmp/capsule/).


### Dependencies

reset

Placed in the `deps` subdirectory of Capsule's cache directory and shared by all capsules. 

tree

### Class Paths

By default, Capsule sets the application's class path to: the capsule jar iteslf, the application's cache directory (if the capsule is extracted) and every jar found in the root of the cache directory (i.e. every jar file placed in the capsule jar's root) -- in no particular order -- and, finally, the application's Maven dependencies in the order they are listen in the `Dependencies` attribute or the pom file.

The classpath, however, can be customized by the `Class-Path` attribute, which can be given an ordered (space separated) list of jars and/or directories relative to the capsule jar root. This attribute only applies if the capsule is extracted, and if it is found in the manifest, then all jars in the cache's root will not be added automatically to the classpath.

In addition to setting the application classpath, you can also specify the boot classpath. The `Boot-Class-Path` attribute is, similar to the `Class-Path` attribute, an ordered, space separated list of jars and/or directories relative to the capsule's root, that will become the application's boot classpath. If you don't want to replace the default Java boot classpath, but simply to tweak it, The `Boot-Class-Path-P` attribute can be used to specify a classpath to be prepended to the default boot classpath, and the `Boot-Class-Path-P` attribute can specify a class path that will be appended to the default.

If the capsule is launched with a `-Xbootclasspath` option, it will override any setting by the capsule's manifest.


### JVM Arguments, System Properties, and Java Agents



### Scripts

While Capsule mostly makes startup scripts unnecessary, in some circumstances they can be useful. Capsule allows placing platform-specific scripts into the capsule jar, and executing them instead of launching a JVM and running `Application-Class`. The `Unix-Script` attribute specifies the location (relative to the capsule jar's root) of a POSIX shell script, to be run on POSIX machines, while `Windows-Script` specifies the location of a Windows script file. If only, say, `Unix-Script` is defined, then on Windows machines Capsule will simply run the `Application-Class` as usual.

The scripts can make use of the `CAPSULE_CACHE_DIR` environment variable to locate capsule files. Scripts cannot be used if the `Extract-Capsule` attribute is `false`. 

### Security Manager

The `Security-Policy` attribute specifies a Java [security policy file](http://docs.oracle.com/javase/7/docs/technotes/guides/security/PolicyFiles.html) to use for the application. Its value is the location of the security file relative to the capsule jar root. The `Security-Policy-A` achieves a similar purpose, only the security policy specified is added to the default one rather than replaces it. Finally, the `Security-Manager` attribute can specify a class to be used as the security manager for the application. 

If any of these three properties is set, a security manager will be in effect when the application runs. If the `Security-Manager` attribute is not set, the default security manager will be used.


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
 

variable expasnsion

External dependencies:

## Refernce

### Attributes

### System Properties

### Environment Variables


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

``` txt
 Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 
 This program and the accompanying materials are licensed under the terms 
 of the Eclipse Public License v1.0 as published by the Eclipse Foundation.

     http://www.eclipse.org/legal/epl-v10.html
```