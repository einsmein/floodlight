package net.floodlightcontroller.myrolechanger;

import java.io.Serializable;

import org.jgroups.Address;

public class AddressInfo implements Serializable{
	protected String controllerId;
	
	public AddressInfo(String cid) {
		this.controllerId = cid;
	}
}
