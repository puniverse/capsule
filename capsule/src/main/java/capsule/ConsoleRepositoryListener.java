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

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;

public final class ConsoleRepositoryListener extends AbstractRepositoryListener {
    private final PrintStream out;
    private final boolean verbose;

    public ConsoleRepositoryListener(boolean verbose, PrintStream out) {
        this.out = out;
        this.verbose = verbose;
    }

    private void println(String str) {
        out.println(str);
    }

    private void verbose(String str) {
        if (verbose)
            println(str);
    }

    @Override public void artifactDownloading(RepositoryEvent ev) {
        if (!verbose)
            println("Downloading dependency " + ev.getArtifact());
        else
            println("Downloading artifact " + ev.getArtifact() + " from " + ev.getRepository());
    }
    @Override public void artifactDownloaded  (RepositoryEvent ev) { verbose("Downloaded artifact " + ev.getArtifact() + " from " + ev.getRepository()); }
    @Override public void artifactResolving   (RepositoryEvent ev) { verbose("Resolving artifact " + ev.getArtifact()); }
    @Override public void artifactResolved    (RepositoryEvent ev) { verbose("Resolved artifact " + ev.getArtifact() + " from " + ev.getRepository()); }
    @Override public void metadataResolved    (RepositoryEvent ev) { verbose("Resolved metadata " + ev.getMetadata() + " from " + ev.getRepository()); }
    @Override public void metadataResolving   (RepositoryEvent ev) { verbose("Resolving metadata " + ev.getMetadata() + " from " + ev.getRepository()); }
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
