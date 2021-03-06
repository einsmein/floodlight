package net.floodlightcontroller.mactracker;


import java.util.Collection;
import java.util.Map;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFControllerRole;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFSwitchHandshakeHandler;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.IFloodlightProviderService;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Set;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.statistics.IStatisticsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.projectfloodlight.openflow.types.DatapathId;

import net.floodlightcontroller.myrolechanger.IMyRoleChangerService;

//import org.zeromq.ZMQ;

public class MACTracker implements IOFMessageListener, IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchService;
	// protected IStatisticsService statisticsService;
	
	protected Set<Long> macAddresses;
	protected static Logger logger;
	protected int numOfInPacket;

  protected IMyRoleChangerService roleChangerService;
	
	@Override
	public String getName() {
		return MACTracker.class.getSimpleName();
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
      l.add(IOFSwitchService.class);
      l.add(IMyRoleChangerService.class);
	    return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		// statisticsService = context.getServiceImpl(IStatisticsService.class);
    roleChangerService = context.getServiceImpl(IMyRoleChangerService.class);
		
    macAddresses = new ConcurrentSkipListSet<Long>();
    logger = LoggerFactory.getLogger(MACTracker.class);
    numOfInPacket = 0;
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
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
        
        numOfInPacket = numOfInPacket + 1;
        logger.info("Handled packet number " + numOfInPacket + " : " + roleChangerService.getANumber());
        
        Map<DatapathId, IOFSwitch> switchMap = switchService.getAllSwitchMap();
        
        for (Map.Entry e: switchMap.entrySet())
        {
        	IOFSwitch swe = (IOFSwitch) e.getValue();
        	DatapathId id = (DatapathId) e.getKey();
        	
        	if (swe.getControllerRole() == OFControllerRole.ROLE_MASTER)
        		logger.info("Controller is MASTER of switch " + id);
        	else if (swe.getControllerRole() == OFControllerRole.ROLE_SLAVE)
            		logger.info("Controller is SLAVE of switch " + id);
        	else
        		logger.info("Controller is EQUAL of switch " + id);
        }
        
        List<OFSwitchHandshakeHandler> handlers = switchService.getSwitchHandshakeHandlers();
        for (OFSwitchHandshakeHandler e: handlers)
        {
        	logger.info("there is a handler for switch " + e.getDpid());
        }
        
        return Command.CONTINUE;
	}
	


}
