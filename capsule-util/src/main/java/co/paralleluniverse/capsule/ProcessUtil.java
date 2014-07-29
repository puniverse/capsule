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
import java.nio.file.Paths;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 * @author pron
 */
public class ProcessUtil {
    /*
     * see https://weblogs.java.net/blog/emcmanus/archive/2007/08/combining_casca.html
     */
    private static Field pidField;
    private static final String PROP_LOCAL_CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";

    private static int getPid(Process process) {
        if (!process.getClass().getName().equals("java.lang..UNIXProcess"))
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

    private static JMXServiceURL getLocalConnectorAddress(String id) {
        VirtualMachine vm = null;
        try {
            vm = VirtualMachine.attach(id);
            String connectorAddr = vm.getAgentProperties().getProperty(PROP_LOCAL_CONNECTOR_ADDRESS);
            if (false && connectorAddr == null) {
                final String agent = Paths.get(vm.getSystemProperties().getProperty("java.home"), "lib", "management-agent.jar").toString();
                vm.loadAgent(agent);
                connectorAddr = vm.getAgentProperties().getProperty(PROP_LOCAL_CONNECTOR_ADDRESS);
            }
            return new JMXServiceURL(connectorAddr);
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

    static JMXServiceURL getLocalConnectorAddress(Process p) {
        return getLocalConnectorAddress(Integer.toString(getPid(p)));
    }

    public static MBeanServerConnection getMBeanServerConnection(Process p) {
        try {
            final JMXServiceURL serviceURL = getLocalConnectorAddress(p);
            final JMXConnector connector = JMXConnectorFactory.connect(serviceURL);
            final MBeanServerConnection mbsc = connector.getMBeanServerConnection();
            return mbsc;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
