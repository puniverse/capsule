---
layout: page
title: User Guide
---

.toc.

## Introduction

### What is Capsule?

Capsule is a packaging and deployment tool for JVM applications. A capsule is a single executable JAR that contains everything your application needs to run either in the form of embedded files or as declarative metadata. It can contain your JAR artifacts, your dependencies and resources, native libraries, the require JRE version, the JVM flags required to run the application well, Java or native agents and more. In short, a capsule is a self-contained JAR that knows everything there is to know about how to run your application the way it's meant to run.

One way of thinking about a capsule is as a fat JAR on steroids (that also allows native libraries and never interferes with your dependencies) and a declarative startup script rolled into one; another, is to see it is as the deploy-time counterpart to your build tool. Just as a build tool manages your build, Capsule manages the launching of your application.

But while plain capsules are cool and let you ship any JVM application -- no matter how complex -- as a single executable JAR, caplets make capsules even more powerful.

### What are Caplets?

Caplets are classes that hook into the capsule and modify its behavior. By default, a capsule contains metadata about your application and the means to execute it as a plain JVM program. Caplets can use the metadata in the capsule to launch the application in some more sophisticated ways, or even change the way it is packaged.

A caplet can be embedded in the capsule, or be packaged separately and used to wrap an existing capsule. While every caplet can be embedded or used as a wrapper, usually just one of the options makes sense.

Here are some examples of caplets (some exist some don't):

* Maven (embedded): this caplet can download the capsule's dependencies from a Maven repository prior to launch, and cache them so that they can be shared with other capsules. This can be used to reduce the capsule's size, "factor out" common dependencies (like an alternative JVM language runtime), or automatically update dependencies or even the application itself.
* Containers (wrapper): this caplet launches a capsule inside a container
* Native executables (wrapper): a caplet that turns a capsule into a native Windows/Mac/Linux native application.
* Daemons (embedded): launches the capsule as a daemon/service
* ZooKeeper/etcd (embedded): reads application values from ZooKeeper/etcd instead of the manifest and uses them to configure the application.
* Sandbox (wrapper): launches the capsule inside a secure JVM sandbox


### Cool Stuff You Can Do with Capsules

* Have your JAR automatically choose an appropriate JVM version, set JVM flags, and add an embedded JAR to the boot class path.
* Embed any required native libraries directly in the JAR, and Capsule automatically makes sure your application finds them.
* Distribute your application as an "executable WAR": it can be deployed to a servlet container *or*, if executed directly, it will automatically download Jetty and deploy itself into the embedded container.
* Distribute a Clojure application without embedding Clojure itself in the capsule, and have Clojure downloaded the first time the capsule is launched. The Clojure runtime will be cached shared among all Clojure capsules so it will only be downloaded once.
* Distribute an Avatar.js application as a JAR containing only JavaScript files, and have Avatar (including its required native libraries) downloaded automatically the first time the application is launched. The Avatar runtime will be cached for later use and shared among other Avatar capules.
* Use a caplet to turn any capsule into a Docker image or to launch it inside a Linux Container.
* Use a caplet to turn any capsule into an OS-specific native application or daemon.

### How Capsule Works

When you include the Capsule class in your JAR file and set it to serve as the JAR's main class, Capsule reads various configuration values (like JVM arguments, environment variables, Maven dependencies and more) and caplets from the JAR's manifest. It then optionally extracts the JAR's contents into a cache directory, performs custom caplet operations, picks a JVM installation based on the version requirements in the manifest, and finally, it spawns another JVM process to run your application as configured.

### What Capsule Doesn't Do

Capsule doesn't contain a JVM distribution, the application user would need to have a JRE installed. Java 9 is expected to have a mechanism for packaging stripped-down versions of the JVM.

### Overhead

A plain capsule adds about 100ms to the startup time, and negligible size to the JAR.

### Alternatives to Capsule

There are a few alternatives to packaging your application in a single JAR. [Maven's Shade plugin](http://maven.apache.org/plugins/maven-shade-plugin/)/[Gradle's Shadow plugin](https://github.com/johnrengelman/shadow) rename dependency classes and might interfere with the application in subtle ways; they also don't support native libraries. [One-Jar](http://one-jar.sourceforge.net/) does support native libraries, but uses class-loader hacks that may interfere with the application in even subtler ways. And none of these support JVM arguments. Shade/Shadow, however, are suitable for distributing libraries, while Capsule only works for applications.

The only distribution mechanism supporting JVM arguments and Java version selection is platform-dependent startup scripts. Even if your build tool can generate those for you, they would always require some form of installation by the user.

With Capsule, you just distribute a single JAR and run it.

### Build-Tool Plugins

Maven:

  * https://github.com/chrischristo/capsule-maven-plugin
  * https://github.com/pguedes/capsule-maven-plugin (limited functionality)

Gradle:

  * https://github.com/danthegoodman/gradle-capsule-plugin

Leiningen:

  * https://github.com/circlespainter/lein-capsule

## User Guide

## An Example

Before we delve into the specifics of defining a Capsule distribution, let us look at an example of a working capsule. The configurations described below can be built using any JVM build tool. The complete usage example, with both Gradle and Maven build files, is found in the [capsule-demo](https://github.com/puniverse/capsule-demo) project.

Our capsule JAR has the following structure:

    foo.jar
    |__ Capsule.class
    |__ app.jar
    |__ dep1.jar
    |__ dep2.jar
    |__ agent1.jar
    \__ META-INF
        \__ MANIFEST.MF

With the manifest (`MANIFEST.MF`) being:

    Manifest-Version: 1.0
    Main-Class: Capsule
    Application-ID: com.acme.foo
    Application-Version: 1.0
    Application-Class: foo.Main
    Min-Java-Version: 1.8.0
    JVM-Args: -server
    System-Properties: foo.bar.option=15 my.logging=verbose
    Java-Agents: agent1.jar

We embed the application JAR (`app.jar`), containing the class `foo.Main` as well as all dependency JARs into the capsule JAR (without extracting them). We also place the `Capsule` class at the root of JAR. Then, in the JAR's manifest, we declare `Capsule` as the main class. This is the class that will be executed when we run `java -jar foo.jar`. The `Application-Class` attribute tells Capsule which class to run in the new JVM process, and we set it to the same value, `mainClass` used by the build's `run` task. The `Min-Java-Version` attribute specifies the JVM version that will be used to run the application. If this version is newer than the Java version used to launch the capsule, Capsule will look for an appropriate JRE installation to use (a maximum version can also be specified). We then list some JVM arguments, system properties and even a Java agent.

When than launch the capsule with `java -jar foo.jar`. When it runs, it will try to find a JRE 8 (or higher) installation on our machine -- whether or not that was the Java version used to launch the capsule -- and then start a new JVM process, configured with the given flags and agent, that will run our application.

### Launching a Capsule

As mentioned before, `java -jar app.jar [app arguments]` will launch the application.

The `java -Dcapsule.version -jar app.jar` command will print the application ID, the version of Capsule used, and then exit without launching the app.

Adding `-Dcapsule.log=verbose` or `-Dcapsule.log=debug`  before `-jar` will print information about Capsule's action.

### A Capsule's Structure and Manifest

A capsule is a JAR file with a manifest -- containing meta-data describing the application -- and some embedded files. The most minimal (probably useless) capsule contains just the `Capsule.class` file, and a `META-INF/MANIFEST.MF` file with the following line:

    Main-Class: Capsule

The existence of `Capsule.class` in the JAR's root, as well as that line in the manifest is what makes a JAR into a capsule (but not yet a launchable one, as we haven't yet specified an attribute that tells the capsule what to run). The capsule's manifest describes everything the application requires in order to launch our application. In the next sections we'll learn the use of various attributes.

### Paths and Artifact Coordinates

Many manifest attributes require specifying the location of files. Files can be specified in two ways: as paths, relative to the capsule's root, or as *artifact coordinates*. Coordinates take the following form:

   group:artifact:version[:classifier]

(the classifier is optional, as is the version; the minimal coordinates are `group:artifact`).

The coordinates are the same as those used by Maven repositories to host the artifact. When a file is given as coordinates, capsule will *resolve* them, by default, by searching for the artifact in the capsule JAR. It will search the following file paths (relative to the capsule's root):

    lib/<group>/<artifact>-<version>.<type>
    lib/<group>-<artifact>-<version>.<type>
    lib/<artifact>-<version>.<type>
    <group>/<artifact>-<version>.<type>
    <group>-<artifact>-<version>.<type>
    <artifact>-<version>.<type>

The *type*, the artifact's file extension, is determined by context (i.e. depending on the specific attribute where the coordinates are used), but is usually `jar`.

The version may be omitted, or may specify a version range, but in those cases, the capsule will just pick any available version, *provided there is just one*.

Caplets may change the coordinate resolution process. For example, the Maven caplet can resolve coordinates by downloading artifacts from a Maven repository if they are not found in the capsule.

### The Application

At the very least, a capsule must have an attribute specifying what application to run. It must be one of:

    Application-Class : [the applications's main class, found in the capsule or one of its dependencies]

which specifies the application's main class, or,

    Application : [the coordinates of the application's main executable JAR or the path of the main executable JAR within the capsule]

or,

    Script : [a platform specific startup shell/batch-script]

If a script is used, a different one should be listed for each OS type, as we'll see below.

### The Capsule ID

Every capsule has an ID comprised of an *application ID* and an *application version*. If the `Application` attribute is used, and the application is given as artifact coordinates, the application ID will be `<group>.<artifact>` and the version will be the artifact's version. Otherwise, the ID and version should be defined with the `Application-ID` and `Application-Version` attributes respectively. `Application-ID` must not contains spaces and should be unique; to ensure uniqueness, it should follow the convention of Java package naming and begin with the author's or application's revered web domain. A good ID is something like `com.acme.foo`.

It is also good practice to define the application's human-readable name, like, "The Foo App", in the `Application-Name` attribute.

In most circumstances, the capsule will launch without specifying any of the attributes described in this section, but it is highly recommended that you define them nonetheless.

### Capsule's Cache

By default, Capsule will extract the capsule JAR's contents -- except for class files placed directly in the JAR -- into a cache directory. This, however, is an implementation detail and should not be relied upon by applications.

The location of the cache directory is, by default, at `~/.capsule/` on Unix/Linux/Mac OS machines, and at `%USERPROFILE%\AppData\Local\capsule\` on Windows. The application caches are placed in the `apps/CAPSULE_ID` subdirectory of the cache.

If the capsule fails to write to the cache directory (say, the user lacks permission), the cache will be placed in a in the default, platform specific, temp-file directory, and deleted upon the application's termination. Otherwise, the capsule will be extracted once. Following run will compare the JAR's modification date with that of the cache, and will re-write the cache only if the capsule JAR is younger. You can force re-extraction of the capsule with `-Dcapsule.reset=true`.

The location of the Capsule cache can be changed with environment variables: setting `CAPSULE_CACHE_NAME` determines the name of the topmost Capsule cache dir (i.e. "capsule" by default), while `CAPSULE_CACHE_DIR` can be used to set a precise path for the cache (e.g. `/tmp/capsule/`).

The location of the cache and the location of the JAR are communicated to the application through the `capsule.dir` and `capsule.jar` system properties respectively. Capsule defines these properties automatically, and the application may use them, for example, to find extracted resources. In addition, those two filesystem paths can be used within the manifest itself to set various values (system properties, environment variables, etc) by referencing them with `$CAPSULE_DIR` and `$CAPSULE_JAR` respectively.

Applications should not generally assume anything about the `capsule.dir` property defined for them by their capsule. Depending on caplet behavior or even changes to the default capsule implementation, that property may be empty; if it isn't the directory cannot be assumed to have write permissions.

### Modes, Platform- and Version-Specific Configuration

A capsule is configured by attributes in its manifest. Manifest attributes specify which Java version to use when launching the application, what agents to load, the JVM arguments, system properties, dependencies and more.

It is possible to specify different values for each of the configurations -- to be selected at launch time -- by defining attributes in special manifest *sections*. Currently, there are three types of sections supported: OS, JRE version and mode. Manifest sections better allow a capsule to adapt to its environment, but are by no means necessary for most capsules.

An OS section is selected based on the OS launching the capsule, and allows defining attributes that are different based on the OS; this is useful for specifying a launch script (in the `Script` attribute, or native libraries in the `Native-Dependencies`/`Native-Agents` attributes), but can be used for any attribute. The name of the section is the OS name, and can be one of: `Windows`, `MacOS`, `Linux`, `Solaris`, `Unix` or `POSIX`. The `Unix` section is in effect for any Unix-like system (including Linux and Solaris), and the `POSIX` section is in effect for Unix-like systems as well as MacOS. So a simple launch script can be defined so:

    Name: Windows
    Script: run.bat

    Name: POSIX
    Script: run.sh

A JRE version section is selected based on the major Java version chosen by capsule to run the application (as we'll see later, a capsule can require a specific Java version, or a minimum version). This kind of section is rare, but can be useful for applications that might use different libraries depending on the JRE version used. This kind of section is named `Java-N`, where N is the major version number (6, 7, 8 etc.).

Modes are a little different, as they are not selected automatically, but by the user when the capsule is launched. Modes are useful in providing different configurations the user can choose from, or even different applications within the same capsule. You define a mode by creating a section that has the mode's name. For obvios reasons, modes can't be named in a way that would confuse them with OS or JRE sections, so, for example, you can't name a mode "Windows", nor can you name it, `Java-9`. Here's an example of the `Special` mode:

    Application-Class: foo.Main

    Name: Special
    Application-Class: foo.Special

To further specialize modes based on OS or JRE version, simply add `-` and the OS/JRE name to the modes, like so:

    Name: Windows
    Script: run.bat

    Name: POSIX
    Script: run.sh

    Name: Special-Windows
    Script: special.bat

    Name: Special-POSIX
    Script: special.sh

A mode is selected by adding `-Dcapsule.mode=[mode name]` to the capsule's launch command line. Mode names are case insensitive. A capsule's modes can be listed by adding `-Dcapsule.modes` to the command line.

The `Application-Name`, `Application-Id` and `Application-Version` attributes can only be listed in the manifest's main section; all other attributes may be specialized based on OS, JRE version, or mode. How attribute's values are combined when different sections are in effect depend on the type of the attribute. If the attribute is a single value (e.g. `Application-Class`), a section's attribute overrides the default value (in the main section). If it is a list (e.g. `Dependencies`), the value is *added* to the default one. So, for example:


    Foo: hi
    Bar: 1 2 3
    Baz: x y

    Name: Linux
    Foo: hi-l
    Bar: 4

    Name: Windows
    Foo: hi-w

    Name: Special
    Bar: 5

    Name: Special-Linux
    Foo: special-l
    Bar: 6

If the mode `special` is selected, and the capsule is run on a Linux system, the value of the `Foo` attribute will be `special-l`, and that of the `Bar` attribute, because it's a list attribute, will be: `1 2 3 4 5 6` (because the `Linux`, `Special`, and `Special-Linux` sections are in effect). The `Baz` attribute is never overridden, and maintains its value of `x y`.

### Selecting the Java Runtime

Two manifest attributes determine which Java installation Capsule will use to launch the application. `Min-Java-Version` (e.g. `1.7.0_50` or `1.8.0`) is the lowest Java version to use, while `Java-Version` (e.g. `1.6`) is the highest *major* Java version to use. One, both, or neither of these attributes may be specified in the manifest.

First, Capsule will test the current JVM (used to launch the capsule) against `Min-Java-Version` and `Java-Version` (if they're specified). If the version of the current JVM matches the requested range, it will be used to launch the application. If not, Capsule will search for other JVM installations, and use the one with the highest version that matches the requested range. If no matching installation is found, the capsule will fail to launch.

It is also possible to require a minimal update version, say, in cases where the update fixes a bug affecting the application. Because the bug may have been fixed in two different major versions in two different updates (e.g., for Java 7 in update 85, and for Java 8 in update 21), the minimal update required is specified per major Java version, in the `Min-Update-Version` attribute. For example

    Min-Update-Version: 7=85 1.8=21

The major version can be given as a single digit (`7`) or as a "formal" Java version (`1.7`, or, `1.7.0`). The updates are specified in a whitespace separated list of entries, each entry containing a key (the major version) and a value (the minimum update) as a `=` separated pair.

If the `JDK-Required` attribute is set to `true`, Capsule will only select JDK installations.

Whatever the `Min-Java-Version`, `Java-Version`, or `JDK-Required` attributes specify, launching the capsule with the `capsule.java.home` system property, will use whatever Java installation is specified by the property, for example: `java -Dcapsule.java.home=/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home -jar app.jar`.

Finally, setting the `capsule.java.cmd` system property with the path to the executable which will be used to launch the JVM, overrides any JRE selection mechanism.

Running `java -Dcapsule.jvms -jar app.jar` will list all Java installations Capsule can find, and then quit without launching the app.

### Class Paths and Native Libraries

By default, Capsule sets the application's class path to the capsule JAR itself and every JAR file placed in the capsule's root -- in some unspecified (but constant) order. If the capsule contains an `Application` attribute, all entries in the `Class-Path` attribute in the manifest of the `Application` JAR are added to the classpath automatically.

The classpath, however, can be customized by the `App-Class-Path`/`Dependencies` attribute, which can be given an ordered (space separated) list of JARs and/or directories relative to the capsule JAR root, and/or artifact coordinates. The `Class-Path` and `Dependencies` attributes are similar, but not identical. If the `Class-Path` attribute is defined, then the JARs in the capsule's root are not automatically added to the class path (unless explicilty listed in the attribute), while the `Dependencies` attribute simply adds them to the class path. Also, the `Dependencies` attribute conventionally lists artifact coordinates, while the `App-Class-Path` attribute usually lists file paths, but this is not enforced, and either style can be used for both.

For convenience, when specifying the class path, glob-pattern wildcards (`*`, `?`) may be used.

In addition to setting the application classpath, you can also define its boot classpath. The `Boot-Class-Path` attribute is, similar to the `App-Class-Path` attribute, an ordered, space separated list of JARs and/or directories relative to the capsule's root and/or artifact coordinates, that will become the application's boot classpath. If you don't want to replace the default Java boot classpath, but simply to tweak it, The `Boot-Class-Path-P` attribute can be used to specify a classpath to be prepended to the default boot classpath, and the `Boot-Class-Path-A` attribute can specify a class path that will be appended to the default.

If the capsule is launched with a `-Xbootclasspath` option, it will override any setting by the capsule's manifest.

The `Library-Path-A` manifest attribute can list JARs or directories (relative to the capsule's root) that will be appended to the application's native library path. Similarly, `Library-Path-P`, can be used to prepend JARs or directories to the default native library path.

Native dependencies can be specified in the `Native-Dependencies` attribute. A native dependency is written as a plain dependency but can be followed by an equal-sign and a new filename to give the artifact once downloaded (e.g.: `com.acme:foo-native-linux-x64:1.0,foo-native.so`). Each native artifact must be a single native library, with a suffix matching the OS (`.so` for Linux, `.dll` for windows, `.dylib` for Mac). The native libraries are downloaded and copied into the application cache (and renamed if requested).

### JVM Arguments, System Properties, Environment Variables and Agents

The `JVM-Args` manifest attribute can contain a space-separated list of JVM argument that will be used to launch the application. Any JVM arguments supplied during the capsule's launch, will override those in the manifest. For example, if the `JVM-Args` attribute contains `-Xmx500m`, and the capsule is launched with `java -Xmx800m -jar app.jar`, then the application will be launched with the `-Xmx800m` JVM argument.

JVM arguments listed on the command line apply both to the Capsule launch process as well as the application process (see *The Capsule Execution Process*). Sometimes this is undesirable (e.g. when specifying a debug port, which can only apply to a single process or a port collision will ensue). JVM arguments for at the application process (only) can be supplied by adding `-Dcapsule.jvm.args="MY JVM ARGS"` to the command line.

The `Args` manifest attribute can contain a space-separated list of command line arguments to be passed to the application; these will be prepended to any arguments passed to the capsule at launch.

The `System-Properties` manifest attribute can contain a space-separated list of system properties that will be defined in the launched application. The properties are listed as `property=value` pairs (or just `property` for an empty value). Any system properties supplied during the capsule's launch, will override those in the manifest. For example, if the `JVM-Args` attribute contains `name=Mike`, and the capsule is launched with `java -Dname=Jason -jar app.jar`, then the application will see the `name` system-property defined as `Jason`.

The `Environment-Variables` manifest attribute, is, just like `System-Properties`, a space-separated list of `var=value` pairs (or just `var` for an empty value). The specified values do not overwrite those already defined in the environment, unless they are listed as `var:=value` rather than `var=value`.

The `Java-Agents` attribute can contain a space-separated list with the names of JARs containing Java agents. The agents are listed as `agent=params` or just `agent`, where `agent` is either the path of a JAR embedded in the capsule, relative to the capsule JAR's root, or the coordinates of a Maven dependency.

Similarly, the `Native-Agents` attribute may contain a list of native JVMTI agents to be used by the application. The formatting is the same as that for `Java-Agents`, except that `agent` is the path to the native library -- minus the extension -- relative to the capsule root. Unlike in the case of `Java-Agents`, listing Maven dependencies is not allowed, but the same result can be achieved by listing the depndencies in the `Native-Dependencies-...` attributes and then referring to the library file's name in the `Native-Agents` attribute.

Remember that values listed in all these configuration values can contain the `$CAPSULE_DIR` and `$CAPSULE_JAR` variables, discussed in the *Capsule's Cache* section.

### Scripts

While Capsule mostly makes startup scripts unnecessary, in some circumstances they can be useful. Capsule allows placing platform-specific scripts into the capsule JAR, and executing them instead of launching a JVM and running `Application-Class`. The `Script` attribute specifies the location (relative to the capsule JAR's root) of the script, and should generally by defined in a platform-specific section.

The scripts can make use of the `CAPSULE_DIR` environment variable to locate capsule files. In addition, Capsule will choose the JVM installation based on the version requirements, and set the `JAVA_HOME` environment variable for the script appropriately.

### Security Manager

The `Security-Policy` attribute specifies a Java [security policy file](http://docs.oracle.com/javase/7/docs/technotes/guides/security/PolicyFiles.html) to use for the application. Its value is the location of the security file relative to the capsule JAR root. The `Security-Policy-A` achieves a similar purpose, only the security policy specified is added to the default one rather than replaces it. Finally, the `Security-Manager` attribute can specify a class to be used as the security manager for the application.

If any of these three properties is set, a security manager will be in effect when the application runs. If the `Security-Manager` attribute is not set, the default security manager will be used.

### Applying Caplets

To apply caplets to the capsule you list them, in the order they're to be applied, in the `Caplets` manifest attribute either as class names or as artifact coordinates.

### Empty Capsules and Capsule Wrapping

A capsule that contains no application (i.e., it's manifest has no `Application-Class`, `Application`, `Application-Name` etc.) is known as an *empty capsule*. Most caplets and, indeed, the Capsule project itself, are shipped as binaries which are essentially empty capsules. While you cannot run an empty capsule on its own, empty capsules can serve -- unmodified -- as *capsule wrappers* that wrap other capsules, or even un-capsuled applications. This is most useful when the empty, wrapper, capsule employs caplets to provide some special behavior to the wrapped capsule or application.

Here's how we use an empty capsule to launch an application stored in a local JAR:

    java -jar capsule.jar coolapp.jar

In both cases, the only requirement from `coolapp` is that it has a main class declared in its manifest. If coolapp is a capsule rather than a simple application, then our empty capsule's caplets will be applied to the wrapped capsule.

### "Really Executable" Capsules

A JAR file can be made "really executable" in UNIX/Linux/MacOS environments -- i.e. it can be run simply as `capsule.jar ARGS` rather than `java -jar capsule.jar ARGS` -- by [prepending a couple of shell script lines to the JAR](http://skife.org/java/unix/2011/06/20/really_executable_jars.html) (it turns out JAR files can tolerate any prepended headers).

Both [capsule-maven-plugin](https://github.com/chrischristo/capsule-maven-plugin) and  [gradle-capsule-plugin](https://github.com/danthegoodman/gradle-capsule-plugin) support creation of really executable capsules simply by setting a flag.

If you choose not to use one of the capsule plugins, then you can use the [really-executable-jars](https://github.com/brianm/really-executable-jars-maven-plugin) Maven plugin to make your capsule really executable (or if you're using the [capsule-maven-plugin](https://github.com/chrischristo/capsule-maven-plugin), just set the `buildExec` tag to true). In Gradle, this can be done by adding the following function to your build file:

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

## Caplets

You can customize many of the capsule's inner workings by creating a caplet -- *custom capsule*. A caplet is a subclass of the `Capsule` class that overrides some of its overridable methods.

Please consult Capsule's [Javadoc](http://puniverse.github.io/capsule/capsule/javadoc/Capsule.html) for specific documentation on custom capsules.

### Available Caplets

* [capsule-maven](https://github.com/puniverse/capsule-maven) - Resolves artifacts against a Maven repository if not embedded in the capsule.
* [capsule-shield](https://github.com/puniverse/capsule-shield) - Runs a capsule inside a Linux Container (experimental)
* [capsule-desktop](https://github.com/puniverse/capsule-desktop) - Turns capsules into native desktop apps (experimental).

## Launching, Manipulating, and Creating Capsules programmatically

The [capsule-util](http://puniverse.github.io/capsule/capsule-util/javadoc/) sub-project contains classes to create and interact with capsule's at runtime. See the Javadocs [here](http://puniverse.github.io/capsule/capsule-util/javadoc/).

## Reference

### Manifest Attributes

Everywhere the word "list" is mentioned, it is whitespace-separated.

* `Application-ID`: the application's unique ID (e.g. `com.acme.foo_app`)
* `Application-Version`: the application's version
* `Application-Name`: the human-readable name of the application
* `Application-Class`: the application's main class
* `Application`: the Maven coordinates of the application's main JAR or the path of the main JAR within the capsule
* `Script`: a startup script to be run *instead* of `Application-Class`, given as a path relative to the capsule's root
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
* `Native-Agents`: a list of native JVMTI agents used by the application; formatted \"agent\" or \"agent=arg1,arg2...\", where agent is either the path to a native library, without the platform-specific suffix, relative to the capsule root; the native library file(s) can be embedded in the capsule or listed as Maven native dependencies using the Native-Dependencies-... attributes.
* `Dependencies`: a list of Maven dependencies given as `groupId:artifactId:version[(excludeGroupId:excludeArtifactId,...)]`
* `Native-Dependencies`: a list of Maven dependencies consisting of `.so` artifacts for Linux; each item can be a key-value pair ('=' separated), with the second component being a new name to give the artifact.
the second component being a new name to give the download artifact. The artifacts will be downloaded and copied into the application's cache directory.
* `Capsule-Log-Level`: sets the default log level for the Capsule launcher (which can be overridden with `-Dcapsule.log`); can be one of: `NONE`, `QUIET` (the default), `VERBOSE`, or `DEBUG`.
* `Caplets`: a list of names of caplet classes -- if embedded in the capsule -- or Maven coordinates of caplet artifacts that will be applied to the capsule in the order they are listed.

### Manifest Variables

* `$CAPSULE_JAR`: the full path to the capsule JAR
* `$CAPSULE_DIR`: the full path to the application cache directory, if the capsule is extracted.
* `$JAVA_HOME`: the full path to the Java installation which will be used to launch the app

### Actions

Actions are system properties that, if defined, perform an action *other* than launching the application.

* `capsule.version`: prints the application ID and the Capsule version
* `capsule.jvms`: prints the JVM installations it can locate with their versions
* `capsule.modes`: prints all available capsule modes

### System Properties

* `capsule.mode`: if set, the capsule will be launched in the specified mode (see *Capsule Configuration and Modes*)
* `capsule.log`: can be set to 'none'/'quiet' (default)/'verbose'/'debug'
* `capsule.jvm.args`: specifies JVM arguments for the capsule's app process.
* `capsule.reset`: if set, forces re-extraction of the capsule, where applies, and/or re-downloading of SNAPSHOT dependencies
* `capsule.java.home`: forces the capsule to use the given path to a Java installation when launching the application.
* `capsule.java.cmd`: firces the capsule to use the give executable to launch the JVM.

Capsule defines these system properties in the application's process:

* `capsule.app`: the app ID
* `capsule.jar`: the full path to the capsule's JAR
* `capsule.dir`: if the JAR has been extracted, the full path of the application cache (use generally discouraged).

Capsule defines these system properties in the capsule (launcher) process (to be queried by `jcmd`):

* `capsule.app.pid`: the child (application) process PID; available only in POSIX environments.

### Environment Variables

* `CAPSULE_CACHE_NAME`: sets the *name* of the root of Capsule's cache in the default location (`~` on Unix, `%LOCALAPPDATA%` on Windows)
* `CAPSULE_CACHE_DIR`: sets the full path of the Capsule's cache

Capsule defines these variables in the application's environment:

* `CAPSULE_APP`: the app ID
* `CAPSULE_JAR`: the full path to the capsule's JAR
* `CAPSULE_DIR`: if the JAR has been extracted, the full path of the application cache.

These values can also be accessed with `$VARNAME` in any capsule manifest attributes.

### Javadoc

* [capsule](http://puniverse.github.io/capsule/capsule/javadoc/Capsule.html)
* [capsule-util](http://puniverse.github.io/capsule/capsule-util/javadoc/)

## License

    Copyright (c) 2014-2015, Parallel Universe Software Co. and Contributors. All rights reserved.

    This program and the accompanying materials are licensed under the terms
    of the Eclipse Public License v1.0 as published by the Eclipse Foundation.

        http://www.eclipse.org/legal/epl-v10.html

As Capsule does not link in any way with any of the code bundled in the JAR file, and simply treats it as raw data, Capsule is no different from a self-extracting ZIP file (especially as manually unzipping and examining the JAR's contents is extremely easy). Capsule's own license, therefore, does not interfere with the licensing of the bundled software.

In particular, even though Capsule's license is incompatible with the GPL/LGPL, it is permitted to distribute GPL programs packaged as capsules, as Capsule is simply a packaging medium and an activation script, and does not restrict access to the packaged GPL code. Capsule does not add any capability to, nor removes any from the bundled application. It therefore falls under the definition of an "aggregate" in the GPL's terminology.
