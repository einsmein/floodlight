package net.floodlightcontroller.myrolechanger;

import java.util.Collection;
import java.util.HashMap;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyRoleChanger extends ReceiverAdapter implements IFloodlightModule, IOFMessageListener, IMyRoleChangerService {
	protected IFloodlightProviderService floodlightProvider;
	protected static Logger logger;
	protected String controllerId;
	protected HashMap<String, Address> ctrlAddress;
	// protected IRestApiService restApi;
	
	JChannel channel;

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
		// l.add(IRestApiService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		logger = LoggerFactory.getLogger(MyRoleChanger.class);

		 Map<String, String> configParams = context.getConfigParams(FloodlightProvider.class);
		 controllerId = configParams.get("controllerId");
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		try {
			channel= new JChannel().setReceiver(this); // use the default config, udp.xml
			channel.connect("RoleChangerChat");
			channel.send(null, "FIRST JOIN, MY (RoleChanger) ADDRESS: " + channel.getAddress());
			
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

	public void receive(Message msg) {
		if(msg.getObject() instanceof AddressInfo) {
			AddressInfo info = (AddressInfo)msg.getObject();
			ctrlAddress.put(info.controllerId, info.address);
			logger.info("Update address hash map " + ctrlAddress.toString());
		}
		
	    System.out.println(msg.getSrc() + ": " + (msg.getObject() instanceof LoadInfo));
	    System.out.println("View:\n" + channel.getView());
	    System.out.println("Address:\n" + channel.getAddress());
	}
	
	public void broadcastAddress(Address address) {		
		try {
			channel.send(null, new AddressInfo(controllerId, channel.getAddress()));
            logger.info("Broadcast my address: " + channel.getAddress());
		}
		catch (Exception e) {
			System.out.print(e.toString());
		}
	}
	
	public void sendMessage(String message) {
		try {
//			Address a = ctrlLoads.keySet().iterator().next();
//			channel.send(a, message);
//            logger.info("sending "+ message + "to " + a);
		}
		catch (Exception e) {
			System.out.print(e.toString());
		}
	}

}
