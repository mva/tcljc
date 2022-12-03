// Copyright (c) Michael van Acken. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (https://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file epl-v20.html at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.
package tinyclj.lang;

import clojure.lang.ISeq;
import clojure.lang.Cons;
import clojure.lang.Symbol;
import java.util.Map;
import java.util.regex.Pattern;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.invoke.MethodHandles.Lookup;

public class Compiler {
  // Because application and compiler can live in different class
  // loaders with a different runtime (e.g. when bootstrapping
  // tclj-in-tclj), it is easier to duplicate munge/demunge code.  See
  // tinyclj/frontend/config.cljt

  private static final String nmRepl(int c) {
    switch (c) {
    case 59:
    case 91:
      throw new IllegalArgumentException(); // one of ";["
    case 47: return "_SLASH_";              // "/"
    case 92: return "_BSLASH_";             // "\\"
    case 60: return "_LT_";                 // "<"
    case 62: return "_GT_";                 // ">"
    case 46: return "_DOT_";                // "."
    default: return null;
    }
  }

  private static final int upToReplacement(String nm, int s) {
    int i = s;
    while ((i != nm.length()) && (nmRepl(nm.charAt(i)) == null)) {
      i++;
    }
    return i;
  }

  private static final String replaceAll(String nm, int s, StringBuilder b) {
    while (true) {
      int e = upToReplacement(nm, s);
      if (e == nm.length()) {
        return b.append(nm.substring(s)).toString();
      } else {
        b.append(nm.substring(s, e)).append(nmRepl(nm.charAt(e)));
        s = e+1;
      }
    }
  }
  
  static public String munge(String nm) {
    int i = upToReplacement(nm, 0);
    if (i == nm.length()) {
      return nm;
    } else {
      return replaceAll(nm, 0, new StringBuilder());
    }
  }


  private static final Map<String,String> deRepl =
    Map.of("_SLASH_", "/",
           "_BSLASH_", "\\\\",  // backslash is special in replacement string
           "_LT_", "<",
           "_GT_", ">",
           "_DOT_", ".");

  private static final Pattern P = Pattern.compile("_(?:DOT|[LG]T|B?SLASH)_");
  
  public static final String demunge(String s) {
    if (s.indexOf('_') < 0) {
      return s;
    } else {
      return P.matcher(s).replaceAll(m -> deRepl.getOrDefault(m.group(), null));
    }
  }


  // duplicates code in exports.clj:
  private static final boolean publicStatic(int mods) {
    return Modifier.isStatic(mods) && Modifier.isPublic(mods);
  }
  
  private static final AccessibleObject lookupMember(Class cl, String name) {
    String mnm = munge(name);
    try {
      var f = cl.getField(mnm);
      return publicStatic(f.getModifiers()) ? f : null;
    } catch (NoSuchFieldException nsfe) {
      try {
        var m = cl.getMethod(mnm);
        return publicStatic(m.getModifiers()) ? m : null;
      } catch (NoSuchMethodException nsme) {
        return null;
      }
    }
  }

  // Problem in short: tinyclj/core.cljt loads core_print.cljt, which
  // refers via MultiFn.java to functions in tinyclj.core -- before
  // tinyclj.core.___ exists.  So track how far translation of the
  // core namespace has progressed, and use the most recent class file
  // for lookups.  The other part of the puzzle is to hack (load
  // "core_print") to flush the current tinyclj.core namespace segment
  // (and define and initialize it) before translating the loaded
  // file's forms.
  static String partialCoreNamespace = "";

  private static final Class namespaceClass(Lookup l, String ns) {
    if (ns == null) {
      ns = l.lookupClass().getPackageName();
    }
    String className = ns+".";
    try {
      if (partialCoreNamespace.startsWith(className)) {
        return l.findClass(partialCoreNamespace);
      } else {
        // Note: this code path assumes that the complete namespace
        // `ns` has been compiled and written out before this call;
        // this fails for all top-level forms within `ns` itself
        // unless a workaround similar to `partialCoreNamespace` is
        // established for all classes
        return l.findClass(className);
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("failed to resolve namespace: "+ns, e);
    }
  }

  private static final Object valueOf(AccessibleObject member) {
    try {
      if (member instanceof Field) {
        return ((Field)member).get(null);
      } else {
        return ((Method)member).invoke(null);
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("failed to get var value", e);
    }
  }
  
  private static final AccessibleObject resolve(Lookup l, String namespace, String name) {
    Class cl = namespaceClass(l, namespace);
    return lookupMember(cl, name);
  }
  
  private static final AccessibleObject resolve(Lookup l, Object op) {
    if (op instanceof Symbol) {
      Symbol sym = (Symbol)op;
      return resolve(l, sym.getNamespace(), sym.getName());
    } else {
      return null;
    }
  }
  
  public static final AFnMh getFn(Lookup l, String namespace, String name) {
    var member = resolve(l, namespace, name);
    return (member != null) ? (AFnMh)valueOf(member) : null;
  }
}
