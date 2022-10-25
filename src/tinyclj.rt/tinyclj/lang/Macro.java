// Copyright (c) Michael van Acken. All rights reserved.
// The use and distribution terms for this software are covered by the
// Eclipse Public License 2.0 (https://www.eclipse.org/legal/epl-v20.html)
// which can be found in the file epl-v20.html at the root of this distribution.
// By using this software in any fashion, you are agreeing to be bound by
// the terms of this license.
// You must not remove this notice, or any other, from this software.
package tinyclj.lang;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

/* FIXME... as soon as we have lazy static fields, and methods are not
 * longer used to wrap an LDC, drop METHOD from target */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value={FIELD, METHOD})
public @interface Macro {
  String macroValue() default ""; // null is not a valid default value
}
