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

package edu.umich.flowfence.client;

import edu.umich.flowfence.common.Direction;

/**
 * A builder that adds an argument to a pending call.
 * @param <TArg> The type of the argument to add.
 * @param <TNextBuilder> The type of the next builder.
 */
public interface ArgBuilder<TArg, TNextBuilder extends CallBuilder> extends CallBuilder {
    // Non-reference pass semantics. (standard Java)
    // In: takes (? extends TArg), returns nothing.
	public TNextBuilder in(TArg arg);
    public <TIn extends TArg> TNextBuilder in(Sealed<TIn> hArg);

    // Out: takes null, returns TArg.
    public TNextBuilder out(Sealed<? super TArg> hArg);

    // InOut: takes (TIn extends TArg), returns same object.
    public <TIn extends TArg> TNextBuilder inOut(TIn arg, Sealed<? super TIn> dest);
    public <TIn extends TArg> TNextBuilder inOut(Sealed<TIn> hArg);
    public <TIn extends TArg> TNextBuilder inOut(Sealed<TIn> hArg, Sealed<? super TIn> dest);

    // RefInOut: takes (TIn extends TArg), returns *different* object.
    public TNextBuilder refInOut(TArg arg, Sealed<? super TArg> dest);
    public TNextBuilder refInOut(Sealed<TArg> hArg);
    public TNextBuilder refInOut(Sealed<? extends TArg> hArg, Sealed<? super TArg> dest);

    // Default behavior.
    public TNextBuilder arg(TArg arg); // Always in.
    public <TIn extends TArg> TNextBuilder arg(Sealed<TIn> hArg); // In or inout as declared.
    // Out and RefInOut can change object identity, and therefore must be explicit.

    // Null helper. Always in. (InOut would be pointless, and RefInOut(null) == out.)
    public TNextBuilder argNull();
    public TNextBuilder inNull();

    public Class<TArg> getArgClass();
}
