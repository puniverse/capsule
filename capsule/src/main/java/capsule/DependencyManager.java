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
import java.util.List;

public interface DependencyManager {
    List<Path> resolveDependencies(List<String> coords, String type);

    List<Path> resolveDependency(String coords, String type);

    List<Path> resolveRoot(String coords, String type);

    String getLatestVersion(String coords, String type);

    void printDependencyTree(List<String> coords, String type, PrintStream out);

    void printDependencyTree(String coords, String type, PrintStream out);
}
