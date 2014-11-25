/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. and Contributors. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
/*
 * *****************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Sonatype, Inc. - initial API and implementation
 ******************************************************************************
 */
package capsule;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

/**
 * A dependency visitor that dumps the graph to the console.
 */
public final class ConsoleDependencyGraphDumper implements DependencyVisitor {
    private final PrintStream out;
    private final Deque<ChildInfo> childInfos = new ArrayDeque<ChildInfo>();
    private final Set<Artifact> visitedNodes = Collections.newSetFromMap(new HashMap<Artifact, Boolean>(512)); // new IdentityHashMap<DependencyNode, Boolean>

    public ConsoleDependencyGraphDumper(PrintStream out) {
        this.out = out;
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
        final boolean visited = !visit(node);
        final String nstr = formatNode(node);
        if (nstr != null)
            out.println(formatIndentation() + nstr + (visited ? " (*)" : ""));
        childInfos.add(new ChildInfo(node.getChildren().size()));
        return !visited;
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
        if (!childInfos.isEmpty())
            childInfos.removeLast();
        if (!childInfos.isEmpty())
            childInfos.getLast().index++;
        return true;
    }

    private boolean visit(DependencyNode node) {
        return visitedNodes.add(node.getArtifact());
    }

    private String formatIndentation() {
        final StringBuilder buffer = new StringBuilder(128);
        for (Iterator<ChildInfo> it = childInfos.iterator(); it.hasNext();)
            buffer.append(it.next().formatIndentation(!it.hasNext()));

        return buffer.toString();
    }

    private String formatNode(DependencyNode node) {
        final Artifact a = node.getArtifact();
        if (a == null)
            return null;

        final StringBuilder buffer = new StringBuilder(128);
        buffer.append(toString(a));

        final Dependency d = node.getDependency();

//        if (d != null && d.getScope().length() > 0) {
//            buffer.append(" [").append(d.getScope());
//            if (d.isOptional())
//                buffer.append(", optional");
//            buffer.append("]");
//        }

        final String premanagedVersion = DependencyManagerUtils.getPremanagedVersion(node);
        if (premanagedVersion != null && !premanagedVersion.equals(a.getBaseVersion()))
            buffer.append(" (version managed from ").append(premanagedVersion).append(")");

        final String premanagedScope = DependencyManagerUtils.getPremanagedScope(node);
        if (premanagedScope != null && !premanagedScope.equals(d.getScope()))
            buffer.append(" (scope managed from ").append(premanagedScope).append(")");

        final DependencyNode winner = (DependencyNode) node.getData().get(ConflictResolver.NODE_DATA_WINNER);
        if (winner != null && !ArtifactIdUtils.equalsId(a, winner.getArtifact())) {
            Artifact w = winner.getArtifact();
            buffer.append(" -> ");
            if (ArtifactIdUtils.toVersionlessId(a).equals(ArtifactIdUtils.toVersionlessId(w)))
                buffer.append(w.getVersion());
            else
                buffer.append(w);
        }
        return buffer.toString();
    }

    private String toString(Artifact a) {
        return a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();
    }

    private static class ChildInfo {
        final int count;
        int index;

        public ChildInfo(int count) {
            this.count = count;
        }

        public String formatIndentation(boolean end) {
            final boolean last = index + 1 >= count;
            if (end)
                return last ? "\\--- " : "+--- ";
            else
                return last ? "     " : "|    ";
        }
    }
}
