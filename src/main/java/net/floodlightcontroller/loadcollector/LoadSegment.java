package net.floodlightcontroller.loadcollector;

public class LoadSegment {
	double[] segment;
	double currentLoad;
	int currentSegment;
	
	public LoadSegment(double CT) {
		this.currentLoad = 0;
		this.currentSegment = 0;
		this.segment = new double[] {0.35*CT, 0.6*CT, 0.8*CT, 0.95*CT};
	}
	
	// return boolean value whether the load has crossed segment
	public boolean updateLoad(double load) {
		int newSegment = segment.length;
		for(int i = 0; i < segment.length; i++) {
			if(load<segment[i]) {
				newSegment = i;
				break;
			}
		}
		this.currentLoad = load;
		if(currentSegment != newSegment) {
			this.currentSegment = newSegment;
			return true;
		}
		return false;
	}
}
