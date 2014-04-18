# Capsule

## A simple single-file container for JVM applications

The JAR file must not contain application `.class` files not packaged within JARs.

 * `App-Class` - the only mandatory attribute
 * `App-Version`
 * `Min-Java-Version`
 * `App-Class-Path` default: the launcher jar root and every jar file found in the launcher jar's root.
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

    private static final String RESET_PROPERTY = "launcher.reset";
    private static final String VERSION_PROPERTY = "launcher.version";
    private static final String LOG_PROPERTY = "launcher.log";
    private static final String TREE_PROPERTY = "launcher.tree";
    
 ## Differnces from [One-Jar](http://one-jar.sourceforge.net/)

 * Might interfere with application (esp. those using tricky things)
 * Does not support Java instrumentation agents, or any command-line arguments
 * No support for maven dependencies

 Disadvantages:

 * Requires writing to the filesystem


 ## License