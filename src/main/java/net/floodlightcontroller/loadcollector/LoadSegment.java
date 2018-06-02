package net.floodlightcontroller.loadcollector;

public class LoadSegment {
	double[] segment;
	double currentLoad;
	int currentSegment;
	
	public LoadSegment() {
		this.currentLoad = 0;
		this.currentSegment = 0;
		this.segment = new double[] {0.4, 0.9, 1.3, 1.5};
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
