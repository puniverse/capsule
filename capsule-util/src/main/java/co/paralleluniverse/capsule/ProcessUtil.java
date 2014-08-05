/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 * @author pron
 */
public final class ProcessUtil {
    /*
     * see https://weblogs.java.net/blog/emcmanus/archive/2007/08/combining_casca.html
     */
    private static Field pidField;
    private static final String PROP_LOCAL_CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";
    private static final String PROP_JAVA_HOME = "java.home";
    private static final Path MANAGEMENT_AGENT = Paths.get("lib", "management-agent.jar");

    public static int getPid(Process process) {
        if (!process.getClass().getName().equals("java.lang.UNIXProcess"))
            throw new UnsupportedOperationException("This operation is only supported in POSIX environments (Linux/Unix/MacOS");
        if (pidField == null) { // benign race
            try {
                Field f = process.getClass().getDeclaredField("pid");
                f.setAccessible(true);
                pidField = f;
            } catch (NoSuchFieldException e) {
                throw new AssertionError(e);
            } catch (SecurityException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            return pidField.getInt(process);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static JMXServiceURL getLocalConnectorAddress(String id, boolean startAgent) {
        VirtualMachine vm = null;
        try {
            vm = VirtualMachine.attach(id);
            String connectorAddr = vm.getAgentProperties().getProperty(PROP_LOCAL_CONNECTOR_ADDRESS);
            if (connectorAddr == null && startAgent) {
                final String agent = Paths.get(vm.getSystemProperties().getProperty(PROP_JAVA_HOME)).resolve(MANAGEMENT_AGENT).toString();
                vm.loadAgent(agent);
                connectorAddr = vm.getAgentProperties().getProperty(PROP_LOCAL_CONNECTOR_ADDRESS);
            }
            return connectorAddr != null ? new JMXServiceURL(connectorAddr) : null;
        } catch (AttachNotSupportedException e) {
            throw new UnsupportedOperationException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (vm != null)
                    vm.detach();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static JMXServiceURL getLocalConnectorAddress(Process p, boolean startAgent) {
        return getLocalConnectorAddress(Integer.toString(getPid(p)), startAgent);
    }

    public static MBeanServerConnection getMBeanServerConnection(Process p, boolean startAgent) {
        try {
            final JMXServiceURL serviceURL = getLocalConnectorAddress(p, startAgent);
            final JMXConnector connector = JMXConnectorFactory.connect(serviceURL);
            final MBeanServerConnection mbsc = connector.getMBeanServerConnection();
            return mbsc;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ProcessUtil() {
    }
}
