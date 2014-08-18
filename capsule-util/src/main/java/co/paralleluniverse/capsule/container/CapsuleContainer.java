/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule.container;

import co.paralleluniverse.capsule.CapsuleLauncher;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import javax.management.StandardEmitterMBean;

/**
 *
 * @author pron
 */
public class CapsuleContainer implements CapsuleContainerMBean {
    private final AtomicLong notificationSequence = new AtomicLong();
    private final ConcurrentMap<String, ProcessInfo> processes = new ConcurrentHashMap<String, ProcessInfo>();
    private final AtomicInteger counter = new AtomicInteger();
    private final Path cacheDir;
    private final NotificationBroadcasterSupport emitter;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public CapsuleContainer(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.emitter = createEmitter();
        registerMBean("co.paralleluniverse:type=CapsuleContainer", getMBeanInterface());
    }

    protected Class<?> getMBeanInterface() {
        return CapsuleContainerMBean.class;
    }

    protected NotificationBroadcasterSupport createEmitter() {
        final MBeanNotificationInfo launch = new MBeanNotificationInfo(
                new String[]{CapsuleProcessLaunched.CAPSULE_PROCESS_LAUNCHED},
                CapsuleProcessLaunched.class.getName(),
                "Notification about a capsule process having launched.");
        final MBeanNotificationInfo death = new MBeanNotificationInfo(
                new String[]{CapsuleProcessKilled.CAPSULE_PROCESS_KILLED},
                CapsuleProcessKilled.class.getName(),
                "Notification about a capsule process having launched.");
        return new NotificationBroadcasterSupport(launch, death);
    }

    private void registerMBean(String name, Class<?> mbeanInterface) {
        try {
            final Object mbean = new StandardEmitterMBean(this, (Class) mbeanInterface, emitter);

            final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            final ObjectName mxbeanName = new ObjectName(name);
            mbs.registerMBean(mbean, mxbeanName);
        } catch (InstanceAlreadyExistsException ex) {
            throw new RuntimeException(ex);
        } catch (MBeanRegistrationException ex) {
            throw new RuntimeException(ex);
        } catch (NotCompliantMBeanException ex) {
            throw new AssertionError(ex);
        } catch (MalformedObjectNameException ex) {
            throw new AssertionError(ex);
        }
    }

    public String launchCapsule(Path capsulePath, List<String> cmdLine, List<String> args) throws IOException {
        return launchCapsule(CapsuleLauncher.newCapsule(capsulePath, cacheDir), cmdLine, args);
    }

    private String launchCapsule(Object capsule, List<String> cmdLine, List<String> args) throws IOException {
        if (cmdLine == null)
            cmdLine = Collections.emptyList();

        try {
            ProcessBuilder pb = CapsuleLauncher.prepareForLaunch(capsule, CapsuleLauncher.enableJMX(cmdLine), args.toArray(new String[args.size()]));
            pb = configureCapsuleProcess(pb);

            final Process p = pb.start();
            final String id = createProcessId(CapsuleLauncher.getAppId(capsule), p);

            final ProcessInfo pi = mountProcess(p, id);
            processes.put(id, pi);

            emitter.sendNotification(new CapsuleProcessLaunched(this, notificationSequence.incrementAndGet(), id));
            onProcessLaunch(id, pi);
            monitorProcess(id, p);

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

    protected void processDied(String id, Process p, int exitValue) {
        emitter.sendNotification(new CapsuleProcessKilled(this, notificationSequence.incrementAndGet(), id, exitValue));
        onProcessDeath(id, getProcessInfo(id), exitValue);
    }

    protected void onProcessLaunch(String id, ProcessInfo pi) {
    }

    protected void onProcessDeath(String id, ProcessInfo pi, int exitValue) {
    }

    private void monitorProcess(final String id, final Process p) {
        new Thread("process-monitor-" + id) {
            @Override
            public void run() {
                try {
                    int exit = p.waitFor();
                    processDied(id, p, exit);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    protected String createProcessId(String appId, Process p) {
        return appId + "-" + counter.incrementAndGet();
    }

    public final Map<String, Process> getProcessInfo() {
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

    @Override
    public Set<String> getProcesses() {
        return processes.keySet();
    }

    @Override
    public void killProcess(String id) {
        ProcessInfo pi = getProcessInfo(id);
        if (pi != null) {
            pi.process.destroy();
        }
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
