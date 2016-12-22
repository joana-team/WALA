package inner;


public class EnclosingMethods {
  static class Outer {
    Object z = new Inner () {
      @SuppressWarnings("unused")
      void lol(Object a, Object b) {
        Object x = a;
        Object y = b;
        
        x.toString();
        y.toString();
      }
    };
    
    static class Inner {
      static void foo(Object a, Object b) {
        Object x = a;
        Object y = b;
        
        Object z = new Cloneable() {
          @SuppressWarnings("unused")
          void bar(Object a, Object b) {
            Object x = a;
            Object y = b;
            
            x.toString();
            y.toString();
          }
        };

        x.toString();
        y.toString();
        
        z.toString();
      }
    }
  }
}
