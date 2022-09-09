package tinyclj.lang;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import clojure.lang.IFn;

// makes clojure.lang.* function callable from Tinyclj code
public abstract class FnBridge implements IFn {
  public Object invoke() {
    return IFn.throwArity(this, 0);
  }

  public Object invoke(Object arg1) {
    return IFn.throwArity(this, 1);
  }

  public Object invoke(Object arg1, Object arg2) {
    return IFn.throwArity(this, 2);
  }

  public Object invoke(Object arg1, Object arg2, Object arg3) {
    return IFn.throwArity(this, 3);
  }

  public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4) {
    return IFn.throwArity(this, 4);
  }

  private static final MethodHandle __a0;
  private static final MethodHandle __a1;
  private static final MethodHandle __a2;
  private static final MethodHandle __a3;
  private static final MethodHandle __a4;

  @Override
  public MethodHandle __arityOrNull(int n) {
    switch (n) {
    case 0: return __a0;
    case 1: return __a1;
    case 2: return __a2;
    case 3: return __a3;
    case 4: return __a4;
    default: return null;
    }
  }
    
  private static final MethodHandle lookupInvoke(MethodHandles.Lookup l, int arity) {
    try {
      return l.findVirtual(FnBridge.class, "invoke",
                           MethodType.genericMethodType(arity));
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  static {
    MethodHandles.Lookup l = MethodHandles.publicLookup();
    __a0 = lookupInvoke(l, 0);
    __a1 = lookupInvoke(l, 1);
    __a2 = lookupInvoke(l, 2);
    __a3 = lookupInvoke(l, 3);
    __a4 = lookupInvoke(l, 4);
  }
}
