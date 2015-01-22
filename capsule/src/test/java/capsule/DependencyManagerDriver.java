/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A small standalone program to exercise the dependency manager implementation.
 */
public class DependencyManagerDriver {
    private static final Path DEFAULT_LOCAL_MAVEN = Paths.get(System.getProperty("user.home"), ".m2", "repository");

    public static void main(String[] args) {
        DependencyManager dm;

        dm = new DependencyManagerImpl(DEFAULT_LOCAL_MAVEN, false, 3, "");
        dm.setRepos(null, true);
        
        resolve(dm, "co.paralleluniverse:quasar-core:LATEST");
        resolve(dm, "co.paralleluniverse:quasar-core:(0.3.0,0.5.0-SNAPSHOT)");
        resolve(dm, "co.paralleluniverse:quasar-core:0.5.0");
        
        // dm = new DependencyManagerImpl(DEFAULT_LOCAL_MAVEN, Arrays.asList("foo(https://repo1.maven.org/maven2/)"), false, true, 3);
    }

    static void resolve(DependencyManager dm, String coords) {
        System.out.println("==================");
        System.out.println("Coords: " + coords);
        String latestVersion = dm.getLatestVersion(coords, "jar");
        System.out.println("Latest: " + latestVersion);
        List<Path> ps = dm.resolveDependency(coords, "jar");
        System.out.println("Resolved: " + ps);
        dm.printDependencyTree(Collections.singletonList(coords), "jar", System.out);
        System.out.println();
        System.out.println();
    }
}
