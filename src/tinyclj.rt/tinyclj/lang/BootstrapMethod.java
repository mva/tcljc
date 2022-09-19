package tinyclj.lang;

import java.lang.invoke.*;
import static java.lang.invoke.MethodType.methodType;
import static java.lang.invoke.MethodHandles.*;
import java.util.Arrays;
import clojure.lang.IFn;

public final class BootstrapMethod {
  private static MethodHandle getArityMh(Lookup lookup, Class<?> fnClass, int n)
    throws NoSuchMethodException, IllegalAccessException
  {
    var arityMt = methodType(MethodHandle.class, IFn.class, Integer.TYPE);
    var arityMh = lookup.findStatic(IFn.class, "__arity", arityMt);
    var getterMt = methodType(MethodHandle.class, fnClass);
    return insertArguments(arityMh, 1, n).asType(getterMt);
  }
  
  public static CallSite invokeFn(Lookup lookup, String name, MethodType type)
    throws NoSuchMethodException, IllegalAccessException
  {
    // method handle producing the arity's method handle, with
    // n=number of args
    var arityMh = getArityMh(lookup, type.parameterType(0), type.parameterCount()-1);
    
    // final call: MethodHandle.invoke(arityMh, fnValue, args...)
    var invokeArity = invoker(type);
    // put arity's method handle in front of fnValue
    var mh = foldArguments(invokeArity, arityMh);
    // call site: invokeFn(fnValue, args...)
    return new ConstantCallSite(mh);
  }


  public static Object
    quote(Lookup lookup, String nm, Class<?> type, Object... args)
  {
    switch (nm) {
    case "symbol":
      return ((args.length == 1) ?
              Literal.symbol(null, (String)args[0]) :
              Literal.symbol((String)args[0], (String)args[1]));
    case "keyword":
      return ((args.length == 1) ?
              Literal.keyword(null, (String)args[0]) :
              Literal.keyword((String)args[0], (String)args[1]));
    case "map":
      return Literal.map(args);
    case "set":
      return Literal.set(args);
    case "vector":
      return Literal.vector(args);
    case "list":
      return Literal.list(args);
    case "re-pattern":
      return Literal.re_pattern((String)args[0]);
    case "with-meta":
      return Literal.with_meta((clojure.lang.IObj)args[0],
                               (clojure.lang.IPersistentMap)args[1]);
    default:
      throw new IllegalArgumentException(nm);
    }
  }

  
  public static tinyclj.lang.StaticFnMh
    createStaticFn(Lookup lookup, String nm, Class<?> type, MethodHandle... mhs)
  {
    return tinyclj.lang.StaticFnMh.create(mhs);
  }


  public static CallSite
    bsmCaseClauseCode(Lookup lookup, String name, MethodType type, Object... guards)
    throws NoSuchMethodException, IllegalAccessException
  {
    // count all guards, expanding lists on the way
    int cnt = 0;
    for (Object guard : guards) {
      if (guard instanceof clojure.lang.ISeq) { // aka `seq?`
        cnt += clojure.lang.RT.count(guard);
      } else {
        cnt += 1;
      }
    }

    // create key/value pairs, mapping single guards to their clause code
    var kvs = new Object[cnt*2];
    int i = 0;                  // index into kvs
    int code = 0;               // clause code
    for (Object guard : guards) {
      if (guard instanceof clojure.lang.ISeq) { // aka `seq?`
        for (Object g : (java.util.List)guard) {
          kvs[i++] = g;
          kvs[i++] = code;
        }
      } else {
        kvs[i++] = guard;
        kvs[i++] = code;
      }
      code++;
    }
    var m = clojure.lang.RT.mapUniqueKeys(kvs);
    
    // call RT/caseClauseCode with `m` as its implicit first argument
    var cccMt = methodType(Integer.TYPE, clojure.lang.IPersistentMap.class,
                           Object.class);
    var cccMh = lookup.findStatic(tinyclj.lang.RT.class, "caseClauseCode", cccMt);
    return new ConstantCallSite(insertArguments(cccMh, 0, m));
  }
}
