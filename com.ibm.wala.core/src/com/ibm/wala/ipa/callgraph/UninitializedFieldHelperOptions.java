/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package com.ibm.wala.ipa.callgraph;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.StandardSolver;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.strings.Atom;

import java.util.*;
import java.util.stream.Collectors;

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
  private final String typeRegexp;

  /**
   * Might be null if no helper class is used
   */
  private UninitializedFieldHelperClass helperClass;

  /**
   * Class loader scopes that are iterated for collecting the types to create fields for
   */
  public final Set<Atom> scopesForCollection;

  public UninitializedFieldHelperOptions(String typeRegexp) {
    this(null, typeRegexp, Collections.singleton(AnalysisScope.APPLICATION));
  }

  public UninitializedFieldHelperOptions(String name, String typeRegexp, Set<Atom> scopesForCollection) {
    this.name = name;
    this.typeRegexp = typeRegexp;
    this.scopesForCollection = Collections.unmodifiableSet(scopesForCollection);
  }

  public UninitializedFieldHelperOptions(){
    this("", "", Collections.emptySet());
  }

  public boolean isEmpty(){
    return typeRegexp.isEmpty();
  }

  public boolean hasFieldForType(TypeReference type) {
    return helperClass != null && helperClass.hasField(type);
  }

  public IField getFieldForType(TypeReference type){
    return helperClass.getField(type);
  }

  public UninitializedFieldHelperClass getHelperClass() {
    return helperClass;
  }

  public void setHelperClass(UninitializedFieldHelperClass helperClass) {
    Assertions.productionAssertion(helperClass != null || isEmpty());
    this.helperClass = helperClass;
  }

  public boolean matchType(TypeReference typeReference){
    if (typeReference.isArrayType()){
      return matchType(typeReference.getInnermostElementType());
    }
    return typeReference.getName().toString().matches(typeRegexp);
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

  public static class UninitializedFieldState {
    /**
     * Might be null
     */
    final UninitializedFieldHelperClass fieldHelperClass;
    private final Map<PointerKey, TypeReference> recorded;
    private final Map<TypeReference, Set<PointerKey>> recordedHelperClassFields;
    private Set<PointerKey> keysToReplace;
    private final Map<PointerKey, CGNode> cgNodePerKey;
    private Set<CGNode> cgNodesWithReplacements;

    public UninitializedFieldState(UninitializedFieldHelperClass fieldHelperClass) {
      this.fieldHelperClass = fieldHelperClass;
      this.recorded = new HashMap<>();
      this.keysToReplace = new HashSet<>();
      this.recordedHelperClassFields = new HashMap<>();
      this.cgNodePerKey = new HashMap<>();
    }

    public void recordFieldAccess(FieldReference ref, PointerKey key, CGNode callNode){
      recordValue(ref.getFieldType(), key, callNode);
    }

    private void recordValue(TypeReference type, PointerKey key, CGNode callNode){
      if (fieldHelperClass != null && fieldHelperClass.hasField(type)) {
        recorded.put(key, type);
        if (callNode != null){
          cgNodePerKey.put(key, callNode);
        }
      }
    }

    public void recordHelperClassFieldWrite(FieldReference ref, PointerKey key){
      if (fieldHelperClass != null) {
        assert fieldHelperClass.hasField(ref.getFieldType()) && fieldHelperClass.getField(ref.getFieldType()).getName().equals(ref.getName());
        recordedHelperClassFields.computeIfAbsent(ref.getFieldType(), r -> new HashSet<>()).add(key);
      }
    }

    public Set<PointerKey> getRecordedKeys(){
      return recorded.keySet();
    }

    /**
     * Also sets calling cg nodes
     */
    public void setKeysToReplace(Set<PointerKey> keysToReplace){
      this.keysToReplace = keysToReplace;
      this.cgNodesWithReplacements = keysToReplace.stream().map(cgNodePerKey::get).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public Set<CGNode> getCgNodesWithReplacements() {
      return Collections.unmodifiableSet(cgNodesWithReplacements);
    }

    public boolean shouldReplace(PointerKey key){
      return keysToReplace.contains(key);
    }

    public Set<PointerKey> getReplacements(PointerKey key) {
      assert shouldReplace(key);
      return recordedHelperClassFields.get(recorded.get(key));
    }

    @Override public String toString() {
      return "UninitializedFieldState{" + "fieldHelperClass=" + fieldHelperClass + ", recorded=" + recorded
          + ", recordedHelperClassFields=" + recordedHelperClassFields + ", keysToReplace=" + keysToReplace + '}';
    }
  }
}