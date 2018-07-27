package edu.umich.oasis.common;

public interface ISensitiveViewAPI {
    void addSensitiveValue(String viewId, String value);
    String readSensitiveValue(String viewId, TaintSet taint);
}
