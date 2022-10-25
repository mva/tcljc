// Copyright (c) Michael van Acken. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (https://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file epl-v20.html at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.
package tinyclj.lang;

@SuppressWarnings("serial")
public class ArityException extends IllegalArgumentException {
  final public int actual;

  public ArityException(int actual) {
    this(actual, null);
  }
  
  public ArityException(int actual, Throwable cause) {
    super("Wrong number of args (" + actual + ") passed to fn", cause);
    this.actual = actual;
  }
}
