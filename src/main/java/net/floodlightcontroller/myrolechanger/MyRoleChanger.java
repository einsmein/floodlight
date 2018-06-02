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
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.FloodlightProvider;
import net.floodlightcontroller.core.internal.OFSwitchHandshakeHandler;
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
import java.util.logging.FileHandler;
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
	protected IOFSwitchService switchService;

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

		OFErrorMsg m = (OFErrorMsg) msg;

		if ((m.getErrType() == OFErrorType.BAD_REQUEST) &&
			  (((OFBadRequestErrorMsg)m).getCode() == OFBadRequestCode.IS_SLAVE) && 
			  (sw.getControllerRole() == OFControllerRole.ROLE_MASTER))
		{
			logger.info("called from masterstate class: controller is slave");
			// logError(m);
			// roleChanger.setSwitchRole(OFControllerRole.ROLE_SLAVE, 
			// 													RoleRecvStatus.RECEIVED_REPLY);

			for (OFSwitchHandshakeHandler handler: switchService.getSwitchHandshakeHandlers()) {
				if (handler.getDpid() == sw.getId()) {
					handler.sendRoleRequestIfNotPending(OFControllerRole.ROLE_SLAVE);
				}
			}
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
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		logger = LoggerFactory.getLogger(MyRoleChanger.class);

		ctrlAddress = new HashMap<>();
		
		Map<String, String> configParams = context.getConfigParams(FloodlightProvider.class);
		controllerId = configParams.get("controllerId");
		statisticCollector = context.getServiceImpl(IStatisticsService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.ERROR, this);
		try {
			channel= new JChannel().setReceiver(this); // use the default config, udp.xml
			channel.connect("CollectorChat");
//			channel.send(null, "FIRST JOIN, MY (RoleChanger) ADDRESS: " + channel.getAddress());

			
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
		broadcastControllerId();
	    System.out.println("** view: " + new_view);
	}

	public void receive(Message msg) {
	    try {
				if(msg.getObject() instanceof AddressInfo) {
					AddressInfo info = (AddressInfo)msg.getObject();
					ctrlAddress.put(info.controllerId, msg.getSrc());
					logger.info("Update address hash map " + ctrlAddress.toString());

					// JUST TESTING
					// for (OFSwitchHandshakeHandler handler: switchService.getSwitchHandshakeHandlers()) {
					// 	migrateSwitch(info.controllerId, handler.getDpid().getLong());
					// 	break;
					// }
				}
				else if (msg.getObject() instanceof SwitchMigrateInstr) {
					SwitchMigrateInstr m = (SwitchMigrateInstr)msg.getObject();

					// Find the switch handler for the switch with received Datapathid
					// Send role request to be a master
					for (OFSwitchHandshakeHandler handler: switchService.getSwitchHandshakeHandlers()) {
						if (m.dpIdRawValue == handler.getDpid().getLong()) {
							logger.info("This controller should be master of sw with id " + m.dpIdRawValue + " and dpid " + handler.getDpid());
							handler.sendRoleRequestIfNotPending(OFControllerRole.ROLE_MASTER);
							break;
						}
					}
				}
	    } catch(Exception ex){
	    	ex.printStackTrace();
	    }
	    
	    logger.info(msg.getSrc() + ": is address? " + (msg.getObject() instanceof AddressInfo) + ": " + msg.getObject().toString());
	    logger.info("View:\n" + channel.getView());
	    logger.info("Address:\n" + channel.getAddress());
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
	
	public void migrateSwitch(String targetCtrlId, long dpIdRawValue) {
		try {
			Address targetCtrlAddr = ctrlAddress.get(targetCtrlId);
			channel.send(targetCtrlAddr, new SwitchMigrateInstr(dpIdRawValue));
      logger.info("sent message to migrate sw " + dpIdRawValue + " to " + targetCtrlAddr);
		}
		catch (Exception e) {
			System.out.print(e.toString());
		}
	}

	// ***************************
	// 	   Switch Migramtion
	// ***************************

	@Override
	public void doSwitchMigration(Double thisCtrlLoad, Double ctrlThreshold,
								  HashMap<String, Double> ctrlLoads,
                                  HashMap<DatapathId, Double> swLoads) {
		logger.info("================================== try migration");
		
		if (ctrlLoads.size() == 0) {
			logger.info("controllerLoads size is 0");
			return;
		}
		
		// Only the controller with the highest load can migrate switch.
		if (!isHighestLoadCtrl(thisCtrlLoad, ctrlLoads)) {
			logger.info("this controller does NOT have the highest load");
			return;
		}
		else
			logger.info("this controller has the highest load");

		// Migrate to the controller with lowest load.
		// PROBLEM: we are ignoring the fact that this controller can exceed CT.
		String targetCtrlId = getLowestLoadCtrl(ctrlLoads);

		if (swLoads.size() == 0 || targetCtrlId.length() == 0) {
			logger.info("swLoads size is 0 or size of targetCtrlId is 0");
			return;
		}
		
		// Find target switch to migrate whose load is closest the following value.
		DatapathId targetSwId = getTargetSwitch(
				(ctrlThreshold - ctrlLoads.get(targetCtrlId)) * 0.7, swLoads);

		// Send a message to the target controller containing MASTER:targetSwId
		migrateSwitch(targetCtrlId, targetSwId.getLong());
	}

	private boolean isHighestLoadCtrl(Double thisCtrlLoad,
									  HashMap<String, Double> ctrlLoads) {
		Double highestLoad = thisCtrlLoad;

		for (HashMap.Entry<String, Double> entry: ctrlLoads.entrySet()) {
			if (controllerId.equals(entry.getKey()))
				continue;
			
			logger.info("comparing load with " + entry.getKey() + " : " + entry.getValue());
			if (entry.getValue() > thisCtrlLoad) {
				return false;
			}
		}

		return true;
	}

	private String getLowestLoadCtrl(HashMap<String, Double> ctrlLoads) {
/*		Iterator itr = ctrlLoads.entrySet().iterator();
		HashMap.Entry<String, Double> entry = (HashMap.Entry<String, Double>) itr;

		// Set the first element of HashMap as return value
		String lowestLoadCtrlId = entry.getKey();
		Double lowestLoad = entry.getValue();

		while (itr.hasNext()) {
			entry = (HashMap.Entry<String, Double>) itr.next();

			if (entry.getValue() < lowestLoad) {
				lowestLoad = entry.getValue();
				lowestLoadCtrlId = entry.getKey();
			}
		}*/
				
		double lowestLoad = 100.0;
		String lowestLoadCtrlId = "";
		
		logger.info("finding lowest load controller");
		
		for (HashMap.Entry<String, Double> entry: ctrlLoads.entrySet()) {			
			logger.info(entry.getKey() + " : " + entry.getValue());
			
			if (entry.getKey().equals(controllerId))
			{
				logger.info("skipped.");
				continue;
			}
			
			if (entry.getValue() < lowestLoad) {
				lowestLoad = entry.getValue();
				lowestLoadCtrlId = entry.getKey();
			}
		}
		
		logger.info("lowest one is: " + lowestLoadCtrlId + " : " + lowestLoad);

		return lowestLoadCtrlId;
	}

	// POTENTIAL ISSUE: What if there is no switch satisfying the formula.
	private DatapathId getTargetSwitch(Double loadThreshold,
									   HashMap<DatapathId, Double> swLoads) {
		/*Iterator itr = swLoads.entrySet().iterator();
		HashMap.Entry<DatapathId, Double> entry = (HashMap.Entry<DatapathId, Double>) itr;
		DatapathId targetId = entry.getKey();
		Double targetLoad = entry.getValue();

		while (itr.hasNext()) {
			entry = (HashMap.Entry<DatapathId, Double>) itr.next();

			if (entry.getValue() < loadThreshold) {
				// In case the first element's load is larger than threshold
				if (targetLoad > loadThreshold) {
					targetLoad = entry.getValue();
					targetId = entry.getKey();
				}
				else if (entry.getValue() > targetLoad) {
					targetLoad = entry.getValue();
					targetId = entry.getKey();
				}
			}
		}*/
		
		logger.info("finding good switch lower than " + loadThreshold);
		
		DatapathId targetId = swLoads.entrySet().iterator().next().getKey();
		Double targetLoad = 0.0;
		
		for (HashMap.Entry<DatapathId, Double> entry: swLoads.entrySet()) {
			logger.info("considering " + entry.getKey() + " : " + entry.getValue());
			if (entry.getValue() < loadThreshold && entry.getValue() > targetLoad) {
				targetLoad = entry.getValue();
				targetId = entry.getKey();
			}
		}

		return targetId;
	}
}
