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
