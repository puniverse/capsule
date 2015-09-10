---
layout: post
title: User Guide
---

* TOC
{:toc}

## Getting Capsule

[Download](https://github.com/puniverse/capsule/releases)

or:

    co.paralleluniverse:capsule:1.0

on Maven Central.

## Building Capsule

    ./gradlew install

## Support

Discuss Capsule on the capsule-user [Google Group/Mailing List](https://groups.google.com/forum/#!forum/capsule-user)

## Build-Tool Plugins

Using a build-tool plugin may simplify the capsule creation process, especially if you're doing something clever. You are encourage to use the appropriate community-contributed plugin for you build-tool of choice:

* Maven: [https://github.com/chrischristo/capsule-maven-plugin](https://github.com/chrischristo/capsule-maven-plugin)
* Gradle: [https://github.com/danthegoodman/gradle-capsule-plugin](https://github.com/danthegoodman/gradle-capsule-plugin)
* Leiningen: [https://github.com/circlespainter/lein-capsule](https://github.com/danthegoodman/gradle-capsule-plugin)
* SBT: Contributions are welcome!

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
    Premain-Class: Capsule
    Main-Class: Capsule
    Application-ID: com.acme.foo
    Application-Version: 1.0
    Application-Class: foo.Main
    Min-Java-Version: 1.8.0
    JVM-Args: -server
    System-Properties: foo.bar.option=15 my.logging=verbose
    Java-Agents: agent1.jar

We embed the application JAR (`app.jar`), containing the class `foo.Main` as well as all dependency JARs into the capsule JAR (without extracting them). We also place the `Capsule` class at the root of JAR. Then, in the JAR's manifest, we declare `Capsule` as both the pre-main and main class. This is the class that will be executed when we run `java -jar foo.jar`. The `Application-Class` attribute tells Capsule which class to run in the new JVM process, and we set it to the same value, `mainClass` used by the build's `run` task. The `Min-Java-Version` attribute specifies the JVM version that will be used to run the application. If this version is newer than the Java version used to launch the capsule, Capsule will look for an appropriate JRE installation to use (a maximum version can also be specified). We then list some JVM arguments, system properties and even a Java agent.

When than launch the capsule with `java -jar foo.jar`. When it runs, it will try to find a JRE 8 (or higher) installation on our machine -- whether or not that was the Java version used to launch the capsule -- and then start a new JVM process, configured with the given flags and agent, that will run our application.

## Launching a Capsule

As mentioned before, `java -jar app.jar [app arguments]` will launch the application.

The `java -Dcapsule.version -jar app.jar` command will print the application ID, the version of Capsule used, and then exit without launching the app.

Adding `-Dcapsule.log=verbose` or `-Dcapsule.log=debug`  before `-jar` will print information about Capsule's action.

## A Capsule's Structure and Manifest

A capsule is a JAR file with a manifest -- containing meta-data describing the application -- and some embedded files. The most minimal (probably useless) capsule contains just the `Capsule.class` file, and a `META-INF/MANIFEST.MF` file with the following lines:

    Main-Class: Capsule
    Premain-Class: Capsule

The existence of `Capsule.class` in the JAR's root, as well as those lines in the manifest is what makes a JAR into a capsule (but not yet a launchable one, as we haven't yet specified an attribute that tells the capsule what to run). The capsule's manifest describes everything the application requires in order to launch our application. In the next sections we'll learn the use of various attributes.

## Paths and Artifact Coordinates

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

## The Application

At the very least, a capsule must have an attribute specifying what application to run. It must be one of:

    Application-Class : [the applications's main class, found in the capsule or one of its dependencies]

which specifies the application's main class, or,

    Application : [the coordinates of the application's main executable JAR or the path of the main executable JAR within the capsule]

or,

    Application-Script : [a platform specific startup shell/batch-script]

If a script is used, a different one should be listed for each OS type, as we'll see below.

## The Capsule ID

Every capsule has an ID comprised of an *application ID* and an *application version*. If the `Application` attribute is used, and the application is given as artifact coordinates, the application ID will be `<group>.<artifact>` and the version will be the artifact's version. Otherwise, the ID and version should be defined with the `Application-ID` and `Application-Version` attributes respectively. `Application-ID` must not contains spaces and should be unique; to ensure uniqueness, it should follow the convention of Java package naming and begin with the author's or application's revered web domain. A good ID is something like `com.acme.foo`.

It is also good practice to define the application's human-readable name, like, "The Foo App", in the `Application-Name` attribute.

In most circumstances, the capsule will launch without specifying any of the attributes described in this section, but it is highly recommended that you define them nonetheless.

## Capsule's Cache

By default, Capsule will extract the capsule JAR's contents -- except for class files placed directly in the JAR -- into a cache directory. This, however, is an implementation detail and should not be relied upon by applications.

The location of the cache directory is, by default, at `~/.capsule/` on Unix/Linux/Mac OS machines, and at `%USERPROFILE%\AppData\Local\capsule\` on Windows. The application caches are placed in the `apps/CAPSULE_ID` subdirectory of the cache.

If the capsule fails to write to the cache directory (say, the user lacks permission), the cache will be placed in a default, platform specific, temp-file directory, and deleted upon the application's termination. Otherwise, the capsule will be extracted once. The following run will compare the JAR's modification date with that of the cache, and will re-write the cache only if the capsule JAR is younger. You can force re-extraction of the capsule with `-Dcapsule.reset=true`.

The location of the Capsule cache can be changed with environment variables: setting `CAPSULE_CACHE_NAME` determines the name of the topmost Capsule cache dir (i.e. "capsule" by default), while `CAPSULE_CACHE_DIR` can be used to set a precise path for the cache (e.g. `/tmp/capsule/`).

The location of the cache and the location of the JAR are communicated to the application through the `capsule.dir` and `capsule.jar` system properties respectively. Capsule defines these properties automatically, and the application may use them, for example, to find extracted resources. In addition, those two filesystem paths can be used within the manifest itself to set various values (system properties, environment variables, etc) by referencing them with `$CAPSULE_DIR` and `$CAPSULE_JAR` respectively.

Applications should not generally assume anything about the `capsule.dir` property defined for them by their capsule. Depending on caplet behavior or even changes to the default capsule implementation, that property may be empty; if it isn't the directory cannot be assumed to have write permissions.

## Modes, Platform- and Version-Specific Configuration

A capsule is configured by attributes in its manifest. Manifest attributes specify which Java version to use when launching the application, what agents to load, the JVM arguments, system properties, dependencies and more.

It is possible to specify different values for each of the configurations -- to be selected at launch time -- by defining attributes in special manifest *sections*. Currently, there are three types of sections supported: OS, JRE version and mode. Manifest sections better allow a capsule to adapt to its environment, but are by no means necessary for most capsules.

An OS section is selected based on the OS launching the capsule, and allows defining attributes that are different based on the OS; this is useful for specifying a launch script (in the `Application-Script` attribute, or native libraries in the `Native-Dependencies`/`Native-Agents` attributes), but can be used for any attribute. The name of the section is the OS name, and can be one of: `Windows`, `MacOS`, `Linux`, `Solaris`, `Unix` or `POSIX`. The `Unix` section is in effect for any Unix-like system (including Linux and Solaris), and the `POSIX` section is in effect for Unix-like systems as well as MacOS. So a simple launch script can be defined so:

    Name: Windows
    Application-Script: run.bat

    Name: POSIX
    Application-Script: run.sh

A JRE version section is selected based on the major Java version chosen by capsule to run the application (as we'll see later, a capsule can require a specific Java version, or a minimum version). This kind of section is rare, but can be useful for applications that might use different libraries depending on the JRE version used. This kind of section is named `Java-N`, where N is the major version number (6, 7, 8 etc.).

Modes are a little different, as they are not selected automatically, but by the user when the capsule is launched. Modes are useful in providing different configurations the user can choose from, or even different applications within the same capsule. You define a mode by creating a section that has the mode's name. For obvious reasons, modes can't be named in a way that would confuse them with OS or JRE sections, so, for example, you can't name a mode "Windows", nor can you name it, `Java-9`. Here's an example of the `Special` mode:

    Application-Class: foo.Main

    Name: Special
    Application-Class: foo.Special

To further specialize modes based on OS or JRE version, simply add `-` and the OS/JRE name to the modes, like so:

    Name: Windows
    Application-Script: run.bat

    Name: POSIX
    Application-Script: run.sh

    Name: Special-Windows
    Application-Script: special.bat

    Name: Special-POSIX
    Application-Script: special.sh

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

## Selecting the Java Runtime

Two manifest attributes determine which Java installation Capsule will use to launch the application. `Min-Java-Version` (e.g. `1.7.0_50` or `1.8.0`) is the lowest Java version to use, while `Java-Version` (e.g. `1.6`) is the highest *major* Java version to use. One, both, or neither of these attributes may be specified in the manifest.

First, Capsule will test the current JVM (used to launch the capsule) against `Min-Java-Version` and `Java-Version` (if they're specified). If the version of the current JVM matches the requested range, it will be used to launch the application. If not, Capsule will search for other JVM installations, and use the one with the highest version that matches the requested range. If no matching installation is found, the capsule will fail to launch.

It is also possible to require a minimal update version, say, in cases where the update fixes a bug affecting the application. Because the bug may have been fixed in two different major versions in two different updates (e.g., for Java 7 in update 85, and for Java 8 in update 21), the minimal update required is specified per major Java version, in the `Min-Update-Version` attribute. For example

    Min-Update-Version: 7=85 1.8=21

The major version can be given as a single digit (`7`) or as a "formal" Java version (`1.7`, or, `1.7.0`). The updates are specified in a whitespace separated list of entries, each entry containing a key (the major version) and a value (the minimum update) as a `=` separated pair.

If the `JDK-Required` attribute is set to `true`, Capsule will only select JDK installations.

Whatever the `Min-Java-Version`, `Java-Version`, or `JDK-Required` attributes specify, launching the capsule with the `capsule.java.home` system property, will use whatever Java installation is specified by the property, for example: `java -Dcapsule.java.home=/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home -jar app.jar`.

Finally, setting the `capsule.java.cmd` system property with the path to the executable which will be used to launch the JVM, overrides any JRE selection mechanism.

Running `java -Dcapsule.jvms -jar app.jar` will list all Java installations Capsule can find, and then quit without launching the app.

## Class Paths and Native Libraries

By default, Capsule sets the application's class path to the capsule JAR itself and every JAR file placed in the capsule's root -- in some unspecified (but constant) order. If the capsule contains an `Application` attribute, the jar(s) referenced by the attribute will be added to the classpath first.

The classpath, however, can be customized by the `App-Class-Path`/`Dependencies` attribute, which can be given an ordered (space separated) list of JARs and/or directories relative to the capsule JAR root, and/or artifact coordinates. The `Class-Path` and `Dependencies` attributes are similar, but not identical. If the `Class-Path` attribute is defined, then the JARs in the capsule's root are not automatically added to the class path (unless explicilty listed in the attribute), while the `Dependencies` attribute simply adds them to the class path. Also, the `Dependencies` attribute conventionally lists artifact coordinates, while the `App-Class-Path` attribute usually lists file paths, but this is not enforced, and either style can be used for both.

For convenience, when specifying the class path, glob-pattern wildcards (`*`, `?`) may be used.

In addition to setting the application classpath, you can also define its boot classpath. The `Boot-Class-Path` attribute is, similar to the `App-Class-Path` attribute, an ordered, space separated list of JARs and/or directories relative to the capsule's root and/or artifact coordinates, that will become the application's boot classpath. If you don't want to replace the default Java boot classpath, but simply to tweak it, The `Boot-Class-Path-P` attribute can be used to specify a classpath to be prepended to the default boot classpath, and the `Boot-Class-Path-A` attribute can specify a class path that will be appended to the default.

If the capsule is launched with a `-Xbootclasspath` option, it will override any setting by the capsule's manifest.

The `Library-Path-A` manifest attribute can list JARs or directories (relative to the capsule's root) that will be appended to the application's native library path. Similarly, `Library-Path-P`, can be used to prepend JARs or directories to the default native library path.

Native dependencies can be specified in the `Native-Dependencies` attribute. A native dependency is written as a plain dependency but can be followed by an equal-sign and a new filename to give the artifact once downloaded (e.g.: `com.acme:foo-native-linux-x64:1.0,foo-native.so`). Each native artifact must be a single native library, with a suffix matching the OS (`.so` for Linux, `.dll` for windows, `.dylib` for Mac). The native libraries are downloaded and copied into the application cache (and renamed if requested).

## JVM Arguments, System Properties, Environment Variables and Agents

The `JVM-Args` manifest attribute can contain a space-separated list of JVM argument that will be used to launch the application. Any JVM arguments supplied during the capsule's launch, will override those in the manifest. For example, if the `JVM-Args` attribute contains `-Xmx500m`, and the capsule is launched with `java -Xmx800m -jar app.jar`, then the application will be launched with the `-Xmx800m` JVM argument.

JVM arguments listed on the command line apply both to the Capsule launch process as well as the application process (see *The Capsule Execution Process*). Sometimes this is undesirable (e.g. when specifying a debug port, which can only apply to a single process or a port collision will ensue). JVM arguments for at the application process (only) can be supplied by adding `-Dcapsule.jvm.args="MY JVM ARGS"` to the command line.

The `Args` manifest attribute can contain a space-separated list of command line arguments to be passed to the application; these will be prepended to any arguments passed to the capsule at launch.

The `System-Properties` manifest attribute can contain a space-separated list of system properties that will be defined in the launched application. The properties are listed as `property=value` pairs (or just `property` for an empty value). Any system properties supplied during the capsule's launch, will override those in the manifest. For example, if the `JVM-Args` attribute contains `name=Mike`, and the capsule is launched with `java -Dname=Jason -jar app.jar`, then the application will see the `name` system-property defined as `Jason`.

The `Environment-Variables` manifest attribute, is, just like `System-Properties`, a space-separated list of `var=value` pairs (or just `var` for an empty value). The specified values do not overwrite those already defined in the environment, unless they are listed as `var:=value` rather than `var=value`.

The `Java-Agents` attribute can contain a space-separated list with the names of JARs containing Java agents. The agents are listed as `agent=params` or just `agent`, where `agent` is either the path of a JAR embedded in the capsule, relative to the capsule JAR's root, or the coordinates of a Maven dependency.

Similarly, the `Native-Agents` attribute may contain a list of native JVMTI agents to be used by the application. The formatting is the same as that for `Java-Agents`, except that `agent` is the path to the native library -- minus the extension -- relative to the capsule root. Unlike in the case of `Java-Agents`, listing Maven dependencies is not allowed, but the same result can be achieved by listing the depndencies in the `Native-Dependencies-...` attributes and then referring to the library file's name in the `Native-Agents` attribute.

Remember that values listed in all these configuration values can contain the `$CAPSULE_DIR` and `$CAPSULE_JAR` variables, discussed in the *Capsule's Cache* section.

## Scripts

While Capsule mostly makes startup scripts unnecessary, in some circumstances they can be useful. Capsule allows placing platform-specific scripts into the capsule JAR, and executing them instead of launching a JVM and running `Application-Class`. The `Application-Script` attribute specifies the location (relative to the capsule JAR's root) of the script, and should generally by defined in a platform-specific section.

The scripts can make use of the `CAPSULE_DIR` environment variable to locate capsule files. In addition, Capsule will choose the JVM installation based on the version requirements, and set the `JAVA_HOME` environment variable for the script appropriately.

## Security Manager

The `Security-Policy` attribute specifies a Java [security policy file](http://docs.oracle.com/javase/7/docs/technotes/guides/security/PolicyFiles.html) to use for the application. Its value is the location of the security file relative to the capsule JAR root. The `Security-Policy-A` achieves a similar purpose, only the security policy specified is added to the default one rather than replaces it. Finally, the `Security-Manager` attribute can specify a class to be used as the security manager for the application.

If any of these three properties is set, a security manager will be in effect when the application runs. If the `Security-Manager` attribute is not set, the default security manager will be used.

## Applying Caplets

To apply caplets to the capsule you list them, in the order they're to be applied, in the `Caplets` manifest attribute either as class names or as artifact coordinates. If given as artifact coordinates, the caplet JAR file should be placed, unextracted, in the capsule's root, or, preferably in the `capsule` directory of the capsule (so as not to be placed on the application's classpath).

## Empty Capsules and Capsule Wrapping

A capsule that contains no application (i.e., it's manifest has no `Application-Class`, `Application`, `Application-Name` etc.) is known as an *empty capsule*. Most caplets and, indeed, the Capsule project itself, are shipped as binaries which are essentially empty capsules. While you cannot run an empty capsule on its own, empty capsules can serve -- unmodified -- as *capsule wrappers* that wrap other capsules, or even un-capsuled applications. This is most useful when the empty, wrapper, capsule employs caplets to provide some special behavior to the wrapped capsule or application.

Here's how we use an empty capsule to launch an application stored in a local JAR:

    java -jar capsule.jar coolapp.jar

In both cases, the only requirement from `coolapp` is that it has a main class declared in its manifest. If coolapp is a capsule rather than a simple application, then our empty capsule's caplets will be applied to the wrapped capsule.

## "Really Executable" Capsules

A JAR file can be made "really executable" in UNIX/Linux/MacOS environments -- i.e. it can be run simply as `capsule.jar ARGS` rather than `java -jar capsule.jar ARGS` -- by [prepending a couple of shell script lines to the JAR](http://skife.org/java/unix/2011/06/20/really_executable_jars.html) (it turns out JAR files can tolerate any prepended headers).

Both [capsule-maven-plugin](https://github.com/chrischristo/capsule-maven-plugin) and  [gradle-capsule-plugin](https://github.com/danthegoodman/gradle-capsule-plugin) support creation of really executable capsules simply by setting a flag.

If you choose not to use one of the capsule plugins, then you can use the [really-executable-jars](https://github.com/brianm/really-executable-jars-maven-plugin) Maven plugin to make your capsule really executable (or if you're using the [capsule-maven-plugin](https://github.com/chrischristo/capsule-maven-plugin), just set the `buildExec` tag to true). In Gradle, this can be done by adding the following function to your build file:

~~~ groovy
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
~~~

and then

~~~ groovy
capsule.doLast { task -> reallyExecutable(task) }
~~~

## The Capsule Execution Process

When a capsule is launched, two processes are involved: first, a JVM process runs the capsule launcher, which then starts up a second, child process that runs the actual application. The two processes are linked so that killing or suspending one, will do the same for the other. On UNIX systems (Linux, Mac, etc.), the launcher process makes public the identity of the child process by setting the `capsule.app.pid` system property, which can be queried with the `jcmd` command. Suppose the capsule's pid is 1234, then the child (app) process pid can be obtained with the following shell command:

    jcmd 1234 VM.system_properties  | grep capsule.app.pid | cut -f 2 -d =

While this model works well enough in most scenarios, sometimes it is desirable to directly launch the process running the application, rather than indirectly. This is supported by "capsule trampoline", and is available for really executable capsules on UNIX systems only. To take advantage of capsule trampolining, use the `capsule/trampoline-execheader.sh` executable header (rather than `capsule/execheader.sh`) when creating the really executable capsule.

## Writing Caplets

You can customize many of the capsule's inner workings by creating a caplet -- *custom capsule*. A caplet is a subclass of the `Capsule` class that overrides some of its overridable methods.

Please consult Capsule's [Javadoc](/javadoc/capsule/Capsule.html) for specific documentation on custom capsules.
