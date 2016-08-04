# *Capsule*<br/>Dead-Simple Packaging and Deployment for JVM Applications
[![Build Status](http://img.shields.io/travis/puniverse/capsule.svg?style=flat)](https://travis-ci.org/puniverse/capsule) [![Coverage](https://coveralls.io/repos/puniverse/capsule/badge.svg?branch=master)](https://coveralls.io/r/puniverse/capsule?branch=master) [![Dependency Status](https://www.versioneye.com/user/projects/539704a483add7f80a000030/badge.svg?style=flat)](https://www.versioneye.com/user/projects/539704a483add7f80a000030) [![Version](http://img.shields.io/badge/version-1.0.3-blue.svg?style=flat)](https://github.com/puniverse/capsule/releases) [![License](http://img.shields.io/badge/license-EPL-blue.svg?style=flat)](https://www.eclipse.org/legal/epl-v10.html)


Capsule is a packaging and deployment tool for JVM applications. A capsule is a single executable JAR that contains everything your application needs to run either in the form of embedded files or as declarative metadata. It can contain your JAR artifacts, your dependencies and resources, native libraries, the require JRE version, the JVM flags required to run the application well, Java or native agents and more. In short, a capsule is a self-contained JAR that knows everything there is to know about how to run your application the way it's meant to run.

One way of thinking about a capsule is as a fat JAR on steroids (that also allows native libraries and never interferes with your dependencies) and a declarative startup script rolled into one; another, is to see it is as the deploy-time counterpart to your build tool. Just as a build tool manages your build, Capsule manages the launching of your application.

But while plain capsules are cool and let you ship any JVM application -- no matter how complex -- as a single executable JAR, caplets make capsules even more powerful.

## Documentation

[Capsule website](http://www.capsule.io)

## Support

Discuss Capsule on the capsule-user [Google Group/Mailing List](https://groups.google.com/forum/#!forum/capsule-user)

## Getting Started

[Download](https://github.com/puniverse/capsule/releases)

or:

    co.paralleluniverse:capsule:1.0.3

or:

Clone the repository and

    gradle install

## License

    Copyright (c) 2014-2016, Parallel Universe Software Co. and Contributors. All rights reserved.

    This program and the accompanying materials are licensed under the terms
    of the Eclipse Public License v1.0 as published by the Eclipse Foundation.

        http://www.eclipse.org/legal/epl-v10.html

As Capsule does not link in any way with any of the code bundled in the JAR file, and simply treats it as raw data, Capsule is no different from a self-extracting ZIP file (especially as manually unzipping and examining the JAR's contents is extremely easy). Capsule's own license, therefore, does not interfere with the licensing of the bundled software.

In particular, even though Capsule's license is incompatible with the GPL/LGPL, it is permitted to distribute GPL programs packaged as capsules, as Capsule is simply a packaging medium and an activation script, and does not restrict access to the packaged GPL code. Capsule does not add any capability to, nor removes any from the bundled application. It therefore falls under the definition of an "aggregate" in the GPL's terminology.
