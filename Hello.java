import jdk.classfile.CodeModel;
import jdk.classfile.Label;

class Hello {
  static class ClassA {
    static final String staticFinalField;

    static {
      staticFinalField = "foo";
    }
  }

  static class ClassB extends ClassA {
  }
  
  public static int l2b(CodeModel xm, Label label) {
    if (
        ClassA.staticFinalField
        ==
        "foobar"
        ) {
    return
      xm
      .labelToBci(
                  label
                  );
    }
    else
      {
        return -1;
      }
  }

  public static void print () {
    System.out.println(ClassB.staticFinalField);
  }
  
  public static void main(String[] args) {
    print();
  }
}
