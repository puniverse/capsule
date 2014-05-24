/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

public class PomReader {
    private final Model pom;

    public PomReader(InputStream is) {
        try {
            this.pom = new MavenXpp3Reader().read(is);
        } catch (Exception e) {
            throw new RuntimeException("Error trying to read pom.", e);
        }
    }

    public String getArtifactId() {
        return pom.getArtifactId();
    }

    public String getGroupId() {
        return pom.getGroupId();
    }

    public String getVersion() {
        return pom.getVersion();
    }

    public String getId() {
        return pom.getId();
    }

    public List<String> getRepositories() {
        final List<Repository> repos = pom.getRepositories();
        if (repos == null)
            return null;
        final List<String> repositories = new ArrayList<String>(repos.size());
        for (Repository repo : repos)
            repositories.add(repo.getUrl());
        return repositories;
    }

    public List<String> getDependencies() {
        final List<Dependency> deps = pom.getDependencies();
        if (deps == null)
            return null;

        final List<String> dependencies = new ArrayList<String>(deps.size());
        for (Dependency dep : deps) {
            if (includeDependency(dep))
                dependencies.add(dep2desc(dep));
        }
        return dependencies;
    }

    private static boolean includeDependency(Dependency dep) {
        if (dep.isOptional())
            return false;
        if ("co.paralleluniverse".equals(dep.getGroupId()) && "capsule".equals(dep.getArtifactId()))
            return false;

        switch (dep.getScope().toLowerCase()) {
            case "compile":
            case "runtime":
                return true;
            default:
                return false;
        }
    }

    private static String dep2desc(Dependency dep) {
        return dep2coords(dep) + exclusions2desc(dep);
    }

    private static String dep2coords(Dependency dep) {
        return dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion()
                + (dep.getClassifier() != null && !dep.getClassifier().isEmpty() ? ":" + dep.getClassifier() : "");
    }

    private static String exclusions2desc(Dependency dep) {
        List<Exclusion> exclusions = dep.getExclusions();
        if (exclusions == null || exclusions.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (Exclusion ex : exclusions)
            sb.append(exclusion2coord(ex)).append(',');
        sb.delete(sb.length() - 1, sb.length());
        sb.append(')');

        return sb.toString();
    }

    private static String exclusion2coord(Exclusion ex) {
        return ex.getGroupId() + ":" + ex.getArtifactId();
    }
}
