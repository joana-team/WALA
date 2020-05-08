package com.ibm.wala.ipa.callgraph;

import com.ibm.wala.types.TypeReference;

import java.util.Map;

/**
 * Maps interfaces to their custom implementations
 */
public class InterfaceImplementationState {

  private final Map<TypeReference, InterfaceImplementationClass> implementations;

  public InterfaceImplementationState(Map<TypeReference, InterfaceImplementationClass> implementations) {
    this.implementations = implementations;
  }

  public boolean isEmpty(){
    return implementations.isEmpty();
  }

  public boolean hasImplementationFor(TypeReference type) {
    return implementations.containsKey(type);
  }

  public InterfaceImplementationClass getImplementation(TypeReference type){
    return implementations.get(type);
  }
}
