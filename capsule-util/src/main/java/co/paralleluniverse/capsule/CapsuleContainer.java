/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

import com.sun.jdmk.remote.cascading.CascadingService;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 * @author pron
 */
public class CapsuleContainer {
    private final AtomicInteger counter = new AtomicInteger();
    private final ConcurrentMap<String, ProcessInfo> processes = new ConcurrentHashMap<String, ProcessInfo>();
    private final CascadingService cascade;

    public CapsuleContainer() {
        this.cascade = new CascadingService(ManagementFactory.getPlatformMBeanServer());
    }

    public String launchCapsule(Path capsulePath, List<String> cmdLine, String[] args) throws IOException {
        try {
            final Object capsule = CapsuleLauncher.getCapsule(capsulePath);
            final String id = CapsuleLauncher.getAppId(capsule) + "-" + counter.incrementAndGet();
            final ProcessBuilder pb = CapsuleLauncher.prepareForLaunch(capsule, CapsuleLauncher.enableJMX(cmdLine), args);
            final Process p = pb.start();
            final JMXServiceURL connectorAddress = ProcessUtil.getLocalConnectorAddress(p);
            final String mountId = cascade.mount(connectorAddress, null, ObjectName.WILDCARD, id);

            processes.put(id, new ProcessInfo(p, mountId, connectorAddress));

            return id;
        } catch (Exception e) {
            if (e instanceof RuntimeException)
                throw (RuntimeException)e;
            throw new RuntimeException(e);
        }
    }

    public void kill(String id, boolean forcibly) {
        final ProcessInfo pi = processes.get(id);
        if (pi == null)
            return;
        if (isAlive(pi.process))
            pi.process.destroy();
        else
            processes.remove(id, pi);
    }

    public Map<String, Process> getProcesses() {
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

    private static boolean isAlive(Process p) {
        try {
            p.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    private static class ProcessInfo {
        final Process process;
        final String mountPoint;
        final JMXServiceURL connectorAddress;
        private MBeanServerConnection jmx;

        public ProcessInfo(Process process, String mountPoint, JMXServiceURL connectorAddress) {
            this.process = process;
            this.mountPoint = mountPoint;
            this.connectorAddress = connectorAddress;
        }

        public synchronized MBeanServerConnection getJMX() {
            try {
                if (jmx == null)
                    this.jmx = JMXConnectorFactory.connect(connectorAddress).getMBeanServerConnection();
                return jmx;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
