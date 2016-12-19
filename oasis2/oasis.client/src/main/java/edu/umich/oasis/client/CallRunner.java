package edu.umich.oasis.client;

import android.os.RemoteException;

import edu.umich.oasis.common.TaintSet;

/**
 * A builder that executes a call whose arguments were previously assembled with ArgBuilder.
 * @param <TResult> The result type of the call.
 */
public interface CallRunner<TResult> extends CallBuilder {
    public Sealed<TResult> call() throws RemoteException;
    public void run() throws RemoteException;
    public CallRunner<TResult> after(Sealed<?>... syncHandles);
    public CallRunner<TResult> taintedWith(TaintSet taint);
    public CallRunner<TResult> taintedWith(String taint);
    public CallRunner<TResult> forceSandbox(int sandbox);
    public CallRunner<TResult> asAsync();

    public Class<? extends TResult> getResultClass();
}
