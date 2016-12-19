package edu.umich.oasis.client;

import edu.umich.oasis.common.Direction;

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
