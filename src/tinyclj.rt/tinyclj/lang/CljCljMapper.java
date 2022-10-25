// Copyright (c) Michael van Acken. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (https://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file epl-v20.html at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.
package tinyclj.lang;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.IdentityHashMap;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import clojure.lang.*;

public final class CljCljMapper {
  // This lookup instance provides access to PUBLIC members of the
  // runtime classes.  Dropping PACKAGE access implicitly drops
  // PROTECTED, UNCONDITIONAL, and PRIVATE as well.
  public static final Lookup rtLookup =
    MethodHandles.lookup().dropLookupMode(Lookup.PACKAGE);
    
  private final Function cplToItf;
  private final Function arrayToCpl;
  private final IdentityHashMap<Object,Object> appToCplCache =
    new IdentityHashMap<Object,Object>();
  private static final ClassLoader sharedClassLoader = Object.class.getClassLoader();
  
  private CljCljMapper(Function cplToItf, Function arrayToCpl) {
    this.cplToItf = cplToItf;
    this.arrayToCpl = arrayToCpl;
  }

  public static CljCljMapper create(Function cplToItf, Function arrayToCpl) {
    return new CljCljMapper(cplToItf, arrayToCpl);
  }


  private Object[] listAsArrayOfApp(List x) {
    Object[] arrayApp = new Object[x.size()];
    int i = 0;
    for (Object obj : x) {
      arrayApp[i] = cplToApp(obj); i++;
    }
    return arrayApp;
  }
  
  private Object itfToMap(Map<Object,Object> x) {
    Object[] arrayApp = new Object[x.size()*2];
    int i = 0;
    for (Map.Entry obj : x.entrySet()) {
      arrayApp[i] = cplToApp(obj.getKey()); i++;
      arrayApp[i] = cplToApp(obj.getValue()); i++;
    }
    return Literal.map(arrayApp);
  }

  private Object itfToVector(List x) {
    return Literal.vector(listAsArrayOfApp(x));
  }
  
  private Object itfToSet(Set x) {
    Object[] arrayApp = x.toArray();
    for (int i=0; i != arrayApp.length; i++) {
      arrayApp[i] = cplToApp(arrayApp[i]);
    }
    return Literal.set(arrayApp);
  }
  
  private Object withMeta(Object obj, Object metaCpl) {
    if (metaCpl == null) {
      return obj;
    } else {
      IPersistentMap metaApp = (IPersistentMap)cplToApp(metaCpl);;
      return Literal.with_meta((IObj)obj, metaApp);
    }
  }
  
  private static final int tagSeq = 0;
  private static final int tagMap = 1;
  private static final int tagSymbol = 2;
  private static final int tagKeyword = 3;
  private static final int tagVector = 4;
  private static final int tagSet = 5;

  private Object itfToAppCollection(Object[] a) { // a == [tag, meta, data...]
    Object res;
    int tag = (int)a[0];
    switch (tag) {
    case tagMap:
      res = itfToMap((Map)a[2]); break;
    case tagSymbol:
      res = Literal.symbol((String)a[2], (String)a[3]); break;
    case tagKeyword:
      return Literal.keyword((String)a[2], (String)a[3]);
    case tagVector:
      res = itfToVector((List)a[2]); break;
    case tagSet:
      res = itfToSet((Set)a[2]); break;
    case tagSeq:
      res = new clojure.lang.LazySeq(() -> {
        return Literal.list(listAsArrayOfApp((List)a[2]));
      });
      break;
    default:
      throw new IllegalStateException(Integer.toString(tag));
    }
    return withMeta(res, a[1]);
  }
  
  private Object itfToApp(Object t) {
    if (t == null) {
      return null;
    } else {
      Class cl = t.getClass();
      if (t instanceof Object[]) {
        return itfToAppCollection((Object[])t);
      } else if (sharedClassLoader == t.getClass().getClassLoader()) {
        return t;
      } else {
        throw new IllegalStateException(cl.toString());
      }
    }
  }
  
  public Object cplToApp(Object t) {
    Object r = itfToApp(cplToItf.apply(t));
    appToCplCache.put(r, t);
    return r;
  }



  private Object metaAsCpl(Object t) {
    return appToCpl(((IObj)t).meta());
  }
  
  private Object[] listAsArrayOfCpl(List t) {
    Object[] arrayCpl = new Object[t.size()];
    int i = 0;
    for (Object x : t) {
      arrayCpl[i] = appToCpl(x); i++;
    }
    return arrayCpl;
  }

  private Object[] taggedNamed(int tag, Object meta, clojure.lang.Named t) {
    return new Object[] {tag, meta, t.getNamespace(), t.getName()};
  }

  private Object[] taggedColl(int tag, Object coll, Object[] data) {
    return new Object[] {tag, metaAsCpl(coll), data};
  }
  
  private Object[] mapToArray(Map<Object,Object> t) {
    Object[] arrayCpl = new Object[t.size()*2];
    int i = 0;
    for (Map.Entry e : t.entrySet()) {
      arrayCpl[i] = appToCpl(e.getKey()); i++;
      arrayCpl[i] = appToCpl(e.getValue()); i++;
    }
    return taggedColl(tagMap, t, arrayCpl);
  }
  
  private Object[] symbolToArray(Symbol t) {
    return taggedNamed(tagSymbol, metaAsCpl(t), t);
  }
  
  private Object[] keywordToArray(Keyword t) {
    return taggedNamed(tagKeyword, null, t);
  }
  
  private Object[] vectorToArray(List t) {
    return taggedColl(tagVector, t, listAsArrayOfCpl(t));
  }
  
  private Object[] setToArray(Set<Object> t) {
    Object[] arrayCpl = new Object[t.size()];
    int i = 0;
    for (Object obj : t) {
      arrayCpl[i] = appToCpl(obj); i++;
    }
    return taggedColl(tagSet, t, arrayCpl);
  }

  private Object[] seqToArray(ISeq t) { // discards meta in seq tails
    return taggedColl(tagSeq, t, listAsArrayOfCpl((List)t));
  }

  private Object uneval(Var var) {
    // The result of a macroexpand can contain a var instance.  This
    // is an object that only exists on the application side.  It must
    // be converted to a symbolic eval-able expression that the
    // compiler can understand: replace clojure.lang.Var with the
    // equivalent special form '(var <var-sym>).
    return ArraySeq.create(Symbol.intern(null, "var"), var.sym);
  }
  
  private Object[] appToArrayCollection(Object t) { // returns [tag, meta, data...]
    if (t instanceof IPersistentMap) {
      return mapToArray((Map)t);
    } else if (t instanceof Symbol) {
      return symbolToArray((Symbol)t);
    } else if (t instanceof Keyword) {
      return keywordToArray((Keyword)t);
    } else if (t instanceof IPersistentVector) {
      return vectorToArray((List)t);
    } else if (t instanceof IPersistentSet) {
      return setToArray((Set)t);
    } else if (t instanceof ISeq) {
      return seqToArray((ISeq)t);
    } else if (t instanceof Var) {
      return (Object[])appToArray(uneval((Var)t));
    } else {
      throw new IllegalStateException(t.getClass().toString());
    }
  }
  
  private Object appToArray(Object t) {
    if (t == null) {
      return null;
    } else if (t instanceof Class) {
      throw new IllegalArgumentException();
    } else if (sharedClassLoader == t.getClass().getClassLoader()) {
      return t;
    } else {
      return appToArrayCollection(t);
    }
  }

  public Object appToCpl(Object t) {
    Object r = appToCplCache.get(t);
    if (r == null) {
      return arrayToCpl.apply(appToArray(t));
    } else {
      return r;
    }
  }
}
