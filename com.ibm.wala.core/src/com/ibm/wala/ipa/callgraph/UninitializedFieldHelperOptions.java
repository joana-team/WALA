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

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Options for generating a class containing fields for all project types that can be used whenever a field of a specific
 * type is not initialized (because it is initialized in code not called by the entry point)
 *
 * Currently only works when using the {@link SSAPropagationCallGraphBuilder} in combination with the {@link StandardSolver}
 */
public class UninitializedFieldHelperOptions {

  private String name;
  /**
   * Matches byte code names of types
   */
  private final Pattern typeRegexp;

  /**
   * Might be null
   */
  private CGNode root;

  private final int count = 2;

  /**
   * Class loader scopes that are iterated for collecting the types to create fields for
   */
  public final Set<Atom> scopesForCollection;

  public UninitializedFieldHelperOptions(String typeRegexp) {
    this(null, typeRegexp, Collections.singleton(AnalysisScope.APPLICATION));
  }

  public UninitializedFieldHelperOptions(String name, String typeRegexp, Set<Atom> scopesForCollection) {
    this.name = name;
    this.typeRegexp = Pattern.compile(typeRegexp);
    this.scopesForCollection = Collections.unmodifiableSet(scopesForCollection);
  }

  public UninitializedFieldHelperOptions(){
    this("", "", Collections.emptySet());
  }

  public boolean isEmpty(){
    return typeRegexp.pattern().isEmpty();
  }

  public boolean matchType(TypeReference typeReference){
    if (typeReference.isArrayType()){
      return matchType(typeReference.getInnermostElementType());
    }
    return scopesForCollection.contains(typeReference.getClassLoader().getName()) &&
        typeRegexp.matcher(typeReference.getName().toString()).matches();
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
}