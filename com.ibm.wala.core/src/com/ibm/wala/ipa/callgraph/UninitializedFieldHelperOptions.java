/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package com.ibm.wala.ipa.callgraph;

import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.StandardSolver;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

import static com.ibm.wala.ipa.callgraph.AnalysisScope.APPLICATION;

/**
 * Options for generating a class containing fields for all project types that can be used whenever a field of a specific
 * type is not initialized (because it is initialized in code not called by the entry point)
 *
 * Works currently only when using the {@link SSAPropagationCallGraphBuilder} in combination with the {@link StandardSolver}
 *
 * Assumes that there are no annotated fields.
 */
public class UninitializedFieldHelperOptions {

  /**
   * Match fields
   */
  public static interface FieldTypeMatcher {
    default boolean matchScope(Atom analysisScope){
      return analysisScope.equals(APPLICATION);
    }

    default boolean matchFieldTypeScope(Atom analysisScope){
      return matchScope(analysisScope);
    }

    default boolean matchOwnerTypeScope(Atom analysisScope){
      return matchScope(analysisScope);
    }

    /**
     *
     * @param typeReference is not an array type
     * @return
     */
    boolean matchType(TypeReference typeReference);

    /**
     *
     * @param typeReference is not an array type
     * @return
     */
    default boolean matchFieldType(TypeReference typeReference){
      return matchType(typeReference);
    }

    /**
     *
     * @param typeReference is not an array type
     * @return
     */
    default boolean matchOwnerType(TypeReference typeReference){
      return matchType(typeReference);
    }

    default boolean match(TypeReference owner, TypeReference field){
      return matchOwnerTypeScope(owner.getClassLoader().getName()) && matchFieldTypeScope(field.getClassLoader().getName()) &&
          matchOwnerType(owner.getInnermostElementType()) && matchFieldType(field.getInnermostElementType());
    }
  }

  private String name;
  private final FieldTypeMatcher matcher;

  /**
   * Might be null
   */
  private CGNode root;

  /**
   * to prevent direct alias detection
   */
  private final int count = 2;

  public UninitializedFieldHelperOptions(FieldTypeMatcher matcher) {
    this(null, matcher);
  }

  public UninitializedFieldHelperOptions(String name, FieldTypeMatcher matcher) {
    this.name = name;
    this.matcher = matcher;
  }

  public boolean isEmpty(){
    return false;
  }

  public boolean matchField(TypeReference owner, TypeReference typeReference){
    return matcher.match(owner, typeReference);
  }

  public boolean matchField(TypeReference typeReference){
    return matcher.matchFieldType(typeReference.getInnermostElementType()) && matcher.matchScope(typeReference.getClassLoader().getName());
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean hasName(){
    return name != null && !name.equals("");
  }

  public CGNode getRoot() {
    return root;
  }

  public void setRoot(CGNode root) {
    this.root = root;
  }

  public int getCount() {
    return count;
  }

  public static UninitializedFieldHelperOptions createEmpty(){
    return new UninitializedFieldHelperOptions(t -> false){
      @Override public boolean isEmpty() {
        return true;
      }
    };
  }
}