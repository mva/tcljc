// Copyright (c) Michael van Acken. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (https://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file epl-v20.html at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.
package tinyclj.lang;

import java.lang.invoke.MethodHandle;
import clojure.lang.IFn;
import clojure.lang.IObj;
import clojure.lang.IPersistentMap;

public abstract class AFnMh implements IFunction {
  // orignally a member of `clojure.lang.AFunction`
  public volatile clojure.lang.MethodImplCache __methodImplCache;

  private final IPersistentMap __meta;
  
  @Override
  public IPersistentMap meta() {
    return __meta;
  }

  abstract protected IObj __withMetaImpl(IPersistentMap meta);
  
  @Override
  public IObj withMeta(IPersistentMap meta) {
    if (__meta == meta) {
      return this;
    } else {
      return __withMetaImpl(meta);
    }
  }

  /**
   * Returns direct method handles that can be used by the compiler if
   * the function instance is known at compilation time.  The handles
   * are sorted by ascending arity.  Most of the time, they refer to
   * static or virtual methods, but any other kind of direct handle is
   * allowed as well.  The only restriction is that the referenced
   * member is public and part of a public class.
   */
  abstract public MethodHandle[] __directMethodHandles();

  protected AFnMh(IPersistentMap meta) {
    __meta = meta;
  }
}
