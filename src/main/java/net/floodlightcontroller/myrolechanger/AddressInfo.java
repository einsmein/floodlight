package net.floodlightcontroller.myrolechanger;

import org.jgroups.Address;

public class AddressInfo {
	protected String controllerId;
	protected Address address;
	
	public AddressInfo(String cid, Address addr) {
		this.controllerId = cid;
		this.address = addr;
	}
}
