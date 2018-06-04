package net.floodlightcontroller.loadcollector;

import java.io.Serializable;

import org.jgroups.JChannel;


public class LoadInfo implements Serializable{
	protected String controllerId;
	protected double throughput;
		
	public LoadInfo (String cid, double tp){
		this.controllerId = cid;
		this.throughput = tp;
	}
	
	public static void informLoad(JChannel channel, String controllerId, double load) {
		try {
			channel.send(null, new LoadInfo(controllerId, load));
		}
		catch (Exception e) {
			System.out.print(e.toString());
		}
	}
}
