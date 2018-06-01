package net.floodlightcontroller.myrolechanger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.floodlightcontroller.core.IOFConnectionBackend;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFErrorType;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;
import org.projectfloodlight.openflow.protocol.errormsg.OFBadRequestErrorMsg;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.FloodlightProvider;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.loadcollector.LoadInfo;
import net.floodlightcontroller.core.IFloodlightProviderService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Set;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.statistics.IStatisticsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jgroups.Address;
import org.projectfloodlight.openflow.types.DatapathId;

public class MyRoleChanger extends ReceiverAdapter implements IFloodlightModule, IOFMessageListener, IMyRoleChangerService {
	protected IFloodlightProviderService floodlightProvider;
	protected static Logger logger;
	protected String controllerId;
	protected HashMap<String, Address> ctrlAddress;
	// protected IRestApiService restApi;
	
	JChannel channel;
	protected IStatisticsService statisticCollector;


	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return MyRoleChanger.class.getSimpleName();
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
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

		logger.info("message came to this module");

		if (msg.getType() != OFType.ERROR)
			return Command.CONTINUE;

		OFErrorMsg m = (OFErrorMsg) msg;

		if ((m.getErrType() == OFErrorType.BAD_REQUEST) &&
			  (((OFBadRequestErrorMsg)m).getCode() == OFBadRequestCode.IS_SLAVE) && 
			  (sw.getControllerRole() == OFControllerRole.ROLE_MASTER))
		{
			logger.info("called from masterstate class: controller is slave");
			// logError(m);
			// roleChanger.setSwitchRole(OFControllerRole.ROLE_SLAVE, 
			// 													RoleRecvStatus.RECEIVED_REPLY);
		}

		return Command.CONTINUE;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IMyRoleChangerService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IMyRoleChangerService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IStatisticsService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		logger = LoggerFactory.getLogger(MyRoleChanger.class);

		ctrlAddress = new HashMap<>();
		
		 Map<String, String> configParams = context.getConfigParams(FloodlightProvider.class);
		 controllerId = configParams.get("controllerId");
		statisticCollector = context.getServiceImpl(IStatisticsService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		try {
			channel= new JChannel().setReceiver(this); // use the default config, udp.xml
			channel.connect("CollectorChat");
//			channel.send(null, "FIRST JOIN, MY (RoleChanger) ADDRESS: " + channel.getAddress());
			broadcastControllerId();
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}

	}

	@Override
	public int getANumber() {
		return 1503;
	}
	

	// *************************
	//      JGroups methods
	// *************************

	public void viewAccepted(View new_view) {
	    System.out.println("** view: " + new_view);
	}

//	public void receive(Message msg) {
//		if(msg.getObject() instanceof LoadInfo) {
//			LoadInfo info = (LoadInfo)msg.getObject();
//			ctrlLoad.put(info.controllerId, info.throughput);
//			logger.info("Update load hash map " + ctrlLoad.toString());
//		}
//		
//	    System.out.println(msg.getSrc() + ": is load? " + (msg.getObject() instanceof LoadInfo) + ": " + msg.getObject().toString());
//	    System.out.println("View:\n" + channel.getView());
//	    System.out.println("Address:\n" + channel.getAddress());
//	}

	public void receive(Message msg) {
	    try {
			if(msg.getObject() instanceof AddressInfo) {
				AddressInfo info = (AddressInfo)msg.getObject();
				ctrlAddress.put(info.controllerId, msg.getSrc());
				logger.info("Update address hash map " + ctrlAddress.toString());
			}
	    } catch(Exception ex){
	    	ex.printStackTrace();
	    }
	    
	    System.out.println(msg.getSrc() + ": is address? " + (msg.getObject() instanceof AddressInfo) + ": " + msg.getObject().toString());
	    System.out.println("View:\n" + channel.getView());
	    System.out.println("Address:\n" + channel.getAddress());
	}
	
	public void broadcastControllerId() {		
		try {
			channel.send(null, new AddressInfo(controllerId));
            logger.info("Broadcast my id: " + channel.getAddress());
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}
			
	// **********************************
	//      Switch migration methods
	// **********************************
	
	@Override
	public void doSwitchMigration(Address ctrlAddress, Integer ctrlThreshold,
								  HashMap<Address, Double> ctrlLoads,
                                  HashMap<DatapathId, Double> swLoads) {
		// Only the controller with the highest load can migrate switch.
		if (!isHighestLoadCtrl(ctrlAddress, ctrlLoads))
			return;

		// Migrate to the controller with lowest load.
		// PROBLEM: we are ignoring the fact that this controller can exceed CT.
		Address targetCtrlAddress = getLowestLoadCtrl(ctrlAddress, ctrlLoads);

		// Find target switch to migrate whose load is closest the following value.
		DatapathId targetSwId = getTargetSwitch(
				(ctrlThreshold - ctrlLoads.get(targetCtrlAddress)) * 0.7, swLoads);

		// Send a message to the target controller containing MASTER:targetSwId
		// ...
	}

	private boolean isHighestLoadCtrl(Address ctrlAddress,
									  HashMap<Address, Double> ctrlLoads) {
		Address highestLoadCtrlAddress = ctrlAddress;
		Double highestLoad = ctrlLoads.get(ctrlAddress);

		for (HashMap.Entry<Address, Double> entry: ctrlLoads.entrySet()) {
			if (entry.getValue() > highestLoad) {
				highestLoad = entry.getValue();
				highestLoadCtrlAddress = entry.getKey();
			}
		}

		return (highestLoadCtrlAddress == ctrlAddress);
	}

	private Address getLowestLoadCtrl(Address ctrlAddress,
									  HashMap<Address, Double> ctrlLoads) {
		HashMap<Address, Double> loads = new HashMap<>(ctrlLoads);
		loads.remove(ctrlAddress);
		// POTENTIAL PROBLEM: what if hashmap is empty.

		Iterator itr = loads.entrySet().iterator();
		HashMap.Entry<Address, Double> entry = (HashMap.Entry<Address, Double>) itr;
		Address lowestLoadCtrlAddress = entry.getKey();
		Double lowestLoad = entry.getValue();

		while (itr.hasNext()) {
			entry = (HashMap.Entry<Address, Double>) itr.next();

			if (entry.getValue() < lowestLoad) {
				lowestLoad = entry.getValue();
				lowestLoadCtrlAddress = entry.getKey();
			}
		}

		return lowestLoadCtrlAddress;
	}

	private DatapathId getTargetSwitch(Double loadThreshold,
									   HashMap<DatapathId, Double> swLoads) {
		Iterator itr = swLoads.entrySet().iterator();
		HashMap.Entry<DatapathId, Double> entry = (HashMap.Entry<DatapathId, Double>) itr;
		DatapathId targetId = entry.getKey();
		Double targetLoad = entry.getValue();

		while (itr.hasNext()) {
			entry = (HashMap.Entry<DatapathId, Double>) itr.next();

			if (entry.getValue() < loadThreshold && entry.getValue() > targetLoad) {
				targetLoad = entry.getValue();
				targetId = entry.getKey();
			}
		}

		return targetId;
	}
}
