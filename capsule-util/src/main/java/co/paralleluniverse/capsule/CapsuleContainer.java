/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.management.InstanceAlreadyExistsException;

/**
 *
 * @author pron
 */
public class CapsuleContainer {
    private final ConcurrentMap<String, ProcessInfo> processes = new ConcurrentHashMap<String, ProcessInfo>();
    private final AtomicInteger counter = new AtomicInteger();
    private final Path cacheDir;

    public CapsuleContainer(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    public String launchCapsule(Path capsulePath, List<String> cmdLine, String[] args) throws IOException {
        return launchCapsule(CapsuleLauncher.getCapsule(capsulePath, cacheDir), cmdLine, args);
    }

    private String launchCapsule(Object capsule, List<String> cmdLine, String[] args) throws IOException {
        try {
            final ProcessBuilder pb = configureCapsuleProcess(CapsuleLauncher.prepareForLaunch(capsule, CapsuleLauncher.enableJMX(cmdLine), args));

            final Process p = pb.start();

            final String id = createProcessId(CapsuleLauncher.getAppId(capsule), p);
            final ProcessInfo pi = mountProcess(p, id);
            processes.put(id, pi);

            return id;
        } catch (Exception e) {
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new RuntimeException(e);
        }
    }

    protected ProcessInfo mountProcess(Process p, String id) throws IOException, InstanceAlreadyExistsException {
        return new ProcessInfo(p);
    }

    /**
     * May be overriden to pipe app IO streams.
     *
     * @param pb The capsule's {@link ProcessBuilder}.
     * @return {@code pb}
     */
    protected ProcessBuilder configureCapsuleProcess(ProcessBuilder pb) throws IOException {
        return pb;
    }

    protected String createProcessId(String appId, Process p) {
        return appId + "-" + counter.incrementAndGet();
    }

    public final Map<String, Process> getProcesses() {
        final Map<String, Process> m = new HashMap<>();
        for (Iterator<Map.Entry<String, Process>> it = m.entrySet().iterator(); it.hasNext();) {
            final Map.Entry<String, Process> e = it.next();
            final Process p = e.getValue();
            if (isAlive(p))
                m.put(e.getKey(), p);
            else
                it.remove();
        }
        return Collections.unmodifiableMap(m);
    }

    protected ProcessInfo getProcessInfo(String id) {
        final ProcessInfo pi = processes.get(id);
        if (pi == null)
            return null;
        if (isAlive(pi.process))
            return pi;
        else {
            processes.remove(id, pi);
            return null;
        }
    }

    public final Process getProcess(String id) {
        final ProcessInfo pi = getProcessInfo(id);
        return pi != null ? pi.process : null;
    }

    private static boolean isAlive(Process p) {
        try {
            p.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    protected static class ProcessInfo {
        final Process process;

        public ProcessInfo(Process process) {
            this.process = process;
        }
    }
}
