package edu.umich.flowfence.common;

public interface ISensitiveViewAPI {
    void addSensitiveValue(String viewId, String value);
    String readSensitiveValue(String viewId, TaintSet taint);
}
