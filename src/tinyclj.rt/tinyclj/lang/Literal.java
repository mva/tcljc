package tinyclj.lang;

import clojure.lang.*;
import java.util.List;
import java.util.regex.Pattern;

/* Called by the reader (initially) and by bootstrap methods (from
   compiled code) to create instances of language-level literals.

   All factories assume that the caller has done any required
   duplicate checking.  That is, they *never* call the
   `createWithCheck` methods.
   
   Collection factories assume that their input (usually an array)
   will never be modified, and can be treated just like immutable
   data.  They should return the appropriate `EMPTY` singletons when
   given an empty argument array.

   The compiler uses the return type of factory methods to determine
   the corresponding literal's type.  Method names mirror the constant
   names from BootstrapMethod.quote().  */
public final class Literal {
  
  public static Symbol symbol(String ns, String name) {
    return Symbol.intern(ns, name);
  }
  
  public static Keyword keyword(String ns, String name) {
    return Keyword.intern(ns, name);
  }
  
  public static IPersistentMap map(Object... init) {
    return clojure.lang.RT.mapUniqueKeys((init.length == 0) ? null : init);
  }

  public static IPersistentSet set(Object... init) {
    if (init.length == 0) {
      return PersistentHashSet.EMPTY;
    } else {
      return PersistentHashSet.create(init);
    }
  }
  
  public static IPersistentVector vector(Object... init) {
    if (init.length == 0) {
      return PersistentVector.EMPTY;
    } else {
      return LazilyPersistentVector.createOwning(init);
    }
  }

  public static ISeq list(Object... init) {
    if (init.length == 0) {
      return PersistentList.EMPTY;
    } else {
      return (ISeq)PersistentList.create(java.util.Arrays.asList(init));
    }
  }

  public static ISeq listOfList(List init) { // called from reader
    if (init.size() == 0) {
      return PersistentList.EMPTY;
    } else {
      return (ISeq)PersistentList.create(init);
    }
  }

  public static Pattern re_pattern(String pattern) {
    return Pattern.compile(pattern);
  }

  public static IObj with_meta(IObj obj, IPersistentMap meta) {
    return obj.withMeta(meta);
  }
}