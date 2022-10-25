// Copyright (c) Michael van Acken. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (https://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file epl-v20.html at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.
package tinyclj.lang;

import java.net.URLClassLoader;
import java.net.URL;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class DynamicClassLoader extends URLClassLoader {
  static {
    var success = registerAsParallelCapable();
    assert(success);
  }
  
  private static final void printClassLoader(ClassLoader l) {
    if (l != null) {
      try {
        System.out.println("ClassLoader: "+l);
        java.util.Enumeration<URL> e = l.getResources("");
        while (e.hasMoreElements()) {
          System.out.println("ClassLoader Resource: " + e.nextElement());
        }
      } catch (java.io.IOException e) {
        throw new Error(e);
      }
      printClassLoader(l.getParent());
    }
  }
  
  public DynamicClassLoader(String name, URL[] urls, ClassLoader parent) {
    super(name, urls, parent);
    //printClassLoader(Thread.currentThread().getContextClassLoader());
  }

  public MethodHandles.Lookup lookupInPackage(byte[] classBytes) {
    try {
      // call into protected final method
      Class cl = defineClass(null, classBytes, 0, classBytes.length);
      Method m = cl.getDeclaredMethod("beachheadLookup");
      return (MethodHandles.Lookup)m.invoke(null);
    } catch (Exception e) {
      throw new Error("failed to create beachhead class", e);
    }
  }

  // Only called on a class loader that is private to the compiler.
  // Used to define a compilation helper class that does not
  // contribute to the application's bytecode.
  public Class ephemeralClass(byte[] classBytes) {
    try {
      return defineClass(null, classBytes, 0, classBytes.length);
    } catch (Exception e) {
      throw new Error("failed to create ephemeral class", e);
    }
  }
}
