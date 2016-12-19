package edu.umich.oasis.internal;

import edu.umich.oasis.internal.ResolvedSodaExceptionResult;
import edu.umich.oasis.common.SodaDescriptor;
import edu.umich.oasis.common.SodaDetails;
import android.os.Debug;

interface ISandboxService
{
    ResolvedSodaExceptionResult resolveSoda(in SodaDescriptor desc, in boolean bestMatch, inout SodaDetails details);

	// Process management
    int getPid();
    int getUid();
    void kill();

    void gc();
    Debug.MemoryInfo dumpMemoryInfo();
}