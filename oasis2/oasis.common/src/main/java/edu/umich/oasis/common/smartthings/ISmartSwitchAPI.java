package edu.umich.oasis.common.smartthings;

import java.util.List;

import edu.umich.oasis.common.IDynamicAPI;

/**
 * Created by jpaupore on 2/4/16.
 */
public interface ISmartSwitchAPI {
    List<SmartDevice> getSwitches();
    void switchOp(String op, String switchId);
}
