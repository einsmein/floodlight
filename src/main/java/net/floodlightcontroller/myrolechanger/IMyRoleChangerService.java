package net.floodlightcontroller.myrolechanger;

import java.util.HashMap;
import org.jgroups.Address;
import org.projectfloodlight.openflow.types.DatapathId;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IMyRoleChangerService extends IFloodlightService {
  public int getANumber();

  public void doSwitchMigration(Address ctrlAddress, Integer ctrlThreshold,
                                HashMap<Address, Double> ctrlLoads,
                                HashMap<DatapathId, Double> swLoads);
}
