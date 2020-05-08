/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package com.ibm.wala.ipa.callgraph;

import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.StandardSolver;
import com.ibm.wala.ipa.cha.IClassHierarchy;

import java.util.Collections;
import java.util.List;

/**
 * Works currently only when using the {@link SSAPropagationCallGraphBuilder} in combination with the {@link StandardSolver}
 *
 * Assumes that there are no annotated fields.
 */
public class InterfaceImplementationOptions {

  /**
   * Mode of pointer key generation for an object of a specific type
   * required in the body of an interface method implementation.
   *
   * Progresses in conservativity
   */
  public static enum Mode {
    /**
     * Generate a new key every time one is requested
     */
    PER_KEY,
    /**
     * Create a new key for each type and each instance
     */
    PER_INSTANCE,
    /**
     * Create a new key each type and all instances together
     */
    PER_TYPE,
    /**
     * Just use the unitialized field helper
     */
    USE_UNINITIALIZED_FIELD_HELPER
  }

  /**
   * to prevent direct alias detection
   */
  private final int count = 2;

  /**
   * Lpkg/pkg2/name
   */
  private final List<String> interfacesToImplement;

  private final InterfaceImplementationClass.FunctionBodyGenerator generator;

  private final Mode mode;

  private final boolean implementInSameClass;

  private InterfaceImplementationState state;

  public InterfaceImplementationOptions(List<String> interfacesToImplement,
      InterfaceImplementationClass.FunctionBodyGenerator generator){
    this(interfacesToImplement, generator, Mode.PER_KEY, false);
  }

  public InterfaceImplementationOptions(List<String> interfacesToImplement,
      InterfaceImplementationClass.FunctionBodyGenerator generator, Mode mode, boolean implementInSameClass) {
    this.interfacesToImplement = interfacesToImplement;
    this.generator = generator;
    this.mode = mode;
    this.implementInSameClass = implementInSameClass;
  }

  public boolean isEmpty(){
    return interfacesToImplement.isEmpty();
  }

  public int getCount() {
    return count;
  }

  public List<String> getInterfacesToImplement() {
    return interfacesToImplement;
  }

  public Mode getMode() {
    return mode;
  }

  public static InterfaceImplementationOptions createEmpty() {
    return new InterfaceImplementationOptions(Collections.emptyList(), (a, b) -> {}, Mode.PER_INSTANCE, true);
  }

  public boolean isAllImplementedInSameClass() {
    return implementInSameClass;
  }

  public InterfaceImplementationClass.FunctionBodyGenerator getGenerator() {
    return generator;
  }

  public InterfaceImplementationState getState() {
    return state;
  }

  public void setState(InterfaceImplementationState state) {
    this.state = state;
  }

  public void createAndAddMultiple(IClassLoader classLoader, IClassHierarchy cha, AnalysisOptions options){
    this.state = InterfaceImplementationClass.createAndAddMultiple(classLoader, cha, options);
  }
}