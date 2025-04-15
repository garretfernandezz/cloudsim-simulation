import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.*;

public class MainSimulation {

    public static void main(String[] args) {
        try {
            int numUsers = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;

            // Initialize CloudSim
            CloudSim.init(numUsers, calendar, traceFlag);

            // Create Datacenter
            Datacenter datacenter = createDatacenter("Datacenter_0");

            // Create Broker
            DatacenterBroker broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();

            // Create VMs
            List<Vm> vmList = new ArrayList<>();
            int mips = 1000;
            long size = 10000;
            int ram = 512;
            long bw = 1000;
            int pesNumber = 1;
            String vmm = "Xen";

            for (int i = 0; i < 3; i++) {
                Vm vm = new Vm(i, brokerId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerTimeShared());
                vmList.add(vm);
            }
            broker.submitVmList(vmList);

            // Create Cloudlets
            List<Cloudlet> cloudletList = new ArrayList<>();
            UtilizationModel utilizationModel = new UtilizationModelFull();
            long fileSize = 300;
            long outputSize = 300;

            for (int i = 0; i < 6; i++) {
                Cloudlet cloudlet = new Cloudlet(i, 40000, pesNumber, fileSize, outputSize,
                        utilizationModel, utilizationModel, utilizationModel);
                cloudlet.setUserId(brokerId);
                cloudletList.add(cloudlet);
            }

            // Load balancing: Round Robin assignment
            int vmIndex = 0;
            for (Cloudlet cloudlet : cloudletList) {
                cloudlet.setVmId(vmList.get(vmIndex).getId());
                vmIndex = (vmIndex + 1) % vmList.size(); // Round robin
            }

            broker.submitCloudletList(cloudletList);

            // Start simulation
            CloudSim.startSimulation();

            // Get results and simulate fault tolerance
            List<Cloudlet> receivedList = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            List<Cloudlet> failedCloudlets = new ArrayList<>();

            System.out.println("========== INITIAL RESULTS ==========");
            printCloudletList(receivedList);

            // Fault tolerance: re-submit failed cloudlets
            for (Cloudlet cloudlet : receivedList) {
                if (Math.random() < 0.3) { // simulate 30% random failure
                    System.out.println("Cloudlet " + cloudlet.getCloudletId() + " FAILED. Re-submitting...");
                    Cloudlet retry = new Cloudlet(
                            cloudlet.getCloudletId() + 100,
                            cloudlet.getCloudletLength(),
                            cloudlet.getNumberOfPes(),
                            cloudlet.getCloudletFileSize(),
                            cloudlet.getCloudletOutputSize(),
                            utilizationModel, utilizationModel, utilizationModel);
                    retry.setUserId(brokerId);
                    retry.setVmId(cloudlet.getVmId());
                    failedCloudlets.add(retry);
                }
            }

            if (!failedCloudlets.isEmpty()) {
                broker.submitCloudletList(failedCloudlets);
                CloudSim.startSimulation();
                List<Cloudlet> retryResults = broker.getCloudletReceivedList();
                CloudSim.stopSimulation();

                System.out.println("========== RETRIED TASKS ==========");
                printCloudletList(retryResults);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();

        int mips = 1000;
        int ram = 2048;
        long storage = 1000000;
        int bw = 10000;

        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerSimple(mips)));

        hostList.add(new Host(0, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw),
                storage, peList, new VmSchedulerTimeShared(peList)));

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double timeZone = 10.0;
        double costPerSec = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, timeZone, costPerSec, costPerMem, costPerStorage, costPerBw);

        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics,
                    new VmAllocationPolicySimple(hostList), new LinkedList<Storage>(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return datacenter;
    }

    private static void printCloudletList(List<Cloudlet> list) {
        String format = "%-12s%-10s%-16s%-8s%-10s%-15s%-15s%n";
        System.out.printf(format, "Cloudlet ID", "STATUS", "Data Center ID", "VM ID", "Time", "Start Time", "Finish Time");
        System.out.println("-------------------------------------------------------------------------------");

        for (Cloudlet cloudlet : list) {
            String status = cloudlet.getStatus() == Cloudlet.SUCCESS ? "SUCCESS" : "FAILED";
            System.out.printf(format,
                    cloudlet.getCloudletId(),
                    status,
                    cloudlet.getResourceId(),
                    cloudlet.getVmId(),
                    String.format("%.2++_f", cloudlet.getActualCPUTime()),
                    String.format("%.2f", cloudlet.getExecStartTime()),
                    String.format("%.2f", cloudlet.getFinishTime()));
        }
    }}

