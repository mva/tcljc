package tinyclj.lang;

import clojure.lang.IFn;
import clojure.lang.IObj;
import clojure.lang.IPersistentMap;

/**
 * Replacement for `clojure.lang.AFunction` that is implemented by
 * function-like objects from `clojure.lang.*`, as well as functions
 * derived from `tinyclj.lang.*`.  The sole member `__methodImplCache`
 * has been moved to `tinyclj.lang.AFnMh`, an abstract class that also
 * implements this interface.
 *
 * Open questions: Should this interface provide the `Comparator` part
 * of clojure.lang.AFunction as well?  And what about `Fn` and
 * `Serializable`?
 */
public interface IFunction extends IFn, IObj {
  @Override
  default public IPersistentMap meta() {
    return null;
  }

  @Override
  default public IObj withMeta(IPersistentMap meta) {
    // technically, a `meta` of null is acceptable here
    throw new UnsupportedOperationException();
  }
}
