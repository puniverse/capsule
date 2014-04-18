# Capsule

## A simple single-file container for JVM applications


The application is completely oblivious to Capsule.

The JAR file must not contain application `.class` files not packaged within JARs.


Mandatory:

* `Main-Class` : `Capsule`
* `App-Class` - the only mandatory attribute

Optional:

* `App-Name`
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

    "capsule.reset"
    "capsule.version"
    "capsule.log"
    "capsule.tree"
 


 ## Example

Embedded dependencies:

``` groovy
task capsule2(type: Jar, dependsOn: jar) {
    archiveName = "foo.jar"
    from jar
    from { configurations.runtime }
    
    from(configurations.capsule.collect { zipTree(it) }) {
        include 'Capsule*.class'
    }
    
    manifest { 
        attributes(
        'Main-Class'  : 'Capsule',
            'App-Class'   : mainClassName,
            'Min-Java-Version' : '1.8.0',
            'JVM-Args' : run.jvmArgs.join(' '),
            'System-Properties' : run.systemProperties.collect { k,v -> "$k=$v" }.join(' '),
            'Java-Agents' : configurations.quasar.iterator().next().getName()
        )
    }
}
```

External dependencies:

2MB overhead

``` groovy
// translates gradle dependencies to capsule dependencies
def getDependencies(config) {
    return config.getAllDependencies().collect { 
        def res = it.group + ':' + it.name + ':' + it.version + (!it.artifacts.isEmpty() ? ':' + it.artifacts.iterator().next().classifier : '')
        if(!it.excludeRules.isEmpty()) {
            res += "(" + it.excludeRules.collect { it.group + ':' + it.module }.join(',') + ")"
        }
        return res
    }
}

task capsule1(type: Jar, dependsOn: jar) {
    archiveName = "foo.jar"
    from jar
    from { configurations.capsule.collect { zipTree(it) } }
    
    manifest { 
        attributes(
        'Main-Class'  :   'Capsule',
            'App-Class'   : mainClassName,
            'Min-Java-Version' : '1.8.0',
            'JVM-Args' : run.jvmArgs.join(' '),
            'System-Properties' : run.systemProperties.collect { k,v -> "$k=$v" }.join(' '),
            'Java-Agents' : getDependencies(configurations.quasar).iterator().next(),
            'Dependencies': getDependencies(configurations.runtime).join(' ')
        )
    }
}
```

## Differnces from [One-Jar](http://one-jar.sourceforge.net/)

 * Might interfere with application (esp. those using tricky things)
 * Does not support Java instrumentation agents, or any command-line arguments
 * No support for maven dependencies

 Disadvantages:

 * Requires writing to the filesystem


 ## License