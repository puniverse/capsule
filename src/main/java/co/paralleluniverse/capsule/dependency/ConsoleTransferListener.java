/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
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
package co.paralleluniverse.capsule.dependency;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;

/**
 * A simplistic transfer listener that logs uploads/downloads to the console.
 */
public class ConsoleTransferListener extends AbstractTransferListener {
    private final PrintStream out;
    private final Map<TransferResource, Long> downloads = new ConcurrentHashMap<TransferResource, Long>();
    private int lastLength;

    public ConsoleTransferListener(PrintStream out) {
        this.out = out;
    }

    @Override
    public void transferInitiated(TransferEvent event) {
        String message = event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploading" : "Downloading";

        out.println(message + ": " + event.getResource().getRepositoryUrl() + event.getResource().getResourceName());
    }

    @Override
    public void transferProgressed(TransferEvent event) {
        TransferResource resource = event.getResource();
        downloads.put(resource, Long.valueOf(event.getTransferredBytes()));

        StringBuilder buffer = new StringBuilder(64);

        for (Map.Entry<TransferResource, Long> entry : downloads.entrySet()) {
            long total = entry.getKey().getContentLength();
            long complete = entry.getValue().longValue();

            buffer.append(getStatus(complete, total)).append("  ");
        }

        int pad = lastLength - buffer.length();
        lastLength = buffer.length();
        pad(buffer, pad);
        buffer.append('\r');

        out.print(buffer);
    }

    private String getStatus(long complete, long total) {
        if (total >= 1024)
            return toKB(complete) + "/" + toKB(total) + " KB ";
        else if (total >= 0)
            return complete + "/" + total + " B ";
        else if (complete >= 1024)
            return toKB(complete) + " KB ";
        else
            return complete + " B ";
    }

    private void pad(StringBuilder buffer, int spaces) {
        String block = "                                        ";
        while (spaces > 0) {
            int n = Math.min(spaces, block.length());
            buffer.append(block, 0, n);
            spaces -= n;
        }
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        transferCompleted(event);

        TransferResource resource = event.getResource();
        long contentLength = event.getTransferredBytes();
        if (contentLength >= 0) {
            String type = (event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploaded" : "Downloaded");
            String len = contentLength >= 1024 ? toKB(contentLength) + " KB" : contentLength + " B";

            String throughput = "";
            long duration = System.currentTimeMillis() - resource.getTransferStartTime();
            if (duration > 0) {
                long bytes = contentLength - resource.getResumeOffset();
                DecimalFormat format = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.ENGLISH));
                double kbPerSec = (bytes / 1024.0) / (duration / 1000.0);
                throughput = " at " + format.format(kbPerSec) + " KB/sec";
            }

            out.println(type + ": " + resource.getRepositoryUrl() + resource.getResourceName() + " (" + len + throughput + ")");
        }
    }

    @Override
    public void transferFailed(TransferEvent event) {
        transferCompleted(event);

        if (!(event.getException() instanceof MetadataNotFoundException))
            event.getException().printStackTrace(out);
    }

    private void transferCompleted(TransferEvent event) {
        downloads.remove(event.getResource());

        StringBuilder buffer = new StringBuilder(64);
        pad(buffer, lastLength);
        buffer.append('\r');
        out.print(buffer);
    }

    @Override
    public void transferCorrupted(TransferEvent event) {
        event.getException().printStackTrace(out);
    }

    protected long toKB(long bytes) {
        return (bytes + 1023) / 1024;
    }
}
