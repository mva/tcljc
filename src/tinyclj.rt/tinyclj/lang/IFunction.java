// Copyright (c) Michael van Acken. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (https://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file epl-v20.html at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.
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
