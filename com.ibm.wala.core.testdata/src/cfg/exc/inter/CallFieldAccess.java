package cfg.exc.inter;

import cfg.exc.intra.B;
import cfg.exc.intra.FieldAccess;
import cfg.exc.intra.FieldAccessDynamic;

public class CallFieldAccess {
  static boolean unknown;
  public static void main(String[] args) {
    unknown = (args.length == 0);
    callIfException();
    callIfNoException();
    callDynamicIfException();
    callDynamicIfNoException();
    callIf2Exception();
    callIf2NoException();
    callDynamicIf2Exception();
    callDynamicIf2NoException();
    callGetException();
    callDynamicGetException();
  }
  
  static B callIfException() {
    return FieldAccess.testIf(unknown, new B(), null);
  }
  static B callIfNoException() {
    return FieldAccess.testIf(unknown, new B(), new B());
  }
  
  @SuppressWarnings("null")
  static B callDynamicIfException() {
    final FieldAccessDynamic fad = unknown ? null : new FieldAccessDynamic();
    return fad.testIf(unknown, new B(), null);
  }
  static B callDynamicIfNoException() {
    final FieldAccessDynamic fad  = new FieldAccessDynamic();
    return fad.testIf(unknown, new B(), new B());
  }
  
  static B callIf2Exception() {
    return FieldAccess.testIf2(unknown, null, null);
  }
  static B callIf2NoException() {
    return FieldAccess.testIf2(unknown, new B(), null);
  }
  
  @SuppressWarnings("null")
  static B callDynamicIf2Exception() {
    final FieldAccessDynamic fad = unknown ? null : new FieldAccessDynamic();
    return fad.testIf2(unknown, null, null);
  }
  static B callDynamicIf2NoException() {
    final FieldAccessDynamic fad = new FieldAccessDynamic();
    return fad.testIf2(unknown, new B(), null);
  }
  
  static B callGetException() {
    B b = new B();
    return FieldAccess.testGet(unknown, b);
  }
  
  @SuppressWarnings("null")
  static B callDynamicGetException() {
    final FieldAccessDynamic fad = unknown ? null : new FieldAccessDynamic();
    B b = new B();
    return fad.testGet(unknown, b);
  }

}
