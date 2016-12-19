package edu.umich.oasis.common;

import java.util.Set;

/**
 * Created by jpaupore on 2/11/16.
 */
public interface ITaintAPI {
    void addTaint(TaintSet ts);
    TaintSet removeTaint(TaintSet toRemove);
    TaintSet removeTaint(Set<String> toRemove);
}
