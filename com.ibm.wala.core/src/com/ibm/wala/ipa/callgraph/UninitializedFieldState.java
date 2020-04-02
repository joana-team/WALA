package com.ibm.wala.ipa.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.NewTypedSiteReference;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PropagationSystem;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder.assignOperator;

public class UninitializedFieldState {

  private final UninitializedFieldHelperOptions options;
  /**
   * Might be null
   */
  private Map<PointerKey, TypeReference> recorded = new HashMap<>();
  private final SubTypeHierarchy hierarchy;
  private final Map<TypeReference, Set<PointerKey>> keysPerType;
  private final Map<TypeReference, Set<TypeReference>> subReferences;
  private final Map<PointerKey, CGNode> cgNodeMap;

  /**
   * Keys with empty points to set. Only these pointer keys will be considered for assignments to generated pointer sets
   */
  private Set<PointerKey> keysWithEmptySet = new HashSet<>();

  public UninitializedFieldState(UninitializedFieldHelperOptions options, SubTypeHierarchy hierarchy) {
    this(options, hierarchy, new HashMap<>());
  }

  private UninitializedFieldState(UninitializedFieldHelperOptions options, SubTypeHierarchy hierarchy,
      Map<TypeReference, Set<PointerKey>> keysPerType) {
    this.options = options;
    this.keysPerType = keysPerType;
    this.subReferences = new HashMap<>();
    this.hierarchy = hierarchy;
    this.cgNodeMap = new HashMap<>();
  }

  public Set<PointerKey> getCreatedKeys(){
    return keysPerType.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
  }

  public Set<PointerKey> getKeysWithEmptySet() {
    return keysWithEmptySet;
  }

  public void record(TypeReference declaringType, TypeReference type, PointerKey key, CGNode node){
    if (match(declaringType, type)){
      System.err.println(declaringType + "#" + type);
      recorded.put(key, type);
      cgNodeMap.put(key, node);
    }
  }

  public void record(IField field, PointerKey key, CGNode node){
    record(field.getDeclaringClass().getReference(), field.getFieldTypeReference(), key, node);
  }

  public void record(FieldReference field, PointerKey key, CGNode node){
    record(field.getDeclaringClass(), field.getFieldType(), key, node);
  }

  public boolean shouldAssign(PointerKey key){
    return keysWithEmptySet.contains(key);
  }

  public void assign(SSAPropagationCallGraphBuilder builder, PointerKey key){
    assign(builder, key, recorded.get(key));
  }

  /**
   * For not recorded keys
   */
  public void assign(SSAPropagationCallGraphBuilder builder, PointerKey key, TypeReference type){
    assert shouldAssign(key);
    for (TypeReference t : getSuitableTypeReferences(type)) {
      for (PointerKey pointerKey : create(builder, t)) {
        builder.getPropagationSystem().newConstraint(key, assignOperator, pointerKey);
      }
    }
  }

  private Set<PointerKey> create(SSAPropagationCallGraphBuilder builder, TypeReference ref){
    System.err.println("Created for " + ref);
    PropagationSystem system = builder.getPropagationSystem();
    return keysPerType.computeIfAbsent(ref, rr -> IntStream.range(0, options.getCount()).mapToObj(r -> {
      NewSiteReference site = new NewTypedSiteReference(1, ref);
      InstanceKey iKey = builder.getInstanceKeyForAllocation(options.getRoot(), site);
      if (iKey == null) { // abstract
        return null;
      }
      PointerKey def = builder.getPointerKeyForLocal(options.getRoot(), Integer.MAX_VALUE - r);
      IClass klass = iKey.getConcreteType();
      if (klass == null){
        return null;
      }
      system.newConstraint(def, iKey);

      SSAPropagationCallGraphBuilder.ConstraintVisitor constraintVisitor = new SSAPropagationCallGraphBuilder.ConstraintVisitor(
          builder, options.getRoot());
      constraintVisitor.visitNew(new SSANewInstruction(0, Integer.MAX_VALUE, site) {
        @Override public SSAInstruction copyForSSA(SSAInstructionFactory insts, int[] defs, int[] uses) {
          return super.copyForSSA(insts, defs, uses);
        }
      });
      return def;
    }).filter(Objects::nonNull).collect(Collectors.toSet()));
  }

  /**
   * Cached
   */
  private Set<TypeReference> getSuitableTypeReferences(TypeReference t){
    return subReferences.computeIfAbsent(t, tr -> {
      Set<TypeReference> base = new HashSet<>();
      Consumer<TypeReference> add = trr -> {
        if (options.matchField(trr) && !trr.isPrimitiveType() && hierarchy.cha.lookupClass(trr) != null){
          base.add(trr);
        }
      };
      add.accept(t);
      if (t.isArrayType()){
        TypeReference cur = t;
        while (cur.isArrayType()){
          cur = cur.getArrayElementType();
          if (!cur.isPrimitiveType()) {
            add.accept(cur);
          }
        }
      }
      return Stream.concat(base.stream(), base.stream().map(hierarchy::getSubTypes).flatMap(Set::stream)).collect(Collectors.toSet());
    });
  }

  boolean match(TypeReference declaringType, TypeReference type){
    return options.matchField(declaringType, type);
  }

  public UninitializedFieldState createNew() {
    return new UninitializedFieldState(options, hierarchy, keysPerType);
  }

  /**
   * Sets the keysWithEmptySet
   */
  public void filterRecorded(Predicate<PointerKey> pred){
    this.keysWithEmptySet = this.recorded.keySet().stream().filter(pred).collect(Collectors.toSet());
  }

  public Set<CGNode> getCGNodesWithReplacements() {
    return keysWithEmptySet.stream().map(cgNodeMap::get).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  public static UninitializedFieldState createDummy(){
    return new UninitializedFieldState(UninitializedFieldHelperOptions.createEmpty(), null){
      @Override public Set<PointerKey> getCreatedKeys() {
        return Collections.emptySet();
      }

      @Override public Set<PointerKey> getKeysWithEmptySet() {
        return Collections.emptySet();
      }

      @Override public void record(TypeReference declaringType, TypeReference type, PointerKey key, CGNode node) {
      }

      @Override public void record(IField field, PointerKey key, CGNode node) {
      }

      @Override public void record(FieldReference field, PointerKey key, CGNode node) {
      }

      @Override public boolean shouldAssign(PointerKey key) {
        return false;
      }

      @Override public void assign(SSAPropagationCallGraphBuilder builder, PointerKey key) {
      }

      @Override public void assign(SSAPropagationCallGraphBuilder builder, PointerKey key, TypeReference type) {
      }

      @Override boolean match(TypeReference declaringType, TypeReference type) {
        return false;
      }

      @Override public UninitializedFieldState createNew() {
        return createDummy();
      }

      @Override public void filterRecorded(Predicate<PointerKey> pred) {
      }

      @Override public Set<CGNode> getCGNodesWithReplacements() {
        return Collections.emptySet();
      }

    };
  }
}
