# Capsule

## A simple single-file container for JVM applications


The application is completely oblivious to Capsule.

The JAR file must not contain application `.class` files not packaged within JARs.


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
            'Application-Class'   : mainClassName,
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
            'Application-Class'   : mainClassName,
            'Min-Java-Version' : '1.8.0',
            'JVM-Args' : run.jvmArgs.join(' '),
            'System-Properties' : run.systemProperties.collect { k,v -> "$k=$v" }.join(' '),
            'Java-Agents' : getDependencies(configurations.quasar).iterator().next(),
            'Dependencies': getDependencies(configurations.runtime).join(' ')
        )
    }
}
```

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