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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import javax.management.StandardEmitterMBean;

/**
 *
 * @author pron
 */
public class CapsuleContainer implements CapsuleContainerMXBean {
    private final AtomicLong notificationSequence = new AtomicLong();
    private final ConcurrentMap<String, ProcessInfo> processes = new ConcurrentHashMap<String, ProcessInfo>();
    private final AtomicInteger counter = new AtomicInteger();
    private final Path cacheDir;
    private final StandardEmitterMBean mbean;
    private volatile Map<String, Path> javaHomes;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public CapsuleContainer(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.mbean = registerMBean("co.paralleluniverse:type=CapsuleContainer", getMBeanInterface());
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

            @Override
            public void run() {
                killAll();
            }
        }));
    }

    protected Class<?> getMBeanInterface() {
        return CapsuleContainerMXBean.class;
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

    private StandardEmitterMBean registerMBean(String name, Class<?> mbeanInterface) {
        try {
            final StandardEmitterMBean _mbean = new StandardEmitterMBean(this, (Class) mbeanInterface, createEmitter());
            final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            final ObjectName mxbeanName = ObjectName.getInstance(name);
            mbs.registerMBean(_mbean, mxbeanName);
            return _mbean;
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

    public String launchCapsule(Path capsulePath, List<String> jvmArgs, List<String> args) throws IOException {
        final Object capsule = CapsuleLauncher.newCapsule(capsulePath, cacheDir, javaHomes);
        if (javaHomes == null) // benign race
            javaHomes = CapsuleLauncher.getJavaHomes(capsule);
        return launchCapsule(capsule, jvmArgs, args);
    }

    private String launchCapsule(Object capsule, List<String> jvmArgs, List<String> args) throws IOException {
        if (jvmArgs == null)
            jvmArgs = Collections.emptyList();
        if (args == null)
            args = Collections.emptyList();

        try {
            final String capsuleId = CapsuleLauncher.getAppId(capsule);
            ProcessBuilder pb = CapsuleLauncher.prepareForLaunch(capsule, CapsuleLauncher.enableJMX(jvmArgs), args);
            pb = configureCapsuleProcess(pb);

            final Process p = pb.start();
            final String id = createProcessId(capsuleId, p);

            final ProcessInfo pi = mountProcess(p, id, capsuleId, jvmArgs, args);
            processes.put(id, pi);

            final Notification n = new CapsuleProcessLaunched(mbean, notificationSequence.incrementAndGet(), System.currentTimeMillis(), id, "args: " + args + " jvmArgs: " + jvmArgs);
            mbean.sendNotification(n);
            onProcessLaunch(id, pi);
            monitorProcess(id, p);

            return id;
        } catch (Exception e) {
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new RuntimeException(e);
        }
    }

    protected ProcessInfo mountProcess(Process p, String id, String capsuleId, List<String> jvmArgs, List<String> args) throws IOException, InstanceAlreadyExistsException {
        return new ProcessInfo(p, capsuleId, jvmArgs, args);
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
        final Notification n = new CapsuleProcessKilled(mbean, notificationSequence.incrementAndGet(), System.currentTimeMillis(), id, exitValue);
        mbean.sendNotification(n);
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

    public final Map<String, ProcessInfo> getProcessInfo() {
        final Map<String, ProcessInfo> m = new HashMap<>();
        for (Iterator<Map.Entry<String, ProcessInfo>> it = processes.entrySet().iterator(); it.hasNext();) {
            final Map.Entry<String, ProcessInfo> e = it.next();
            final ProcessInfo pi = e.getValue();
            if (isAlive(pi.process))
                m.put(e.getKey(), pi);
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

    private void killAll() {
        for (ProcessInfo pi : getProcessInfo().values())
            pi.process.destroy();
    }

    @Override
    public final Set<String> getProcesses() {
        Set<String> ps = new HashSet<>();
        for (Map.Entry<String, ProcessInfo> entry : getProcessInfo().entrySet()) {
            ps.add(entry.getKey() + " " + entry.getValue());
        }
        return ps;
    }

    @Override
    public final void killProcess(String id) {
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
        final String capsuleId;
        final List<String> jvmArgs;
        final List<String> args;

        public ProcessInfo(Process process, String capsuleId, List<String> jvmArgs, List<String> args) {
            this.process = process;
            this.capsuleId = capsuleId;
            this.jvmArgs = Collections.unmodifiableList(new ArrayList<String>(jvmArgs));
            this.args = Collections.unmodifiableList(new ArrayList<String>(args));;
        }

        @Override
        public String toString() {
            return "(" + "capsule: " + capsuleId + " args: " + args + " jvmArgs:" + jvmArgs + ')';
        }
    }
}
