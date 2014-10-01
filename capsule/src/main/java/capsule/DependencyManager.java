/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. and Contributors. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static java.util.Collections.unmodifiableMap;

public interface DependencyManager {
    void setRepos(List<String> repos, boolean allowSnapshots);

    List<Path> resolveDependencies(List<String> coords, String type);

    List<Path> resolveDependency(String coords, String type);

    String getLatestVersion(String coords, String type);

    void printDependencyTree(List<String> coords, String type, PrintStream out);

    void printDependencyTree(String coords, String type, PrintStream out);

    static final Map<String, String> WELL_KNOWN_REPOS = unmodifiableMap(new HashMap<String, String>() {
        {
            put("central", "central(https://repo1.maven.org/maven2/)");
            put("central-http", "central(http://repo1.maven.org/maven2/)");
            put("jcenter", "jcenter(https://jcenter.bintray.com/)");
            put("jcenter-http", "jcenter(http://jcenter.bintray.com/)");
        }
    });
}
