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
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.function.Function;
import clojure.lang.ISeq;
import clojure.lang.Cons;

public final class RT {
  public static boolean truthyReference (Object x) {
    if (x instanceof Boolean) {
      return ((Boolean)x).booleanValue();
    } else {
      return (x != null);
    }
  }

  public static int booleanToZeroOne (boolean x) {
    return x ? 1 : 0;
  }

  private static final Integer DEFAULT_CLAUSE_CODE = -1;
  public static int caseClauseCode(clojure.lang.IPersistentMap m, Object select) {
    return ((Integer)m.valAt(select, DEFAULT_CLAUSE_CODE)).intValue();
  }

  public static Exception noMatchingClause(Object value) {
    String msg = "No matching clause: "+value;
    return new IllegalArgumentException (msg);
  }


  public static MethodHandle[] methodHandleArray (MethodHandle... mhs) {
    return mhs;
  }

  public static MethodHandle[] methodHandleArray (MethodHandle mh) {
    return new MethodHandle[] {mh};
  }


  // Generic apply should also work for function-like objects.
  public static Object apply(clojure.lang.IFn f, Object arglist) {
    // make sure argument is nil if `arglist` is empty
    return f.applyTo(clojure.lang.RT.seq(arglist));
  }
  
  public static Object applyMacroMapped(Function cplToItf, Function arrayToCpl,
                                        AFnMh f, Object form0, Object env0) {
    CljCljMapper m = CljCljMapper.create(cplToItf, arrayToCpl);
    ISeq form = (ISeq)m.cplToApp(form0);
    Object env = m.cplToApp(env0);
    // Pass the arguments of the macro invocation as a seq.  The
    // standard IFn.applyTo logic select the matching macro arity and
    // maps the seq elements to the arity's parameters.
    Object res = f.applyTo(new Cons(form, new Cons(env, form.more())));
    return m.appToCpl(res);
  }

  public static void pushFileContext (String ns) {
    var nsSym = clojure.lang.Symbol.intern(ns);
    var nsInst = clojure.lang.Namespace.findOrCreate(nsSym);
    var map = clojure.lang.RT.mapUniqueKeys(clojure.lang.RT.CURRENT_NS,
                                            nsInst);
    clojure.lang.Var.pushThreadBindings(map);
  }
  
  public static void popFileContext () {
    clojure.lang.Var.popThreadBindings();
  }


  public static void createNamespace (String ns) {
    var nsSym = clojure.lang.Symbol.intern(ns);
    if (!nsSym.equals(clojure.lang.RT.CLOJURE_NS.name)) {
      // make sure that no stale vars survive that refer to a previous
      // instance of this namespace
      var nsInst = clojure.lang.Namespace.remove(nsSym);
    }
    var nsInst = clojure.lang.Namespace.findOrCreate(nsSym);
  }


  public static void markCoreInitialization(String className) {
    Compiler.partialCoreNamespace = className;
  }
  
  private static MethodHandles.Lookup coreLookup;

  // invoked early in tinyclj/lang/core.cljt
  public static void setCoreLookup(MethodHandles.Lookup l) {
    coreLookup = l;
  }
  
  public static AFnMh resolveRuntimeFn(String namespace, String name) {
    var fn = Compiler.getFn(coreLookup, namespace, name);
    if (fn == null) {
      throw new IllegalStateException("failed to resolve runtime fn "+
                                      namespace+"/"+name);
    } else {
      return fn;
    }
  }

  private static boolean isArityName(String nm) {
    return (nm.length() >= 3) && nm.startsWith("fn") &&
      (nm.charAt(2) >= '0') && (nm.charAt(2) <= '9');
  }

  // Sort by ascending parameter count, and then by absence of varargs
  // flag (i.e. a non-varargs MethodHandle is considered larger).
  private static int compareCountAndVarArgs(MethodHandle mh1, MethodHandle mh2) {
    var x = Integer.compare(mh1.type().parameterCount(),
                            mh2.type().parameterCount());
    return (x == 0) ?
      -Boolean.compare(mh1.isVarargsCollector(), mh2.isVarargsCollector()) :
      x;
  }
  private static final java.util.Comparator<MethodHandle> cmpMH =
    RT::compareCountAndVarArgs;

  /** Returns array of method handles (sorted by parameter count) if
   * `x` is a function definition, and null otherwise. */
  public static MethodHandle[] arityHandlesIfFn(MethodHandles.Lookup l,
                                                Class memberType, Object x) {
    if (!AFnMh.class.isAssignableFrom(memberType)) {
      return null; // if not emitted as function typed value by compiler
    } else if (x instanceof StaticFnMh) {
      return ((StaticFnMh)x).__directMethodHandles();
    } else if (x instanceof AFnMh) {
      var a = new ArrayList();
      for (Method m : x.getClass().getDeclaredMethods()) {
        if (isArityName(m.getName())) {
          try {
            a.add(l.unreflect(m));
          } catch (IllegalAccessException e) {
            // omit method
          }
        }
      }
      a.sort(cmpMH);

      int n = a.size();
      if (n >= 2) {
        var mh1 = (MethodHandle)a.get(n-2);
        var mh2 = (MethodHandle)a.get(n-1);
        if (mh1.type().parameterCount() == mh2.type().parameterCount()) {
          // varargs bridge function delegates to ISeq implementation
          // with same parameter count: delete the non-varargs (and
          // therefore ISeq) MethodHandle
          a.remove(n-1);
        }
      }
      
      return (MethodHandle[])a.toArray(new MethodHandle[a.size()]);
    } else {
      return null;
    }
  }


  private static String typeParameter(String typeName) {
    int a = typeName.indexOf('<');
    if (a >= 0) {
      return typeName.substring(a+1, typeName.lastIndexOf('>'));
    } else {
      return null;
    }
  }

  private static Class toClass(MethodHandles.Lookup l, String type) {
    try {
      int a = type.indexOf('[');
      if (a < 0) {
        return l.findClass(type);
      } else {
        Class cl = l.findClass(type.substring(0, a));
        int rank = (type.length()-a) / 2;
        for (int i=0; i<rank; i++) {
          cl = cl.arrayType();
        }
        return cl;
      }
    } catch (Exception e) {
      throw clojure.lang.Util.sneakyThrow(e);
    }
  }
  private static Object[] publicVarInfo(MethodHandles.Lookup l, Field f) {
    // parse generic type like "clojure.lang.Var<java.lang.String>"
    // or "clojure.lang.Var<java.lang.String[]>"
    String typeParam = typeParameter(f.getGenericType().getTypeName());
    if (typeParam != null) {
        return new Object[]{":var", toClass(l, typeParam)};
    }
    return null;
  }

  private static Object[] publicMacroInfo(AFnMh mfn, Macro ma) {
    var macroValue = ma.macroValue();
    return new Object[]{":macro", mfn, "".equals(macroValue) ? null : macroValue};
  }
  
  private static Object[] publicAliasInfo(Alias aa) {
    var of = aa.of();
    return new Object[]{":alias", of};
  }
  
  private static Object[] publicFieldInfo(MethodHandles.Lookup l, Field f) {
    if (f.getType() == clojure.lang.Var.class) {
      return publicVarInfo(l, f);
    } else if (AFnMh.class.isAssignableFrom(f.getType())) {
      Annotation ma;
      if ((ma = f.getDeclaredAnnotation(Macro.class)) != null) {
        try {
          return publicMacroInfo((AFnMh)f.get(null), (Macro)ma);
        } catch (Exception e) {
          throw clojure.lang.Util.sneakyThrow(e);
        }
      }
    } else if (f.getType() == Object.class) {
      Annotation aa;
      if ((aa = f.getDeclaredAnnotation(Alias.class)) != null) {
        try {
          return publicAliasInfo((Alias)aa);
        } catch (Exception e) {
          throw clojure.lang.Util.sneakyThrow(e);
        }
      }
    }
    return null;
  }
  private static Object[] publicMethodInfo(MethodHandles.Lookup l, Method m) {
    if (AFnMh.class.isAssignableFrom(m.getReturnType())) {
      Annotation ma;
      if ((ma = m.getDeclaredAnnotation(Macro.class)) != null) {
        try {
          return publicMacroInfo((AFnMh)m.invoke(null, (Object[])null), (Macro)ma);
        } catch (Exception e) {
          throw clojure.lang.Util.sneakyThrow(e);
        }
      }
    }
    return null;
  }
  public static Object[] publicDefInfo(MethodHandles.Lookup l, Member m) {
    if (m instanceof Field) {
      return publicFieldInfo(l, (Field)m);
    } else if (m instanceof Method) {
      return publicMethodInfo(l, (Method)m);
    } else {
      return null;
    }
  }
}
