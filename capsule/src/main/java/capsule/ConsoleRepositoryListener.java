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

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;

public class ConsoleRepositoryListener extends AbstractRepositoryListener {
    private final PrintStream out;
    private final boolean verbose;

    public ConsoleRepositoryListener(boolean verbose, PrintStream out) {
        this.out = out;
        this.verbose = verbose;
    }

    private void println(String str) {
        out.println(str);
    }

    private void printlnVerbose(String str) {
        if (verbose)
            println(str);
    }

    @Override public void metadataResolved    (RepositoryEvent ev) { printlnVerbose("Resolved metadata " + ev.getMetadata() + " from " + ev.getRepository()); }
    @Override public void metadataResolving   (RepositoryEvent ev) { printlnVerbose("Resolving metadata " + ev.getMetadata() + " from " + ev.getRepository()); }
    @Override public void artifactDownloading (RepositoryEvent ev) { println("Downloading artifact " + ev.getArtifact() + " from " + ev.getRepository()); }
    @Override public void artifactDownloaded  (RepositoryEvent ev) { println("Downloaded artifact " + ev.getArtifact() + " from " + ev.getRepository()); }
    @Override public void artifactResolving   (RepositoryEvent ev) { printlnVerbose("Resolving artifact " + ev.getArtifact()); }
    @Override public void artifactResolved    (RepositoryEvent ev) { printlnVerbose("Resolved artifact " + ev.getArtifact() + " from " + ev.getRepository()); }
    @Override public void metadataInvalid          (RepositoryEvent ev) { println("Invalid metadata " + ev.getMetadata()); }
    @Override public void artifactDescriptorInvalid(RepositoryEvent ev) { println("Invalid artifact descriptor for " + ev.getArtifact() + ": " + ev.getException().getMessage()); }
    @Override public void artifactDescriptorMissing(RepositoryEvent ev) { println("Missing artifact descriptor for " + ev.getArtifact()); }
    @Override public void artifactInstalling  (RepositoryEvent ev) { println("Installing " + ev.getArtifact() + " to " + ev.getFile()); }
    @Override public void artifactInstalled   (RepositoryEvent ev) { println("Installed " + ev.getArtifact() + " to " + ev.getFile()); }
    @Override public void artifactDeploying   (RepositoryEvent ev) { println("Deploying " + ev.getArtifact() + " to " + ev.getRepository()); }
    @Override public void artifactDeployed    (RepositoryEvent ev) { println("Deployed " + ev.getArtifact() + " to " + ev.getRepository()); }
    @Override public void metadataDeploying   (RepositoryEvent ev) { println("Deploying " + ev.getMetadata() + " to " + ev.getRepository()); }
    @Override public void metadataDeployed    (RepositoryEvent ev) { println("Deployed " + ev.getMetadata() + " to " + ev.getRepository()); }
    @Override public void metadataInstalling  (RepositoryEvent ev) { println("Installing " + ev.getMetadata() + " to " + ev.getFile()); }
    @Override public void metadataInstalled   (RepositoryEvent ev) { println("Installed " + ev.getMetadata() + " to " + ev.getFile()); }
}
