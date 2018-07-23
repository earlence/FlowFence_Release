package edu.umich.oasis.common;

import android.os.RemoteException;

public interface ISensitiveViewAPI {
    void addSensitiveValue(String viewId, String value) throws RemoteException;
    String readSensitiveValue(String viewId, TaintSet taint) throws RemoteException;
}
