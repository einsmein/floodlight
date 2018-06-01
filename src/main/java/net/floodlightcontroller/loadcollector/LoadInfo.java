package net.floodlightcontroller.loadcollector;

import java.io.Serializable;


public class LoadInfo implements Serializable{
	protected String controllerId;
	protected double throughput;
		
	public LoadInfo (String cid, double tp){
		this.controllerId = cid;
		this.throughput = tp;
	}
}
