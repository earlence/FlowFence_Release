/*
 * Copyright (C) 2017 The Regents of the University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
