package net.floodlightcontroller.myrolechanger;

import java.io.Serializable;
import org.projectfloodlight.openflow.types.DatapathId;

public class SwitchMigrateInstr implements Serializable{
	protected long dpIdRawValue;

  public SwitchMigrateInstr(long swId) {
    this.dpIdRawValue = swId;
  }
}
