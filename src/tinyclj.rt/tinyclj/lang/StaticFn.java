// Copyright (c) Michael van Acken. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (https://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file epl-v20.html at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.
package tinyclj.lang;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import clojure.lang.IObj;
import clojure.lang.IPersistentMap;

// Note: Within a function definition `(fn* nm [x] ... (meta nm)
// ...)`, the inside meta is always `nil` for instances of this class.
// The inside view maps `nm` always to a constant singleton value,
// which has no external decorations applied.
public final class StaticFn extends AFnMh {
  protected final MethodHandle[] mhs;
  
  /* The last element of `arities` is non-null for a varargs function,
   * and null otherwise.  It serves as default method handle for large
   * arities.  */
  private final MethodHandle[] arities;

  public MethodHandle __arityOrNull(int n) {
    return arities[Math.min(n, arities.length-1)];
  }
  

  @Override
  public final StaticFn __withMetaImpl(IPersistentMap meta) {
    return new StaticFn(meta, mhs, arities);
  }
  
  @Override
  public final MethodHandle[] __directMethodHandles() {
    return mhs;
  }

  private static MethodHandle dropInstanceArgument(MethodHandle mh) {
    MethodHandle mhInstance = MethodHandles.dropArguments(mh, 0, Object.class);
    return mhInstance.withVarargs(mh.isVarargsCollector());
  }
  
  private static final MethodHandle[] spreadMhs(MethodHandle... mhs) {
    MethodHandle highParams = mhs[mhs.length-1];
    boolean isVarargs = highParams.isVarargsCollector();
    int len = highParams.type().parameterCount() + (isVarargs ? 1 : 2);
    MethodHandle[] a = new MethodHandle[len];
    for (MethodHandle mh : mhs) {
      a[mh.type().parameterCount()] = dropInstanceArgument(mh);
    }
    if (isVarargs && (len >= 2) && (a[len-2] == null)) {
      a[len-2] = a[len-1]; // varargs argument can be empty
    }
    return a;
  }

  private StaticFn(IPersistentMap meta,
                     MethodHandle[] mhs,
                     MethodHandle[] arities) {
    super(meta);
    this.mhs = mhs;
    this.arities = arities;
  }

  private StaticFn(IPersistentMap meta, MethodHandle[] mhs) {
    this(meta, mhs, spreadMhs(mhs));
  }

  // Pre-condition: Non-empty `mhs` is sorted by strictly ascending
  // parameter count (aka arity).  Only the last MethodHandle can be
  // varargs.
  public static final StaticFn create(MethodHandle[] mhs) {
    return new StaticFn(null, mhs);
  }
}
