package tinyclj.lang;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import clojure.lang.IObj;
import clojure.lang.IPersistentMap;

// Note: Within a function definition `(fn* nm [x] ... (meta nm)
// ...)`, the inside meta is always `nil` for instances of this class.
// The inside view maps `nm` always to a constant singleton value,
// which has no external decorations applied.
public final class StaticFnMh extends AFnMh {
  protected final MethodHandle[] mhs;
  
  /* The last element of `arities` is non-null for a varargs function,
   * and null otherwise.  It serves as default method handle for large
   * arities.  */
  private final MethodHandle[] arities;

  public MethodHandle __arityOrNull(int n) {
    return arities[Math.min(n, arities.length-1)];
  }
  

  @Override
  public final StaticFnMh __withMetaImpl(IPersistentMap meta) {
    return new StaticFnMh(meta, mhs, arities);
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

  private StaticFnMh(IPersistentMap meta,
                     MethodHandle[] mhs,
                     MethodHandle[] arities) {
    super(meta);
    this.mhs = mhs;
    this.arities = arities;
  }

  private StaticFnMh(IPersistentMap meta, MethodHandle[] mhs) {
    this(meta, mhs, spreadMhs(mhs));
  }

  // Pre-condition: Non-empty `mhs` is sorted by strictly ascending
  // parameter count (aka arity).  Only the last MethodHandle can be
  // varargs.
  public static final StaticFnMh create(MethodHandle[] mhs) {
    return new StaticFnMh(null, mhs);
  }
}
