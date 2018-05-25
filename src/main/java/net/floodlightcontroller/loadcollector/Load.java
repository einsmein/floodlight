package net.floodlightcontroller.loadcollector;

import java.io.Serializable;

public class Load implements Serializable{
	protected double throughput;
	
	public Load (double tp){
		this.throughput = tp;
	}
}
