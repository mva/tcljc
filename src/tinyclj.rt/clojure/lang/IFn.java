/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package clojure.lang;

//import java.util.concurrent.Callable;
import java.lang.invoke.MethodHandle;
import java.util.List;

/**
 * Provides access to method handles of a function instance, as well
 * as glue code that bridges from the invoke of Clojure (generic
 * signature, taking Objects and returning an Object) and the method
 * handle view.
 *
 * This interface is in the clojure.lang package to avoid unnecessary
 * diff churn.  Logically, it belongs in tinyclj.lang.
 */
public interface IFn /*extends Callable, Runnable*/ {

  /**
   * Return the method handle for arity `n`.  If the arity does not
   * exist, then the result is either `null` or the returned method
   * handle raises `clojure.lang.ArityException` when invoked.
   * Pre-condition: (not (neg? n))
   */
  public MethodHandle __arityOrNull(int n);

  public static Object throwArity(IFn fn, int n) {
    String name = fn.getClass().getSimpleName();
    throw new clojure.lang.ArityException(n, clojure.lang.Compiler.demunge(name));
  }

  /**
   * Returns a non-null method handle for arity `n`.  The returned
   * method handle may raise `clojure.lang.ArityException` when
   * invoked.
   * Pre-condition: (not (neg? n))
   */
  public static MethodHandle __arity(IFn fn, int n) {
      MethodHandle mh = fn.__arityOrNull(n);
      if (mh == null) {
        return (MethodHandle)throwArity(fn, n);
      } else {
        return mh;
      }
  }

  // enable calling from clojure.lang.* classes:
  
  default Object invoke() {
    try {
      return (Object)__arity(this, 0).invoke(this);
    } catch (Throwable t) {
      throw Util.sneakyThrow(t);
    }
  }

  default Object invoke(Object arg1) {
    try {
      return (Object)__arity(this, 1).invoke(this, arg1);
    } catch (Throwable t) {
      throw Util.sneakyThrow(t);
    }
  }

  default Object invoke(Object arg1, Object arg2) {
    try {
      return (Object)__arity(this, 2).invoke(this, arg1, arg2);
    } catch (Throwable t) {
      throw Util.sneakyThrow(t);
    }
  }

  default Object invoke(Object arg1, Object arg2, Object arg3) {
    try {
      return (Object)__arity(this, 3).invoke(this, arg1, arg2, arg3);
    } catch (Throwable t) {
      throw Util.sneakyThrow(t);
    }
  }

  default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4) {
    try {
      return (Object)__arity(this, 4).invoke(this, arg1, arg2, arg3, arg4);
    } catch (Throwable t) {
      throw Util.sneakyThrow(t);
    }
  }

  // ... do we need more arities here?

  default Object applyTo(ISeq arglist) {
      try {
        int n = RT.count(arglist); // Clojure bounds this to 20
        var mh = __arity(this, n);
        // alternative: mh.bindTo(this).invokeWithArguments((List)arglist)
        return mh.invokeWithArguments((List)RT.cons(this, arglist));
      } catch (Throwable t) {
        throw Util.sneakyThrow(t);
      }
  }
}
