/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import java.io.IOException;
import java.lang.management.ManagementFactory;
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

    private static String getLocalConnectorAddress(int pid) {
        try {
            final VirtualMachine vm = VirtualMachine.attach(Integer.toString(pid));
            String connectorAddr = vm.getAgentProperties().getProperty(PROP_LOCAL_CONNECTOR_ADDRESS);
            if (false && connectorAddr == null) {
                final String agent = Paths.get(vm.getSystemProperties().getProperty("java.home"), "lib", "management-agent.jar").toString();
                vm.loadAgent(agent);
                connectorAddr = vm.getAgentProperties().getProperty(PROP_LOCAL_CONNECTOR_ADDRESS);
            }
            vm.detach();
            return connectorAddr;
        } catch (AttachNotSupportedException e) {
            throw new UnsupportedOperationException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static MBeanServerConnection attach(Process p) {
        try {
            final int pid = getPid(p);
            final String connectorAddr = getLocalConnectorAddress(pid);
            final JMXServiceURL serviceURL = new JMXServiceURL(connectorAddr);
            final JMXConnector connector = JMXConnectorFactory.connect(serviceURL);
            final MBeanServerConnection mbsc = connector.getMBeanServerConnection();
            return mbsc;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
