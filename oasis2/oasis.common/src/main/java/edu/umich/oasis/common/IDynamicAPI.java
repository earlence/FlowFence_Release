package edu.umich.oasis.common;

/**
 * Created by jpaupore on 10/4/15.
 */
public interface IDynamicAPI {
    Object invoke(String method, Object... args);
}
