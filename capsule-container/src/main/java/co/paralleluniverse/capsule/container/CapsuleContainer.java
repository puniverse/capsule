/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. and Contributors. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule.container;

import co.paralleluniverse.capsule.Capsule;
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
 * Launches, monitors and manages capsules.
 *
 * @author pron
 */
public class CapsuleContainer implements CapsuleContainerMXBean {
    public static final String CAPSULE_PROCESS_LAUNCHED = "capsule.launch";
    public static final String CAPSULE_PROCESS_KILLED = "capsule.death";
    private final AtomicLong notificationSequence = new AtomicLong();
    private final ConcurrentMap<String, ProcessInfo> processes = new ConcurrentHashMap<String, ProcessInfo>();
    private final AtomicInteger counter = new AtomicInteger();
    private final Path cacheDir;
    private final StandardEmitterMBean mbean;
    private final Map<String, Path> javaHomes;

    /**
     * Constructs a new capsule container
     *
     * @param cacheDir the path of the directory to hold capsules' caches
     */
    @SuppressWarnings("OverridableMethodCallInConstructor")
    public CapsuleContainer(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.mbean = registerMBean("co.paralleluniverse:type=CapsuleContainer", getMBeanInterface());
        this.javaHomes = CapsuleLauncher.findJavaHomes();
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
                new String[]{CAPSULE_PROCESS_LAUNCHED},
                Notification.class.getName(),
                "Notification about a capsule process having launched.");
        final MBeanNotificationInfo death = new MBeanNotificationInfo(
                new String[]{CAPSULE_PROCESS_KILLED},
                Notification.class.getName(),
                "Notification about a capsule process having launched.");
        return new NotificationBroadcasterSupport(launch, death);
    }

    @SuppressWarnings("unchecked")
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

    /**
     * Launch and monitor a capsule.
     *
     * @param capsulePath the capsule file
     * @param mode        the capsule mode ({@code null} for the default mode)
     * @param jvmArgs     the JVM arguments for the capsule
     * @param args        the arguments of the capsule's application
     * @return a unique process ID
     */
    public String launchCapsule(Path capsulePath, String mode, List<String> jvmArgs, List<String> args) throws IOException {
        final Capsule capsule = new CapsuleLauncher(capsulePath).setJavaHomes(javaHomes).newCapsule(mode, null);
        return launchCapsule(capsule, jvmArgs, args);
    }

    private String launchCapsule(Capsule capsule, List<String> jvmArgs, List<String> args) throws IOException {
        if (jvmArgs == null)
            jvmArgs = Collections.emptyList();
        if (args == null)
            args = Collections.emptyList();

        try {
            final String capsuleId = capsule.getAppId();
            final ProcessBuilder pb = configureCapsuleProcess(capsule.prepareForLaunch(CapsuleLauncher.enableJMX(jvmArgs), args));

            final Process p = pb.start();
            final String id = createProcessId(capsuleId, p);

            final ProcessInfo pi = mountProcess(p, id, capsuleId, jvmArgs, args);
            processes.put(id, pi);

            mbean.sendNotification(processLaunchedNotification(id, jvmArgs, args));
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
     * May be overridden to pipe app IO streams.
     *
     * @param pb The capsule's {@link ProcessBuilder}.
     * @return {@code pb}
     */
    protected ProcessBuilder configureCapsuleProcess(ProcessBuilder pb) throws IOException {
        return pb;
    }

    void processDied(String id, Process p, int exitValue) {
        mbean.sendNotification(processDiedNotification(id, exitValue));
        onProcessDeath(id, getProcessInfo(id), exitValue);
    }

    /**
     * Called a a process has been launched.
     *
     * @param id the process ID
     * @param pi the {@link ProcessInfo} object for the process
     */
    protected void onProcessLaunch(String id, ProcessInfo pi) {
    }

    /**
     * Called a a process has died.
     *
     * @param id        the process ID
     * @param pi        the {@link ProcessInfo} object for the process
     * @param exitValue the process's exit value
     */
    protected void onProcessDeath(String id, ProcessInfo pi, int exitValue) {
    }

    private Notification processLaunchedNotification(String id, List<String> jvmArgs, List<String> args) {
        return new Notification(CAPSULE_PROCESS_LAUNCHED, mbean, notificationSequence.incrementAndGet(), System.currentTimeMillis(), id + " args: " + args + " jvmArgs: " + jvmArgs);
    }

    private Notification processDiedNotification(String id, int exitValue) {
        return new Notification(CAPSULE_PROCESS_KILLED, mbean, notificationSequence.incrementAndGet(), System.currentTimeMillis(), id + "exitValue: " + exitValue);
    }

    private void monitorProcess(final String id, final Process p) {
        new Thread("process-monitor-" + id) {
            @SuppressWarnings("CallToPrintStackTrace")
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

    /**
     * Generates a unique id for a process
     *
     * @param appId the capsule's app ID
     * @param p     the process
     * @return a unique process ID
     */
    protected String createProcessId(String appId, Process p) {
        return appId + "-" + counter.incrementAndGet();
    }

    /**
     * Returns information about all managed processes.
     *
     * @return a map from process IDs to their respective {@link ProcessInfo} objects.
     */
    protected final Map<String, ProcessInfo> getProcessInfo() {
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

    /**
     * Returns information about a process
     *
     * @param id the process ID
     * @return the process's {@link ProcessInfo} object/
     */
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

    /**
     * 
     * @param id the process ID (as returned from {@link #launchCapsule(Path, String, List, List) launchCapsule}.
     * @return the process
     */
    public final Process getProcess(String id) {
        final ProcessInfo pi = getProcessInfo(id);
        return pi != null ? pi.process : null;
    }

    private void killAll() {
        for (ProcessInfo pi : getProcessInfo().values())
            pi.process.destroy();
    }

    /**
     * Returns the IDs of all currently running managed processes.
     */
    @Override
    public final Set<String> getProcesses() {
        Set<String> ps = new HashSet<>();
        for (Map.Entry<String, ProcessInfo> entry : getProcessInfo().entrySet()) {
            ps.add(entry.getKey() + " " + entry.getValue());
        }
        return ps;
    }

    /**
     * Kills a managed process
     *
     * @param id the process's ID
     */
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
            this.args = Collections.unmodifiableList(new ArrayList<String>(args));
        }

        @Override
        public String toString() {
            return "(" + "capsule: " + capsuleId + " args: " + args + " jvmArgs:" + jvmArgs + ')';
        }
    }
}
