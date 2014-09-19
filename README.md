# *Capsule*<br/>Dead-Simple Packaging and Deployment for JVM Apps
[![Build Status](http://img.shields.io/travis/puniverse/capsule.svg?style=flat)](https://travis-ci.org/puniverse/capsule) [![Dependency Status](https://www.versioneye.com/user/projects/539704a483add7f80a000030/badge.svg?style=flat)](https://www.versioneye.com/user/projects/539704a483add7f80a000030) [![Version](http://img.shields.io/badge/version-0.8.0-blue.svg?style=flat)](https://github.com/puniverse/capsule/releases) [![License](http://img.shields.io/badge/license-EPL-blue.svg?style=flat)](https://www.eclipse.org/legal/epl-v10.html)


Capsule is a dead-easy deployment package for standalone JVM applications. Capsule lets you package your entire application into a single JAR file and run it like this `java -jar app.jar`. That's it. You don't need platform-specific startup scripts, and no JVM flags: the application capsule contains all the JVM configuration options. It supports native libraries, custom boot class-path, and Java agents. It can automatically download Maven dependencies when the program is first launched if you choose not to embed them in the capsule, and it can even automatically download a new version of your application when it is published to a Maven repository.

### How Capsule Works

When you include the Capsule class in your JAR file and set it to serve as the JAR's main class, Capsule reads various configuration values (like JVM arguments, environment variables, Maven dependencies and more) from the JAR's manifest. It then downloads all required Maven dependencies, if any, and optionally extracts the JAR's contents into a cache directory. It then picks a JVM installation based on the version requirements in the manifest, and finally, it spawns another JVM process to run your application as configured.

### What Capsule Doesn't Do

Capsule doesn't contain a JVM distribution, the application user would need to have a JRE installed. Java 9 is expected to have a mechanism for packaging stripped-down versions of the JVM.

### Overhead

A "fat" capsule (one with all dependencies embedded in the capsule JAR), adds about 100ms to the startup time. A "thin" capsule (one with external dependencies, downloaded at first launch), adds about 500ms to the launch time, once the dependencies have been downloaded and cached during the first launch.

In terms of JAR size, fat capsules add negligible overhead, while thin capsules add 1.5MB (for the code responsible to downloading and resolving the external dependencies) -- but they don't require the dependencies to be embedded in the JAR, so there's overall savings in deliverable size.

### Alternatives to Capsule

There are a few alternatives to packaging your application in a single JAR. [Maven's Shade plugin](http://maven.apache.org/plugins/maven-shade-plugin/)/[Gradle's Shadow plugin](https://github.com/johnrengelman/shadow) rename dependency classes and might interfere with the application in subtle ways; they also don't support native libraries. [One-Jar](http://one-jar.sourceforge.net/) does support native libraries, but uses class-loader hacks that may interfere with the application in even subtler ways. And none of these support JVM arguments. Shade/Shadow, however, are suitable for distributing libraries, while Capsule only works for applications.

The only distribution mechanism supporting JVM arguments and Java version selection is platform-dependent startup scripts. Even if your build tool can generate those for you, they would always require some form of installation by the user.

With Capsule, you just distribute a single JAR and run it.

### Getting Capsule

[Download](https://github.com/puniverse/capsule/releases)

or:

    co.paralleluniverse:capsule:0.8.0

On Maven Central.

### Building Capsule

    ./gradlew install

### Support

Discuss Capsule on the capsule-user [Google Group/Mailing List](https://groups.google.com/forum/#!forum/capsule-user)

### Build-Tool Plugins

Maven:

  * https://github.com/chrischristo/capsule-maven-plugin
  * https://github.com/pguedes/capsule-maven-plugin (limited functionality)

Gradle:

  * https://github.com/danthegoodman/gradle-capsule-plugin

### What's Missing/TODO (contributions happily accepted!)

* Leiningen plugins for easy creation of capsules.

## Usage Examples

Before we delve into the specifics of defining a Capsule distribution, let us look at a few different ways of packaging a capsule. The configurations described below can be built using any JVM build tool. The complete usage example, with both Gradle and Maven build files, is found in the [capsule-demo](https://github.com/puniverse/capsule-demo) project.

The first example creates what may be called a *fat* capsule. The capsule JAR has the following structure:

    foo.jar
    |__ Capsule.class
    |__ app.jar
    |__ dep1.jar
    |__ dep2.jar
    \__ MANIFEST
        \__ MANIFEST.MF

With the manifest (`MANIFEST.MF`) being:

    Manifest-Version: 1.0
    Main-Class: Capsule
    Application-Class: foo.Main
    Min-Java-Version: 1.8.0
    JVM-Args: -server
    System-Properties: foo.bar.option=15 my.logging=verbose
    Java-Agents: dep2.jar

We embed the application JAR (`app.jar`) as well as all dependency JARs into the capsule JAR (without extracting them). We also include the `Capsule` class in the JAR.
Then, in the JAR's manifest, we declare `Capsule` as the main class. This is the class that will be executed when we run `java -jar foo.jar`. The `Application-Class` attribute tells Capsule which class to run in the new JVM process, and we set it to the same value, `mainClass` used by the build's `run` task. The `Min-Java-Version` attribute specifies the JVM version that will be used to run the application. If this version is newer than the Java version used to launch the capsule, Capsule will look for an appropriate JRE installation to use (a maximum version can also be specified with the `Java-Version` attribute). We then also specify JVM arguments, system properties and even a Java agent.

When we run the capsule with `java -jar foo.jar`, its contents will be extracted into a cache directory (whose location can be customized).

This kind of capsule has the advantage of being completely self-contained, and it does not require online access to run. The downside is that it can be rather large, as all dependencies are stuffed into the JAR.

The second kind of capsule -- a *thin* capsule -- does not embed the app's dependencies in the JAR, but downloads them when first run. The capsule JAR looks like this:

    foo.jar
    |__ Capsule.class
    |__ capsule/
    |    \__ [capsule classes]
    |__ com/
    |   \__ acme/
    |       \__ [app classes]
    \__ MANIFEST/
        \__ MANIFEST.MF

With the manifest being:

    Manifest-Version: 1.0
    Main-Class: Capsule
    Application-Class: foo.Main
    Min-Java-Version: 1.8.0
    Extract-Capsule : false
    JVM-Args: -server
    System-Properties: foo.bar.option=15 my.logging=verbose
    Dependencies: com.acme:dep1:1.2 com.acme:dep2:3.4
    Java-Agents: com.acme:dep2:3.4


This capsule doesn't embed the dependencies in the JAR, so our application's classes can be simply placed in it unwrapped. Instead, the `Dependencies` attribute declares the application's dependencies. The first time we run `java -jar foo.jar`, the dependencies will be downloaded (by default from Maven Central, but other Maven repositories may be declared in the manifest). The dependencies are placed in a cache directory shared by all capsules, so common ones like SLF4J or Guava will only be downloaded once. Also, because the app's classes are placed directly in the JAR, and the dependencies are loaded to a shared cache, the capsule does not need to be extracted to the filesystem at all, hence the manifest says `Extract-Capsule : false`.

Instead of specifying the dependencies and (optionally) the repositories directly in the manifest, if the capsule contains a `pom.xml` file in the JAR root, it will be used to find the dependencies.

In order to support Maven dependencies, we needed to include all of Capsule's classes in the capsule JAR rather than just the `Capsule` class. This will add about 1.5MB to the (compressed) JAR, but will save a lot more by not embedding all the dependencies.

Finally, a capsule may not contain any of the application's classes/JARs at all. The capsule's manifest can contain these two attributes:

    Main-Class: Capsule
    Application: com.acme:foo

And when the capsule is launched, the newest available version of the application will be downloaded, cached and launched. In this use-case, the capsule JAR looks like this:

    foo.jar
    |__ Capsule.class
    |__ capsule/
    |    \__ [capsule classes]
    \__ MANIFEST/
        \__ MANIFEST.MF

Note how none of the application's classes are actually found in the JAR.

## User Guide

A capsule requires two attributes in its manifest file. The first, is the same for all capsules:

    Main-Class : Capsule

This attributes makes the JAR file into a capsule. The second attribute tells the capsule what to run when launched. It must be one of:

    Application-Class : [the applications's main class, found in the capsule or one of its dependencies]

which specifies the application's main class, or,

    Application : [the Maven coordinates of the application's main JAR]

or,

    Unix-Script    : [a startup script to be run on *nix machines, found in the capsule]
    Windows-Script : [a startup script to be run on Windows machines, found in the capsule]

These attributes are sufficient to build a capsule, but there are many other configuration options, which will be explained below.

### Launching the Capsule

As mentioned before, `java -jar app.jar [app arguments]` will launch the application.

The `java -Dcapsule.version -jar app.jar` command will print the application ID, the version of Capsule used, and then exit without launching the app.

Capsule's JAR can be used standalone (without merging with any application binaries), to launch applications stored in a Maven repository like so:

    java -jar capsule.jar com.acme:foo [app arguments]

The above command will download (and cache) the Maven artifact and its dependencies, and run it, provided that it's a capsule itself, or even any executable JAR.

Adding `-Dcapsule.log=verbose` or `-Dcapsule.log=debug`  before `-jar` will print information about Capsule's action.

### Capsule Configuration and Modes

A capsule is (mostly) configured by attributes in its manifest. Manifest attributes specify which Java version to use when launching the application, what agents to load, the JVM arguments, system properties, dependencies and more.

It is possible to specify different values for each of the configurations -- to be selected at launch time -- by creating several capsule *modes*. Modes are defined by manifest sections, each section's name corresponds to the mode. Attributes in the manifest's main section are used in the default mode, and those listed in the named sections can override them when the respective node is selected (configurations without overrides will be taken from the main section).

Example:

    Attribute-X: foo
    Attribute-Y: bar

    Name: Special
    Attribute-Y: baz

When running in the `Special` mode, `Attribute-Y` will have the value `baz`, while the non-overriden `Attribute-X` will have the value `foo`, as in the default mode.

To select a non default mode, you launch the capsule with the `-Dcapsule.mode=MODE` command line argument.

The `Application-Name` and `Application-Version` attributes (see the next section) can only be listed in the manifest's main section.

### Application ID

The application ID is used to find the capsule's application cache, where the capsule's contents will be extracted if necessary. If the manifest has an `Application-Name`, it will be the application's ID, combined with the `Application-Version` attribute, if found. If there is no `Application-Name` attribute, and a `pom.xml` file is found in the JAR's root, the ID will be formed by joining the POM's groupId, artifactId, and version properties. If there is no POM file, the `Application-Class` attribute will serve as the application name.

The application's ID can be overridden by the `capsule.app.id` system property, if defined when launching the capsule, as in `java -Dcapsule.app.id=my_old_app -jar app.jar`

### Selecting the Java Runtime

Two manifest attributes determine which Java installation Capsule will use to launch the application. `Min-Java-Version` (e.g. `1.7.0_50` or `1.8.0`) is the lowest Java version to use, while `Java-Version` (e.g. `1.6`) is the highest *major* Java version to use. One, both, or neither of these attributes may be specified in the manifest.

First, Capsule will test the current JVM (used to launch the capsule) against `Min-Java-Version` and `Java-Version` (if they're specified). If the version of the current JVM matches the requested range, it will be used to launch the application. If not, Capsule will search for other JVM installations, and use the one with the highest version that matches the requested range. If no matching installation is found, the capsule will fail to launch.

It is also possible to require a minimal update version, say, in cases where the update fixes a bug affecting the application. Because the bug may have been fixed in two different major versions in two different updates (e.g., for Java 7 in update 85, and for Java 8 in update 21), the minimal update required is specified per major Java version, in the `Min-Update-Version` attribute. For example

    Min-Update-Version: 7=85 1.8=21

The major version can be given as a single digit (`7`) or as a "formal" Java version (`1.7`, or, `1.7.0`). The updates are specified in a whitespace separated list of entries, each entry containing a key (the major version) and a value (the minimum update) as a `=` separated pair.

If the `JDK-Required` attribute is set to `true`, Capsule will only select JDK installations.

Whatever the `Min-Java-Version`, `Java-Version`, or `JDK-Required` attributes specify, launching the capsule with the `capsule.java.home` system property, will use whatever Java installation is specified by the property, for example: `java -Dcapsule.java.home=/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home -jar app.jar`.

Running `java -Dcapsule.jvms -jar app.jar` will list all Java installations Capsule can find, and then quit without launching the app.

### Capsule's Cache

By default, Capsule will extract the capsule JAR's contents -- except for class files placed directly in the JAR -- into a cache directory. If the
`Extract-Capsule` manifest attribute is set to `false`, the JAR will not be extracted.

The capsule will be extracted once. Following run will compare the JAR's modification date with that of the cache, and will re-write the cache only if the capsule JAR is younger. You can force re-extraction of the capsule with `-Dcapsule.reset=true`.

The location of the cache and the location of the JAR are communicated to the application through the `capsule.dir` and `capsule.jar` system properties respectively. Capsule defines these properties automatically, and the application may use them, for example, to find extracted resources. In addition, those two filesystem paths can be used within the manifest itself to set various values (system properties, environment variables, etc) by referencing them with `$CAPSULE_DIR` and `$CAPSULE_JAR` respectively.

Using the values in `capsule.dir` or `CAPSULE_DIR` to directly access the cache is generally discouraged, and should be a last resort (but a working one, nonetheless), as it depends on Capsule's inner workings.

Capsule's cache is found, by default, at `~/.capsule/` on Unix/Linux/Mac OS machines, and at `%USERPROFILE%\AppData\Local\capsule\` on Windows. The application caches are placed in the `apps/APP_ID` subdirectory of the cache, while the shared dependency cache is at the `deps` subdirectory. The location of the Capsule cache can be changed with environment variables: setting `CAPSULE_CACHE_NAME` determines the name of the topmost Capsule cache dir (i.e. "capsule" by default), while `CAPSULE_CACHE_DIR` can be used to set a precise path for the cache (e.g. `/tmp/capsule/).

### Maven Dependencies

The capsule can specify external dependencies as coordinates in Maven repositories. One way of specifying dependencies, is placing the app's `pom.xml` file in the capsule JAR's root. Another is specifying the dependencies and repositories in the capsule's manifest.

By default, Capsule will look for dependencies on Maven Central. If other repositories are needed (or if you don't want to access Maven Central), the `Repositories` attribute is a space-separated list of Maven repository URLs. The repositories will be searched in the order they are listed. If the `Repositories` attribute is found in the manifest, then Maven Central will not be searched.

Instead of specifying explicit URLs, the following well-known repository names can be listed in the `Repositories` attribute:

* `central` - Maven central, HTTPS
* `central-http` - Maven central, HTTP
* `jcenter` - jCenter, HTTPS
* `jcenter-http` - jCenter, HTTP
* `local` - Default local Maven repository (`userdir/.m2/repository`). You should only use local repositories in tests.

Capsule also automatically uses the information in Maven's `settings.xml` file to access repositories that require authentication. The `settings.xml` used is the user's settings (in the `.m2` directory in the user's home directory), or in the global settings (in the `conf` subdirectory of the Maven installation). A `settings.xml` file is absolutely not required for Capsule's operation, and is mostly useful when accessing private organizational Maven repositories for dependencies.

The dependencies, (if not read from the POM), are listed in the `Dependencies` attribute, as a space-separated list of Maven coordinates in the Gradle format, i.e. `groupId:artifactId:version`. Exclusions can be given as a comma separated list within parentheses, immediately following the main artifact, of `groupId:artifactId` coordinates, where the artifact can be the wildcard `*`. For example:

    Dependencies : com.esotericsoftware.kryo:kryo:2.23.0(org.ow2.asm:*)

The dependencies are downloaded the first time the capsule is launched, and placed in the `deps` subdirectory of the Capsule cache, where they are shared among all capsules.

The `CAPSULE_REPOS` environment variable can be set to a *comma-* (`,`) or a whitespace-separated list of Maven repository URLS or well-known repository names (see above), which will be prepended to those specified in the manifest or the POM.

By default, SNAPSHOT dependencies are not allowed, unless the `Allow-Snapshots` is set to `true`.

Capsule can make use of Maven repositories in another way: the `Application` manifest attribute can specify the Maven coordinates of the application's main JAR file, which can in itself be a capsule.

Maven [version ranges](http://maven.apache.org/enforcer/enforcer-rules/versionRanges.html) (as well as `LATEST` and `RELEASE`) are supported. For example: `Application: com.acme:foo:[1.0,2.0)`. The newest version matching the range (or the newest version if no range is given), will be downloaded, cached and launched. Not specifying a version at all will resolve to the latest release. If the application's main artifact is a capsule, then all configurations will be taken based on those in the artifact capsule.

Native dependencies can be specified in the `Native-Dependencies-Linux`/`Native-Dependencies-Win`/`Native-Dependencies-Mac` attributes, each for its respective OS. A native dependency is written as a plain dependency but can be followed by a comma and a new filename to give the artifact once downloaded (e.g.: `com.acme:foo-native-linux-x64:1.0,foo-native.so`). Each native artifact must be a single native library, with a suffix matching the OS (`.so` for Linux, `.dll` for windows, `.dylib` for Mac). The native libraries are downloaded and copied into the application cache (and renamed if requested).

Adding `-Dcapsule.reset=true`, can force a re-download of SNAPSHOT versions.

The command: `java -Dcapsule.tree -jar app.jar`, will print the dependency tree for the capsule, and then quit without launching the app.

Two more system properties affect the way Capsule searches for dependencies. If `capsule.offline` is defined or set to `true` (`-Dcapsule.offline` or `-Dcapsule.offline=true`), Capsule will not attempt to contact online repositories for dependencies (instead, it will use the local Maven repository/cache only). `capsule.local` determines the path for the local Maven repository/cache Capsule will use (which, by default, is the `deps` subdirectory of the Capsule cache).

### Class Paths

By default, Capsule sets the application's class path to: the capsule JAR itself, the application's cache directory (if the capsule is extracted) and every JAR found in the root of the cache directory (i.e. every JAR file placed in the capsule JAR's root) -- in no particular order -- and, finally, the application's Maven dependencies in the order they are listen in the `Dependencies` attribute or the POM file.

The classpath, however, can be customized by the `Class-Path` attribute, which can be given an ordered (space separated) list of JARs and/or directories relative to the capsule JAR root. This attribute only applies if the capsule is extracted, and if it is found in the manifest, then all JARs in the cache's root will not be added automatically to the classpath.

In addition to setting the application classpath, you can also specify the boot classpath. The `Boot-Class-Path` attribute is, similar to the `Class-Path` attribute, an ordered, space separated list of JARs and/or directories relative to the capsule's root, that will become the application's boot classpath. If you don't want to replace the default Java boot classpath, but simply to tweak it, The `Boot-Class-Path-P` attribute can be used to specify a classpath to be prepended to the default boot classpath, and the `Boot-Class-Path-P` attribute can specify a class path that will be appended to the default.

If the capsule is launched with a `-Xbootclasspath` option, it will override any setting by the capsule's manifest.

The `Library-Path-A` manifest attribute can list JARs or directories (relative to the capsule's root) that will be appended to the application's native library path. Similarly, `Library-Path-P`, can be used to prepend JARs or directories to the default native library path.

### JVM Arguments, System Properties, Environment Variables and Java Agents

The `JVM-Args` manifest attribute can contain a space-separated list of JVM argument that will be used to launch the application. Any JVM arguments supplied during the capsule's launch, will override those in the manifest. For example, if the `JVM-Args` attribute contains `-Xmx500m`, and the capsule is launched with `java -Xmx800m -jar app.jar`, then the application will be launched with the `-Xmx800m` JVM argument.

JVM arguments listed on the command line apply both to the Capsule launch process as well as the application process (see *The Capsule Execution Process*). Sometimes this is undesirable (e.g. when specifying a debug port, which can only apply to a single process or a port collision will ensue). JVM arguments for at the application process (only) can be supplied by adding `-Dcapsule.jvm.args="MY JVM ARGS"` to the command line.

The `Args` manifest attribute can contain a space-separated list of command line arguments to be passed to the application; these will be prepended to any arguments passed to the capsule at launch.

The `System-Properties` manifest attribute can contain a space-separated list of system properties that will be defined in the launched application. The properties are listed as `property=value` pairs (or just `property` for an empty value). Any system properties supplied during the capsule's launch, will override those in the manifest. For example, if the `JVM-Args` attribute contains `name=Mike`, and the capsule is launched with `java -Dname=Jason -jar app.jar`, then the application will see the `name` system-property defined as `Jason`.

The `Environment-Variables` manifest attribute, is, just like `System-Properties`, a space-separated list of `var=value` pairs (or just `var` for an empty value). The specified values do not overwrite those already defined in the environment, unless they are listed as `var:=value` rather than `var=value`.

The `Java-Agents` attribute can contain a space-separated list with the names of JARs containing Java agents. The agents are listed as `agent=params` or just `agent`, where `agent` is either the path of a JAR embedded in the capsule, relative to the capsule JAR's root, or the coordinates of a Maven dependency.

Remember that values listed in all these configuration values can contain the `$CAPSULE_DIR` and `$CAPSULE_JAR` variables, discussed in the *Capsule's Cache* section.

### Scripts

While Capsule mostly makes startup scripts unnecessary, in some circumstances they can be useful. Capsule allows placing platform-specific scripts into the capsule JAR, and executing them instead of launching a JVM and running `Application-Class`. The `Unix-Script` attribute specifies the location (relative to the capsule JAR's root) of a POSIX shell script, to be run on POSIX machines, while `Windows-Script` specifies the location of a Windows script file. If only, say, `Unix-Script` is defined, then on Windows machines Capsule will simply run the `Application-Class` as usual.

The scripts can make use of the `CAPSULE_DIR` environment variable to locate capsule files. Scripts cannot be used if the `Extract-Capsule` attribute is `false`.

### Security Manager

The `Security-Policy` attribute specifies a Java [security policy file](http://docs.oracle.com/javase/7/docs/technotes/guides/security/PolicyFiles.html) to use for the application. Its value is the location of the security file relative to the capsule JAR root. The `Security-Policy-A` achieves a similar purpose, only the security policy specified is added to the default one rather than replaces it. Finally, the `Security-Manager` attribute can specify a class to be used as the security manager for the application.

If any of these three properties is set, a security manager will be in effect when the application runs. If the `Security-Manager` attribute is not set, the default security manager will be used.

### Custom Capsules

You can customize many of the capsule's inner workings by creating a *custom capsule*. A custom capsule is a subclass of `Capsule` that overrides some of its overridable methods. To use your custom capsule, include its class in the root of the capsule JAR, and simply indicate it is the JAR's main class in the mainfest, like so:

    Main-Class: MyCustomCapsule

Please consult Capsule's [Javadoc](http://puniverse.github.io/capsule/capsule/javadoc/Capsule.html) for specific documentation on custom capsules.

### "Really Executable" Capsules

A JAR file can be made "really executable" in UNIX/Linux/MacOS environments -- i.e. it can be run simply as `capsule.jar ARGS` rather than `java -jar capsule.jar ARGS` -- by [prepending a couple of shell script lines to the JAR](http://skife.org/java/unix/2011/06/20/really_executable_jars.html) (it turns out JAR files can tolerate any prepended headers). In Maven, you can use the [really-executable-jars](https://github.com/brianm/really-executable-jars-maven-plugin) plugin to make your capsule really executable (or if you're using the [capsule-maven-plugin](https://github.com/chrischristo/capsule-maven-plugin), just set the `buildExec` tag to true). In Gradle, this can be done by adding the following function to your build file:

``` groovy
def reallyExecutable(jar) {
    ant.concat(destfile: "tmp.jar", binary: true) {
        zipentry(zipfile: configurations.capsule.singleFile, name: 'capsule/execheader.sh')
        fileset(dir: jar.destinationDir) {
            include(name: jar.archiveName)
        }
    }
    copy {
        from 'tmp.jar'
        into jar.destinationDir
        rename { jar.archiveName }
    }
    delete 'tmp.jar'
}
```

and then

``` groovy
capsule.doLast { task -> reallyExecutable(task) }
```

### The Capsule Execution Process

When a capsule is launched, two processes are involved: first, a JVM process runs the capsule launcher, which then starts up a second, child process that runs the actual application. The two processes are linked so that killing or suspending one, will do the same for the other. On UNIX systems (Linux, Mac, etc.), the launcher process makes public the identity of the child process by setting the `capsule.app.pid` system property, which can be queried with the `jcmd` command. Suppose the capsule's pid is 1234, then the child (app) process pid can be obtained with the following shell command:

    jcmd 1234 VM.system_properties  | grep capsule.app.pid | cut -f 2 -d =

While this model works well enough in most scenarios, sometimes it is desirable to directly launch the process running the application, rather than indirectly. This is supported by "capsule trampoline", and is available for really executable capsules on UNIX systems only. To take advantage of capsule trampolining, use the `capsule/trampoline-execheader.sh` executable header (rather than `capsule/execheader.sh`) when creating the really executable capsule.

### capsule-util

The [capsule-util](http://puniverse.github.io/capsule/capsule-util/javadoc/) sub-project contains classes to create and interact with capsule's at runtime. See the Javadocs [here](http://puniverse.github.io/capsule/capsule-util/javadoc/).

## Reference

### Manifest Attributes

Everywhere the word "list" is mentioned, it is whitespace-separated.

* `Application-Name`: the name of the application, used to define its ID
* `Application-Version`: the application's version, used to define its ID
* `Application-Class`: the application's main class
* `Application`: The Maven coordinates of the application's main JAR (can be a capsule)
* `Unix-Script`: a startup script to be run *instead* of `Application-Class` on Unix/Linux/Mac OS, given as a path relative to the capsule's root
* `Windows-Script`: a startup script to be run *instead* of `Application-Class` on Windows, given as a path relative to the capsule's root
* `Extract-Capsule`: if `false`, the capsule JAR will not be extracted to the filesystem (default: `true`)
* `Min-Java-Version`: the lowest Java version required to run the application; Capsule will look for an appropriate installation
* `Min-Update-Version`: a space-separated key-value ('=' separated) list mapping Java versions to the minimum update version required
* `Java-Version`: the highest version of the Java installation required to run the application; Capsule will look for an appropriate installation
* `JDK-Required`: if set to `true`, the Capsule will only be launched using a JDK, if one matching the requested versions is found.
* `JVM-Args`: a list of JVM arguments that will be used to launch the application's Java process
* `Args`: the list of command line arguments to be passed to the application; the UNIX shell-style special variables (`$*`, `$1`, `$2`, ...) can refer to the actual arguments passed on the capsule's command line; if no special var is used, the listed values will be prepended to the supplied arguments (i.e., as if `$*` had been listed last).
* `Environment-Variables`: a list of environment variables that will be put in the applications environment; formatted `var=value` or `var`
* `System-Properties`: a list of system properties that will be defined in the applications JVM; formatted `prop=value` or `prop`
* `App-Class-Path`: a list of JARs, relative to the capsule root, that will be put on the application's classpath, in the order they are listed
* `Capsule-In-Class-Path`: if set to `false`, the capsule JAR itself will not be on the application's classpath (default: `true`)
* `Boot-Class-Path`: a list of JARs, dependencies, and/or directories, relative to the capsule root, that will be used as the application's boot classpath.
* `Boot-Class-Path-A`: a list of JARs dependencies, and/or directories, relative to the capsule root, that will be appended to the applications default boot classpath
* `Boot-Class-Path-P`: a list of JARs dependencies, and/or directories, relative to the capsule root, that will be *prepended* to the applications default boot classpath
* `Library-Path-A`: a list of JARs and/or directories, relative to the capsule root, that will be appended to the default native library path
* `Library-Path-P`: a list of JARs and/or directories, relative to the capsule root, that will be *prepended* to the default native library path
* `Security-Manager`: the name of a class that will serve as the application's security-manager
* `Security-Policy`: a security policy file, relative to the capsule root, that will be used as the security policy
* `Security-Policy-A`: a security policy file, relative to the capsule root, that will be appended to the default security policy
* `Java-Agents`: a list of Java agents used by the application; formatted `agent` or `agent=arg1,arg2...`, where agent is either the path to a JAR relative to the capsule root, or a Maven coordinate of a dependency
* `Repositories`: a list of Maven repository URLs
* `Dependencies`: a list of Maven dependencies given as `groupId:artifactId:version[(excludeGroupId:excludeArtifactId,...)]`
* `Allow-Snapshots`: If `true`, allows for SNAPSHOT dependencies (default: `false`)
* `Native-Dependencies-Linux`: a list of Maven dependencies consisting of `.so` artifacts for Linux; each item can be a comma separated pair, with the second component being a new name to give the download artifact. The artifacts will be Windows and copied into the application's cache directory.
* `Native-Dependencies-Win`: a list of Maven dependencies consisting of `.dll` artifacts for Linux; each item can be a comma separated pair, with the second component being a new name to give the download artifact. The artifacts will be downloaded and copied into the application's cache directory.
* `Native-Dependencies-Mac`: a list of Maven dependencies consisting of `.dylib` artifacts for Mac OS X; each item can be a comma separated pair, with the second component being a new name to give the download artifact. The artifacts will be downloaded and copied into the application's cache directory.
* `Capsule-Log-Level`: sets the default log level for the Capsule launcher (which can be overridden with `-Dcapsule.log`); can be one of: `NONE`, `QUIET` (the default), `VERBOSE`, or `DEBUG`.

### Manifest Variables

* `$CAPSULE_JAR`: the full path to the capsule JAR
* `$CAPSULE_DIR`: the full path to the application cache directory, if the capsule is extracted.
* `$JAVA_HOME`: the full path to the Java installation which will be used to launch the app

### System Properties

* `capsule.version`: if set, the capsule will print the application ID, its Capsule version and quit without launching the app
* `capsule.tree`: if set, the capsule will print the app's dependency tree, and then quit without launching the app
* `capsule.jvms`: if set, the capsule will print the JVM installations it can locate with their versions, and then quit without launching the app
* `capsule.resolve`: all external dependencies, if any, will be downloaded (if not cached already), and/or the capsule will be extracted if necessary, but the application will not be launched
* `capsule.mode`: if set, the capsule will be launched in the specified mode (see *Capsule Configuration and Modes*)
* `capsule.log`: can be set to 'none'/'quiet' (default)/'verbose'/'debug'
* `capsule.jvm.args`: specifies JVM arguments for the capsule's app process.
* `capsule.reset`: if set, forces re-extraction of the capsule, where applies, and/or re-downloading of SNAPSHOT dependencies
* `capsule.app.id`: sets the value of the application ID (see user guide)
* `capsule.java.home`: forces the capsule to use the given path to a Java installation when launching the application.
* `capsule.offline`: if defined (without a value) or set to `true`, Capsule will not attempt to contact online repositories for dependencies
* `capsule.local`: the path for the local Maven repository; defaults to CAPSULE_CACHE/deps
* `capsule.connect.timeout`: The maximum amount of time (in milliseconds) to wait for a successful connection to a remote repository. Non-positive values indicate no timeout.
* `capsule.request.timeout`: The maximum amount of time (in milliseconds) to wait for remaining data to arrive from a remote repository. Note that this timeout does not restrict the overall duration of a request, it only restricts the duration of inactivity between consecutive data packets. Non-positive values indicate no timeout.

Capsule defines these system properties in the application's process:

* `capsule.app`: the app ID
* `capsule.jar`: the full path to the capsule's JAR
* `capsule.dir`: if the JAR has been extracted, the full path of the application cache (use generally discouraged).

Capsule defines these system properties in the capsule (launcher) process (to be queried by `jcmd`):

* `capsule.app.pid`: the child (application) process PID; available only in POSIX environments.

### Environment Variables

* `CAPSULE_CACHE_NAME`: sets the *name* of the root of Capsule's cache in the default location (`~` on Unix, `%LOCALAPPDATA%` on Windows)
* `CAPSULE_CACHE_DIR`: sets the full path of the Capsule's cache
* `CAPSULE_REPOS`: sets the list -- comma (`,`) or whitespace separated -- of Maven repositories that the capsule will use; overrides those specified in the manifest or the POM.
* `CAPSULE_CONNECT_TIMEOUT`: The maximum amount of time (in milliseconds) to wait for a successful connection to a remote repository. Non-positive values indicate no timeout.
* `CAPSULE_REQUEST_TIMEOUT`: The maximum amount of time (in milliseconds) to wait for remaining data to arrive from a remote repository. Note that this timeout does not restrict the overall duration of a request, it only restricts the duration of inactivity between consecutive data packets. Non-positive values indicate no timeout.

Capsule defines these variables in the application's environment:

* `CAPSULE_APP`: the app ID
* `CAPSULE_JAR`: the full path to the capsule's JAR
* `CAPSULE_DIR`: if the JAR has been extracted, the full path of the application cache.

These values can also be accessed with `$VARNAME` in any capsule manifest attributes.

### Javadoc

* [capsule](http://puniverse.github.io/capsule/capsule/javadoc/Capsule.html)
* [capsule-util](http://puniverse.github.io/capsule/capsule-util/javadoc/)
* [capsule-container](http://puniverse.github.io/capsule/capsule-container/javadoc/)

## License

    Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.

    This program and the accompanying materials are licensed under the terms
    of the Eclipse Public License v1.0 as published by the Eclipse Foundation.

        http://www.eclipse.org/legal/epl-v10.html

As Capsule does not link in any way with any of the code bundled in the JAR file, and simply treats it as raw data, Capsule is no different from a self-extracting ZIP file (especially as manually unzipping and examining the JAR's contents is extremely easy). Capsule's own license, therefore, does not interfere with the licensing of the bundled software.

In particular, even though Capsule's license is incompatible with the GPL/LGPL, it is permitted to distribute GPL programs packaged as capsules, as Capsule is simply a packaging medium and an activation script, and does not restrict access to the packaged GPL code. Capsule does not add any capability to, nor removes any from the bundled application. It therefore falls under the definition of an "aggregate" in the GPL's terminology.
