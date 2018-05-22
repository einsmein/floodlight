package net.floodlightcontroller.loadcollector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import org.apache.zookeeper.ZooKeeper;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;



public class LoadCollector extends ReceiverAdapter implements IOFMessageListener, IFloodlightModule {
	protected IFloodlightProviderService floodlightProvider;
	protected IDebugCounterService debugCounterService;

	private IDebugCounter ctrPacketIn;
	private ZKConnector zoo;
	
	protected Set<Long> macAddresses;
	protected static Logger logger;
	protected int numPacketIn;
	protected double throughputPacketIn;
	protected long startCountTime;
	protected String controllerId;
	private final String PACKAGE = LoadCollector.class.getPackage().getName();

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
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		debugCounterService = context.getServiceImpl(IDebugCounterService.class);
		registerDebugCounters();
		
	    macAddresses = new ConcurrentSkipListSet<Long>();
	    logger = LoggerFactory.getLogger(LoadCollector.class);
	    numPacketIn = 0;
	    throughputPacketIn = 0;
//		startCountTime = Time.currentElapsedTime();


		 Map<String, String> configParams = context.getConfigParams(FloodlightProvider.class);
		 	controllerId = configParams.get("controllerId");
		   
		   try {
		           zoo = new ZKConnector();
		           zoo.connect("0.0.0.0");
		   } catch (Exception e) {
		           // TODO Auto-generated catch block
		          e.printStackTrace();
		   }
	}
	
	
	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

		try {
			channel= new JChannel().setReceiver(this); // use the default config, udp.xml
			channel.connect("ChatCluster");
//			eventLoop();
//			channel.close();
		} catch(Exception ex) {}
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
        
        long numIn = ctrPacketIn.getCounterValue();
        logger.info("Handled packet number " + numPacketIn + ", counter: " + ctrPacketIn.getCounterValue());

        long lastMod = ctrPacketIn.getLastModified();
        long period = lastMod-startCountTime; 
        
        // If the last update of packet in has been more than a minute
        if(period > 60000) {
        	throughputPacketIn = numIn*1.0/period;
        	ctrPacketIn.reset();
        	startCountTime = lastMod;

            Message noti = new Message(null, "" + numIn + ", throughtput: " + throughputPacketIn + ", period: " + period);
            try {
				channel.send(noti);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            
        }

        // If it's the 10th packet since the last throughput update
        if((numIn % 100) == 0) {
        	throughputPacketIn = numIn*1.0/period;
        	ctrPacketIn.reset();
        	startCountTime = lastMod;
            
	        try {
	            System.out.print("> "); System.out.flush();
	            Message noti = new Message(null, "" + numIn + ", throughtput: " + throughputPacketIn);
	            channel.send(noti);
	        }
	        catch (Exception e) {
	            System.out.print(e.toString());
	        }
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
	    System.out.println(msg.getSrc() + ": " + msg.getObject());
	    System.out.println("View:\n" + channel.getView());
	    System.out.println("Address:\n" + channel.getAddress());
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
