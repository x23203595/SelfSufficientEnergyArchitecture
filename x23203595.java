package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.utils.FogLinearPowerModel;
import org.cloudbus.cloudsim.Storage;

public class x23203595 {
    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();
    static List<Actuator> actuators = new ArrayList<>();
    static int numOfAreas = 2;
    static int numOfCamerasPerArea = 2;

    private static boolean CLOUD = true;

    public static void main(String[] args) {
        Log.printLine("Starting Smart Building Simulation...");

        try {
            Log.disable();
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;

            CloudSim.init(num_user, calendar, trace_flag);

            String appId = "smart_building";

            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            createFogDevices(broker.getId(), appId);

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            
         // Place modules on edge devices
            for (FogDevice device : fogDevices) {
                if (device != null && device.getName().startsWith("EdgeNode")) {
                    moduleMapping.addModuleToDevice("Env_Data_Processing", device.getName());
                    moduleMapping.addModuleToDevice("Light_Data_Processing", device.getName());
                    moduleMapping.addModuleToDevice("Humidity_Data_Processing", device.getName());
                    moduleMapping.addModuleToDevice("Energy_Data_Processing", device.getName());
                }
            }

            // Place modules on fog nodes (area gateways)
            for (FogDevice device : fogDevices) {
                if (device != null && device.getName().startsWith("areaGW")) {
                    moduleMapping.addModuleToDevice("Occupancy_Video_Processing", device.getName());
                    moduleMapping.addModuleToDevice("HVAC_Command", device.getName());
                    moduleMapping.addModuleToDevice("Security_Command", device.getName());
                    moduleMapping.addModuleToDevice("Lighting_Command", device.getName());
                    moduleMapping.addModuleToDevice("Energy_Command", device.getName());
                }
            }

            // Place modules on the cloud
            if (CLOUD) {
                moduleMapping.addModuleToDevice("Cloud_Storage", "cloud");
                moduleMapping.addModuleToDevice("Env_Data_Processing", "cloud");
                moduleMapping.addModuleToDevice("Occupancy_Video_Processing", "cloud");
                moduleMapping.addModuleToDevice("Light_Data_Processing", "cloud");
                moduleMapping.addModuleToDevice("Humidity_Data_Processing", "cloud");
                moduleMapping.addModuleToDevice("Energy_Data_Processing", "cloud");
                moduleMapping.addModuleToDevice("HVAC_Command", "cloud");
                moduleMapping.addModuleToDevice("Security_Command", "cloud");
                moduleMapping.addModuleToDevice("Lighting_Command", "cloud");
                moduleMapping.addModuleToDevice("Energy_Command", "cloud");
            }
       
            System.out.println("Fog Devices: " + fogDevices.size());
            for (FogDevice device : fogDevices) {
                System.out.println("Device: " + device.getName());
            }

            System.out.println("Sensors: " + sensors.size());
            for (Sensor sensor : sensors) {
                System.out.println("Sensor: " + sensor.getName());
            }

            System.out.println("Actuators: " + actuators.size());
            for (Actuator actuator : actuators) {
                System.out.println("Actuator: " + actuator.getName());
            }
            
            // Create the master controller for the Module Placement Policy
            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);

            controller.submitApplication(application,
                    (CLOUD) ? (new ModulePlacementMapping(fogDevices, application, moduleMapping))
                            : (new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            // Extend simulation time if needed
            CloudSim.startSimulation();

            // Wait for more time to ensure loops execute
            CloudSim.stopSimulation();

            Log.printLine("Smart Building Simulation finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }
    
    // Create the Cloud and Proxy Server in the Fog Cloud and specifying the areas
    private static void createFogDevices(int userID, String appID) {
    	FogDevice cloud = createFogDevice("cloud", 30000, 8000, 10000, 10000, 0, 0.01, 100, 80);
        cloud.setParentId(-1);
        fogDevices.add(cloud);
        
        FogDevice proxy = createFogDevice("proxy-server", 2000, 4000, 10000, 10000, 1, 0.0, 100, 80);
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
        fogDevices.add(proxy);

        for (int i = 0; i < numOfAreas; i++) {
            addArea(i + "", userID, appID, proxy.getId());
        }
    }
    
    //Define the area gateways to the PIR Camera sensors
    private static FogDevice addArea(String id, int userID, String appID, int parentID) {
    	FogDevice router = createFogDevice("areaGW-" + id, 2500, 4000, 10000, 10000, 1, 0.0, 60, 45);
        fogDevices.add(router);
        router.setUplinkLatency(5); // latency of connection between area gateway and proxy server is 5 ms

        for (int i = 0; i < numOfCamerasPerArea; i++) {
            String mobileId = id + "-" + i;
            FogDevice camera = addCamera(mobileId, userID, appID, router.getId());
            camera.setUplinkLatency(2); // latency of connection between camera and router is 2 ms
            fogDevices.add(camera);
        }

        router.setParentId(parentID);
        return router;
    }
    
    //Create the PIR Camera Sensors establishing a connection to each Sensor
    private static FogDevice addCamera(String id, int userID, String appID, int parentID) {
    	FogDevice camera = createFogDevice("Camera-" + id, 2000, 1000, 10000, 10000, 3, 0, 50, 30);
        camera.setParentId(parentID);

        FogDevice edgeNode = createFogDevice("EdgeNode-" + id, 1500, 2000, 10000, 10000, 2, 0.0, 30, 20);
        edgeNode.setParentId(camera.getId());
        fogDevices.add(edgeNode);

        Sensor pirSensor = new Sensor("PIR-" + id, "PIR_CAMERA_SENSOR", userID, appID, new DeterministicDistribution(10));
        pirSensor.setGatewayDeviceId(camera.getId());
        pirSensor.setLatency(1.0);
        sensors.add(pirSensor);

        addSensor("TempAirWaterDHT22Sensor-" + id, "TEMP_AIR_QUALITY_DHT22_SENSOR", userID, appID, edgeNode.getId());
        addSensor("LightOPT3001Sensor-" + id, "LIGHT_OPT3001_SENSOR", userID, appID, edgeNode.getId());
        addSensor("HumidityTIHDC1000Sensor-" + id, "HUMIDITY_TI_HDC1000_SENSOR", userID, appID, edgeNode.getId());
        addSensor("EnergyMeter-" + id, "ENERGY_SENSOR", userID, appID, edgeNode.getId());

        return camera;
    }
    
    // Define the IoT/Fog Sensors in the Edge Layer
    private static void addSensor(String id, String sensorType, int userID, String appID, int parentID) {
    	 FogDevice sensorDevice = createFogDevice("Sensor-" + id, 1000, 2500, 15000, 16000, 3, 0, 50, 30);
    	 sensorDevice.setParentId(parentID);
         fogDevices.add(sensorDevice);

         Sensor sensor = new Sensor("Sensor-" + id, sensorType, userID, appID, new DeterministicDistribution(10));
         sensor.setGatewayDeviceId(sensorDevice.getId());
         sensor.setLatency(1.0);
         sensors.add(sensor);

         // Depending on the sensor type, create the corresponding actuator
         if (sensorType.equals("TEMP_AIR_QUALITY_DHT22_SENSOR")) {
             Actuator hvacActuator = new Actuator("HVAC-" + id, userID, appID, "HVAC_Command");
             hvacActuator.setGatewayDeviceId(sensorDevice.getId());
             hvacActuator.setLatency(1.0);
             actuators.add(hvacActuator);
         } else if (sensorType.equals("LIGHT_OPT3001_SENSOR")) {
             Actuator lightingActuator = new Actuator("Lighting-" + id, userID, appID, "Lighting_Command");
             lightingActuator.setGatewayDeviceId(sensorDevice.getId());
             lightingActuator.setLatency(1.0);
             actuators.add(lightingActuator);
         } else if (sensorType.equals("HUMIDITY_TI_HDC1000_SENSOR")) {
             Actuator humidityActuator = new Actuator("Humidity-" + id, userID, appID, "HVAC_Command");
             humidityActuator.setGatewayDeviceId(sensorDevice.getId());
             humidityActuator.setLatency(1.0);
             actuators.add(humidityActuator);
         } else if (sensorType.equals("ENERGY_SENSOR")) {
             Actuator energyActuator = new Actuator("Energy-" + id, userID, appID, "Energy_Command");
             energyActuator.setGatewayDeviceId(sensorDevice.getId());
             energyActuator.setLatency(1.0);
             actuators.add(energyActuator);
         }
     }
    
    // Define the VM requirements of each component
     private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
         List<Pe> peList = new ArrayList<>();
         peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

         int hostId = FogUtils.generateEntityId();
         long storage = 2000000;
         int bw = 10000;

         PowerHost host = new PowerHost(
                 hostId,
                 new RamProvisionerSimple(ram),
                 new BwProvisionerOverbooking(bw),
                 storage,
                 peList,
                 new StreamOperatorScheduler(peList),
                 new FogLinearPowerModel(busyPower, idlePower)
         );

         List<Host> hostList = new ArrayList<>();
         hostList.add(host);

         String arch = "x86";
         String os = "Linux";
         String vmm = "Xen";
         double time_zone = 10.0;
         double cost = 3.0;
         double costPerMem = 0.05;
         double costPerStorage = 0.001;
         double costPerBw = 0.0;

         LinkedList<Storage> storageList = new LinkedList<>();

         FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                 arch, os, vmm, host, time_zone, cost, costPerMem,
                 costPerStorage, costPerBw);

         FogDevice fogdevice = null;
         try {
             fogdevice = new FogDevice(nodeName, characteristics,
                     new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
         } catch (Exception e) {
             e.printStackTrace();
         }

         fogdevice.setLevel(level);
         return fogdevice;
     }
     
     //Generate the Application Model for the Application Modules and Application Edges
     private static Application createApplication(String appID, int userID) {
         Application application = Application.createApplication(appID, userID);

         application.addAppModule("Env_Data_Processing", 10);
         application.addAppModule("Light_Data_Processing", 10);
         application.addAppModule("Humidity_Data_Processing", 10);
         application.addAppModule("Energy_Data_Processing", 10);
         application.addAppModule("Occupancy_Video_Processing", 10); 
         application.addAppModule("Cloud_Storage", 10); 
         application.addAppModule("HVAC_Command", 10);
         application.addAppModule("Security_Command", 10);
         application.addAppModule("Lighting_Command", 10);
         application.addAppModule("Energy_Command", 10);

         // Adding edges based on DAG
         application.addAppEdge("TEMP_AIR_QUALITY_DHT22_SENSOR", "Env_Data_Processing", 700, 1200, "TEMP_AIR_QUALITY_DATA", Tuple.UP, AppEdge.SENSOR);
         application.addAppEdge("Env_Data_Processing", "HVAC_Command", 1000, 1500, "PROCESSED_TEMP_AIR_QUALITY_DATA", Tuple.UP, AppEdge.MODULE);
         application.addAppEdge("Env_Data_Processing", "Cloud_Storage", 1000, 1500, "PROCESSED_TEMP_AIR_QUALITY_DATA", Tuple.UP, AppEdge.MODULE);

         application.addAppEdge("LIGHT_OPT3001_SENSOR", "Light_Data_Processing", 600, 1000, "LIGHT_DATA", Tuple.UP, AppEdge.SENSOR);
         application.addAppEdge("Light_Data_Processing", "Lighting_Command", 600, 1000, "PROCESSED_LIGHT_DATA", Tuple.UP, AppEdge.MODULE);
         application.addAppEdge("Light_Data_Processing", "Cloud_Storage", 600, 1000, "PROCESSED_LIGHT_DATA", Tuple.UP, AppEdge.MODULE);

         application.addAppEdge("HUMIDITY_TI_HDC1000_SENSOR", "Humidity_Data_Processing", 700, 1800, "HUMIDITY_DATA", Tuple.UP, AppEdge.SENSOR);
         application.addAppEdge("Humidity_Data_Processing", "HVAC_Command", 700, 1200, "PROCESSED_HUMIDITY_DATA", Tuple.UP, AppEdge.MODULE);
         application.addAppEdge("Humidity_Data_Processing", "Cloud_Storage", 700, 1200, "PROCESSED_HUMIDITY_DATA", Tuple.UP, AppEdge.MODULE);

         application.addAppEdge("PIR_CAMERA_SENSOR", "Occupancy_Video_Processing", 2500, 10000, "OCCUPANCY_VIDEO_DATA", Tuple.UP, AppEdge.SENSOR);
         application.addAppEdge("Occupancy_Video_Processing", "Security_Command", 2000, 18000, "PROCESSED_OCCUPANCY_VIDEO_DATA", Tuple.UP, AppEdge.MODULE);
         application.addAppEdge("Occupancy_Video_Processing", "Cloud_Storage", 2000, 18000, "PROCESSED_OCCUPANCY_VIDEO_DATA", Tuple.UP, AppEdge.MODULE);

         application.addAppEdge("ENERGY_SENSOR", "Energy_Data_Processing", 800, 1500, "ENERGY_DATA", Tuple.UP, AppEdge.SENSOR);
         application.addAppEdge("Energy_Data_Processing", "Energy_Command", 800, 1500, "PROCESSED_ENERGY_DATA", Tuple.UP, AppEdge.MODULE);
         application.addAppEdge("Energy_Data_Processing", "Cloud_Storage", 800, 1500, "PROCESSED_ENERGY_DATA", Tuple.UP, AppEdge.MODULE);
         
         // Adding tuple mappings
         application.addTupleMapping("Env_Data_Processing", "TEMP_AIR_QUALITY_DATA", "PROCESSED_TEMP_AIR_QUALITY_DATA", new FractionalSelectivity(1.0));
         application.addTupleMapping("Occupancy_Video_Processing", "OCCUPANCY_VIDEO_DATA", "PROCESSED_OCCUPANCY_VIDEO_DATA", new FractionalSelectivity(1.0));
         application.addTupleMapping("Energy_Data_Processing", "ENERGY_DATA", "PROCESSED_ENERGY_DATA", new FractionalSelectivity(1.0));
         application.addTupleMapping("Light_Data_Processing", "LIGHT_DATA", "PROCESSED_LIGHT_DATA", new FractionalSelectivity(1.0));
         application.addTupleMapping("Humidity_Data_Processing", "HUMIDITY_DATA", "PROCESSED_HUMIDITY_DATA", new FractionalSelectivity(1.0));

         return application;
     }
    }