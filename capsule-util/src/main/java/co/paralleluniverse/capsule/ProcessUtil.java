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
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 * @author pron
 */
public class ProcessUtil {
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

    public static MBeanServerConnection attach(Process p) {
        try {
            final String id = Integer.toString(getPid(p));
            final JMXServiceURL serviceURL = getLocalConnectorAddress(id);
            
            final JMXConnector connector = JMXConnectorFactory.connect(serviceURL);
            final MBeanServerConnection mbsc = connector.getMBeanServerConnection();
            return mbsc;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String attach(Process p, String id, CascadingService cascade) {
        try {
            final String pid = Integer.toString(getPid(p));
            final JMXServiceURL serviceURL = getLocalConnectorAddress(pid);

            final String mountId = cascade.mount(serviceURL, null, ObjectName.WILDCARD, id);
            return mountId;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
