## Tiny Clojure Compiler

Impetus for this project was the wish for easier use of
[LWJGL](https://github.com/LWJGL/lwjgl3) classes, and to have a test
bed for features that [Project
Valhalla](https://openjdk.org/projects/valhalla/) may bring to the
JVM.  With Clojure's runtime classes and the JVM doing all the heavy
lifting, building a suitable compiler would surely be a limited
effort.  So I thought a few weeks after the release of JDK 9 and
before doing multiple iterations of the "tiny" compiler.

### How to use the thing

See [hello-tcljc](https://github.com/mva/hello-tcljc) for
prerequisites and basic usage.

### Kind of Clojure, but more static

Starting with a rather static view on Clojure source code, `tcljc`
produces concise and predictable byte code.  From this it recovers some
but not all of Clojure's more dynamic aspects.  This is not quite as
scary as it sounds, because the JVM is very dynamic machine on its
own.  The list of the good, the bad, and the ugly begins like this:

* Type hints act as type assertions.  They enforce the given type
  (instead of suggesting it), and they take effect at the point of
  declaration (instead of the point of use).  All primitive types are
  supported and they are supported everywhere.  A very fresh change is
  the array type notation recently [green
  lit](https://clojure.org/news/2023/10/06/deref#:~:text=Our%20plan%20going%20forward%20is%20to%20support%20a%20new%20array%20class%20syntax%20which%20is%20a%20symbol%20of%20the%20class%20with%20a%20*%20suffix.)
  by Rich Hickey.  I decided to drop the old notations.  That is,
  `^int*` replaces both `^ints` and ```^"[I"```, and things like
  ```(new ints** 3 5)``` and ```(instance? Object* x)``` work.

* Arithmetic resembles that of Java and is *not* a wrapper for
  `clojure.lang.Numbers`.  For example, binary `+` is always compiled
  to one of the `[ILFD]ADD` instructions, depending on the types of
  its primitive arguments.

* There is only auto-boxing and -unboxing for assignment situations,
  like passing an argument to a parameter or returning the value of a
  function arity.  For arithmetics conversion from reference to
  primitive view must be done manually, for example by writing `^int
  foo` (taking an `Integer` or one of its super types).

* Functions are implemented via [method
  handles](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/MethodHandle.html).
  `clojure.lang.IFn` still exists but is only a thin wrapper
  supporting the other `clojure.lang.*` classes.  Function compilation
  is often able to map individual arities to static methods, without
  any need to have a dedicated class holding the arity methods and
  representing the function itself.

* There is no runtime reflection.  If the compiler cannot resolve a
  method or field, then it will complain loudly.  Kind of
  `*warn-on-reflection*`, but always on.

* The compiler's symbol tables (globals, namespace aliases, imports)
  are not available during runtime.  As a consequence, there is no
  REPL, no `resolve`, and only a limited compile-time `macroexpand`.

* [...]

### What the future may bring

The compiler is a vehicle for experiments, and it follows both the
development branch of [OpenJDK](https://github.com/openjdk/jdk) and to
a lesser degree upstream
[Clojure](https://github.com/clojure/clojure).

The JVM is rapidly adding features that are valuable even for non-Java
language implementations.  Over the past years the compiler made use
of [Dynamic Class-File Constants](https://openjdk.java.net/jeps/309),
[JVM Constants API](https://openjdk.java.net/jeps/334), [Virtual
Threads](https://openjdk.org/jeps/444), and [Class-File
API](https://openjdk.org/jeps/457) in their preview stage and
sometimes even before that.  I do not expect the pace of interesting
stuff to slow down.

The biggest item on the horizon is Valhalla.  Once it enters preview
(maybe in the course of '24?), I intend make the additional type
decorations available on the language level.  Both the Class-File API
and the new array type syntax may help with the implementation.

The Clojure pipeline has also interesting things to offer.  Some of
this brings immediate benefits for `tcljc`, like the change to
`LazySeq` and `Delay` that was good for a compiler bootstrap speedup
of 1.05.  Other plans are more [far
reaching](https://clojure.org/news/2023/09/08/deref) and touch on
topics where `tcljc` already has an opinion on.  The goal is always to
close gaps between `tcljc` and Clojure proper as far as possible,
given the fact that one comes from the direction of "more static" and
the other from "more dynamic".  I'm not sure what will turn out to be
a good path here.

Whatever the future brings: I intend to have fun.  I hope you will
have fun as well!
