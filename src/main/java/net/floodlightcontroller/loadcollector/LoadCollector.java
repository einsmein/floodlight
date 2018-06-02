package net.floodlightcontroller.loadcollector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import com.google.gson.Gson;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.FloodlightProvider;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.mactracker.MACTracker;
import net.floodlightcontroller.packet.Ethernet;
import zkconnecter.ZKConnector;

//import org.apache.zookeeper.ZooKeeper;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;

import org.projectfloodlight.openflow.types.DatapathId;
import net.floodlightcontroller.myrolechanger.IMyRoleChangerService;

public class LoadCollector extends ReceiverAdapter implements IOFMessageListener, IFloodlightModule {
	protected IFloodlightProviderService floodlightProvider;
	protected IDebugCounterService debugCounterService;
	protected IMyRoleChangerService roleChangerService;

	private IDebugCounter ctrPacketIn;
	private HashMap<String, Double> ctrlLoad;

	// HashMap to store latest measured switch load
	private HashMap<DatapathId, Double> switchLoads;

	// HashMap to store INPACKETs from each switch since last switch load update.
	private HashMap<DatapathId, Integer> inpacketPerSwitchCounters;

	private Integer ctrlThreshold;

	
	protected Set<Long> macAddresses;
	protected static Logger logger;
	protected int numPacketIn;
//	protected double throughputPacketIn;
	protected long startCountTime;
	protected String controllerId;
	private final String PACKAGE = LoadCollector.class.getPackage().getName();
	
	protected LoadSegment loadSegment;

    JChannel channel;
    String user_name=System.getProperty("user.name", "n/a");

    
	@Override
	public String getName() {
		return LoadCollector.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
		    new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IMyRoleChangerService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		debugCounterService = context.getServiceImpl(IDebugCounterService.class);
		roleChangerService = context.getServiceImpl(IMyRoleChangerService.class);

		Map<String, String> configParams = context.getConfigParams(FloodlightProvider.class);
		controllerId = configParams.get("controllerId");
		 
		registerDebugCounters();

		ctrlLoad = new HashMap<>();
	    macAddresses = new ConcurrentSkipListSet<Long>();
	    logger = LoggerFactory.getLogger(LoadCollector.class);
	    numPacketIn = 0;
//	    throughputPacketIn = 0;
		startCountTime = System.currentTimeMillis();
		loadSegment = new LoadSegment();
		
		switchLoads = new HashMap<>();
		inpacketPerSwitchCounters = new HashMap<>();
		ctrlThreshold = 50;
	}
	
	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

		try {
			channel= new JChannel().setReceiver(this); // use the default config, udp.xml
			channel.connect("CollectorChat");
			channel.send(null, "FIRST JOIN, MY (LoadCollector) ADDRESS: " + channel.getAddress());
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}
	
	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		Ethernet eth =
                IFloodlightProviderService.bcStore.get(cntx,
                                            IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
 
        Long sourceMACHash = eth.getSourceMACAddress().getLong();
        if (!macAddresses.contains(sourceMACHash)) {
            macAddresses.add(sourceMACHash);
            logger.info("MAC Address: {} seen on switch: {}",
                    eth.getSourceMACAddress().toString(),
                    sw.getId().toString());
        }
        
        numPacketIn = numPacketIn + 1;
        ctrPacketIn.increment();

        // Increment number of INPACKET for this switch
        DatapathId swId = sw.getId();
        if (inpacketPerSwitchCounters.containsKey(swId))
        	inpacketPerSwitchCounters.put(sw.getId(), inpacketPerSwitchCounters.get(swId) + 1);
        else
        	inpacketPerSwitchCounters.put(sw.getId(), 1);
        // =============================================
        
        long numIn = ctrPacketIn.getCounterValue();
        long lastMod = ctrPacketIn.getLastModified();
        long period = lastMod-startCountTime; 
        logger.info("Handled packet number " + numPacketIn + ", counter: " + numIn);
        
        // If the last update of packet in has been more than a minute
        // If it's the 10th packet since the last throughput update
        if(period > 10000 || numIn % 100 == 0) {
//        	throughputPacketIn = numIn * 1.0 / (period / 1000);
        	double newLoad = numIn * 1.0 / (period / 1000);
			logger.info("************** throughput = " + newLoad + ", prev = " + loadSegment.currentLoad);
        	boolean inform = loadSegment.updateLoad(numIn * 1.0 / (period / 1000));
        	ctrPacketIn.reset();
        	startCountTime = lastMod;
        	if (inform) {
            	informLoad(newLoad);	// inform load through segment?
        	}
            
          // Update the load of each switch (unit: INPACKET per second)
        	for (HashMap.Entry<DatapathId, Integer> entry: inpacketPerSwitchCounters.entrySet()) {
        		switchLoads.put(entry.getKey(), (double) (entry.getValue()) / period * 1000);

        		// Reset INPACKET counter for the switch
        		inpacketPerSwitchCounters.put(entry.getKey(), 0);
        	}
        	// ==================================================================

        	// Try to migrate switch if the current ctrl load exceeds CT
        	// if (throughputPacketIn > ctrlThreshold)
        	// 	roleChangerService.doSwitchMigration(address, ctrlThreshold, ctrlLoads, switchLoads);
        }
		
        return Command.CONTINUE;
	}
	
	// *************************
	//      JGroups methods
	// *************************
	
	private void eventLoop() {
	    BufferedReader in=new BufferedReader(new InputStreamReader(System.in));
	    while(true) {
	        try {
	            System.out.print("> "); System.out.flush();
	            String line=in.readLine().toLowerCase();
	            if(line.startsWith("quit") || line.startsWith("exit"))
	                break;
	            line="[" + user_name + "] " + line;
	            Message msg=new Message(null, line);
	            channel.send(msg);
	        }
	        catch(Exception e) {
	        }
	    }
	}
	public void viewAccepted(View new_view) {
	    System.out.println("** view: " + new_view);
	}

	public void receive(Message msg) {
		if(msg.getObject() instanceof LoadInfo) {
			LoadInfo info = (LoadInfo)msg.getObject();
			ctrlLoad.put(info.controllerId, info.throughput);
			logger.info("Update load hash map " + ctrlLoad.toString());
		}
		
	    System.out.println(msg.getSrc() + ": is load? " + (msg.getObject() instanceof LoadInfo) + ": " + msg.getObject().toString());
	    System.out.println("View:\n" + channel.getView());
	    System.out.println("Address:\n" + channel.getAddress());
	}
	
	public void informLoad(double load) {		
		try {
			channel.send(null, new LoadInfo(controllerId, load));
			logger.info("Sent throughput message and reset counter, throughput = " + load);
		}
		catch (Exception e) {
			System.out.print(e.toString());
		}
	}

	// *************************
	//  Register debug counters
	// *************************

	private void registerDebugCounters() throws FloodlightModuleException {
		if (debugCounterService == null) {
			logger.error("Debug Counter Service not found.");
		}
		debugCounterService.registerModule(PACKAGE);
		ctrPacketIn = debugCounterService.registerCounter(PACKAGE, "packet-in",
				"Number of packet_in's seen");
	}

}
