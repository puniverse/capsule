/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public interface DependencyManager {
    void setRepos(List<String> repos, boolean allowSnapshots);
    
    List<Path> resolveDependencies(List<String> coords, String type);

    List<Path> resolveDependency(String coords, String type);

    String getLatestVersion(String coords, String type);

    void printDependencyTree(List<String> coords, String type, PrintStream out);

    void printDependencyTree(String coords, String type, PrintStream out);

    static final String PROP_USER_HOME = "user.home";

    static final Path DEFAULT_LOCAL_MAVEN = Paths.get(System.getProperty(PROP_USER_HOME), ".m2");

    static final Map<String, String> WELL_KNOWN_REPOS = Lang.stringMap(
            "central", "central(https://repo1.maven.org/maven2/)",
            "central-http", "central(http://repo1.maven.org/maven2/)",
            "jcenter", "jcenter(https://jcenter.bintray.com/)",
            "jcenter-http", "jcenter(http://jcenter.bintray.com/)",
            "local", "local(file:" + DEFAULT_LOCAL_MAVEN.resolve("repository") + ")"
    );
}
