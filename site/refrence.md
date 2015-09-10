---
layout: post
---

# Reference

## Manifest Attributes

Everywhere the word "list" is mentioned, it is whitespace-separated.

* `Application-ID`: the application's unique ID (e.g. `com.acme.foo_app`)
* `Application-Version`: the application's version
* `Application-Name`: the human-readable name of the application
* `Application-Class`: the application's main class
* `Application`: the Maven coordinates of the application's main JAR or the path of the main JAR within the capsule
* `Application-Script`: a startup script to be run *instead* of `Application-Class`, given as a path relative to the capsule's root
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

## Manifest Variables

* `$CAPSULE_JAR`: the full path to the capsule JAR
* `$CAPSULE_DIR`: the full path to the application cache directory, if the capsule is extracted.
* `$JAVA_HOME`: the full path to the Java installation which will be used to launch the app

## Actions

Actions are system properties that, if defined, perform an action *other* than launching the application.

* `capsule.version`: prints the application ID and the Capsule version
* `capsule.jvms`: prints the JVM installations it can locate with their versions
* `capsule.modes`: prints all available capsule modes

## System Properties

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

## Environment Variables

* `CAPSULE_CACHE_NAME`: sets the *name* of the root of Capsule's cache in the default location (`~` on Unix, `%LOCALAPPDATA%` on Windows)
* `CAPSULE_CACHE_DIR`: sets the full path of the Capsule's cache


Capsule defines these variables in the application's environment:

* `CAPSULE_APP`: the app ID
* `CAPSULE_JAR`: the full path to the capsule's JAR
* `CAPSULE_DIR`: if the JAR has been extracted, the full path of the application cache.

These values can also be accessed with `$VARNAME` in any capsule manifest attributes.

