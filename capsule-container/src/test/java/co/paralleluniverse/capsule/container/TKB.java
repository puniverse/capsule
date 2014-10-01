package co.paralleluniverse.capsule.container;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TKB {
    private static final String PROP_LOCAL_CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";
    private static final String PROP_JAVA_HOME = "java.home";
    private static final Path MANAGEMENT_AGENT = Paths.get("lib", "management-agent.jar");

    public static void main(String[] args) {

        for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
            VirtualMachine vm = null;
            try {
                vm = VirtualMachine.attach(vmd.id());
                String connectorAddr = vm.getAgentProperties().getProperty(PROP_LOCAL_CONNECTOR_ADDRESS);
                boolean attached = false;
                if (connectorAddr == null) {
                    final String agent = Paths.get(vm.getSystemProperties().getProperty(PROP_JAVA_HOME)).resolve(MANAGEMENT_AGENT).toString();
                    vm.loadAgent(agent);
                    connectorAddr = vm.getAgentProperties().getProperty(PROP_LOCAL_CONNECTOR_ADDRESS);
                    attached = true;
                }
                System.out.println(vmd.displayName() + " :: " + vmd.id() + " || " + attached + " ** " + connectorAddr + " ----->>>> " + vm.getAgentProperties());
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
    }
}
