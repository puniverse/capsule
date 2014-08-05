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
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 * @author pron
 */
public class MBeanCapsuleContainer extends CapsuleContainer {
    @Override
    protected String createProcessId(String appId, Process p) {
        return super.createProcessId(appId, p) + "@" + ProcessUtil.getPid(p);
    }

    @Override
    protected CapsuleContainer.ProcessInfo mountProcess(Process p, String id) throws IOException, InstanceAlreadyExistsException {
        final JMXServiceURL connectorAddress = ProcessUtil.getLocalConnectorAddress(p, false);
        return new ProcessInfo(p, connectorAddress);
    }

    @Override
    protected ProcessInfo getProcessInfo(String id) {
        return (ProcessInfo) super.getProcessInfo(id);
    }

    public final MBeanServerConnection getProcessMBeans(String id) {
        final ProcessInfo pi = getProcessInfo(id);
        return pi != null ? pi.getJMX() : null;
    }

    protected static class ProcessInfo extends CapsuleContainer.ProcessInfo {
        final JMXServiceURL connectorAddress;
        private MBeanServerConnection jmx;

        public ProcessInfo(Process process, JMXServiceURL connectorAddress) {
            super(process);
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
