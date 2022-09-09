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
