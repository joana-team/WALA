package com.ibm.wala.ipa.callgraph;

import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.callgraph.impl.AbstractRootMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrikeCT.ClassConstants;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.IRFactory;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Class that contains public static fields for types, helps to deal with the access to fields that are not set in the program
 * slice visible from the currently used entry point. Only really helpful for non-full-program analyses.
 *
 * It has fields for every type used as a field type or in a method signature (using class and type have to match the regular
 * expression set in the options with their byte code names). It has also fields for all element types of array types
 * (e.g. for A[][] their will be fields with the following types: A[][], A[], A).
 */
public class UninitializedFieldHelperClass extends SyntheticClass {

  private final Map<String, IField> fieldNameToField = new HashMap<>();
  private final Map<TypeReference, String> typeRefToFieldName = new HashMap<>();

  public final AnalysisOptions options;

  private final IMethod constructor;

  private final SubTypeHierarchy hierarchy;

  /**
   * @param T   type reference describing this class
   * @param cha
   * @param options
   */
  private UninitializedFieldHelperClass(TypeReference T, IClassHierarchy cha, AnalysisOptions options) {
    super(T, cha);
    this.hierarchy = new SubTypeHierarchy(cha);
    this.options = options;
    initFields(T);
    this.constructor = createConstructor(T);
  }
  private void initFields(TypeReference t){
    collectTypeReferences(getClassHierarchy(), options.getFieldHelperOptions())
        .forEach(this::createField);
  }

  private static Set<TypeReference> collectTypeReferences(IClassHierarchy cha, UninitializedFieldHelperOptions options){
    Set<TypeReference> ret = new HashSet<>();
    Consumer<TypeReference> add = t -> {
      if (options.matchType(t) && !t.isPrimitiveType() && cha.lookupClass(t) != null){
        ret.add(t);
      }
    };
    for (IClass iClass : cha) {
      if (options.scopesForCollection.contains(iClass.getReference().getClassLoader().getName()) && options.matchType(iClass.getReference())){
        iClass.getAllFields().stream().map(IField::getFieldTypeReference).forEach(add);
        iClass.getAllMethods().stream().flatMap(m ->
            Stream.concat(IntStream.range(0, m.getNumberOfParameters()).mapToObj(m::getParameterType), Stream.of(m.getReturnType())))
            .forEach(add);
        add.accept(iClass.getReference());
      }
    }
    Set<TypeReference> refsToAdd = new HashSet<>();
    for (TypeReference typeReference : ret) {
      if (typeReference.isArrayType()){
        TypeReference cur = typeReference;
        while (cur.isArrayType()){
          cur = cur.getArrayElementType();
          if (!cur.isPrimitiveType()) {
            refsToAdd.add(cur);
          }
        }
      }
    }
    ret.addAll(refsToAdd);
    Logger.getGlobal().info("Collected field helper types: " + ret.size() + " -> " + ret);
    return ret;
  }

  private IMethod createConstructor(TypeReference t){
    AbstractRootMethod constructor = new AbstractRootMethod(MethodReference.findOrCreate(t, "init", "()V"), this,
        this.getClassHierarchy(), options, new IAnalysisCacheView(){

      @Override public void invalidate(IMethod method, Context C) {

      }

      @Override public IRFactory<IMethod> getIRFactory() {
        return null;
      }

      @Override public IR getIR(IMethod method) {
        return null;
      }

      @Override public DefUse getDefUse(IR ir) {
        return null;
      }

      @Override public IR getIR(IMethod method, Context context) {
        return null;
      }

      @Override public void clear() {
      }

    }){
      {
        // create all fields
        Map<TypeReference, Integer> localsPerRef = new HashMap<>();
        typeRefToFieldName.forEach((ref, name) -> {
          int tmpVar = createForTypeReference(ref, this);
          localsPerRef.put(ref, tmpVar);
          //this.statements.add(insts.PutInstruction(statements.size(), tmpVar, fieldNameToField.get(name).getReference()));
        });
        // assign based on class hierarchies
        typeRefToFieldName.forEach((ref, name) -> {
          List<Integer> values = getSuitableTypeReferences(ref).stream().filter(r -> hierarchy.isSubClass(ref, r) || ref.equals(r)).map(
              localsPerRef::get).collect(Collectors.toList());
          int[] params = new int[values.size()];
          IntStream.range(0, params.length).forEach(i -> params[i] = values.get(i));
          int local = addLocal();
          this.statements.add(insts.PhiInstruction(statements.size(), local, params));
          localsPerRef.put(ref, local);
          this.statements.add(insts.PutInstruction(statements.size(), local, fieldNameToField.get(name).getReference()));
        });
        // assign array elements, starting from the lowest dimensional arrays
        typeRefToFieldName.keySet().stream().filter(t -> t.isArrayType() && !t.getArrayElementType().isPrimitiveType()).sorted(Comparator.comparing(TypeReference::getDimensionality))
            .forEach(ref -> {
              int[] params = new int[2];
              int value = localsPerRef.get(ref.getArrayElementType());
              int local = addAllocation(ref).getDef();
              this.statements.add(insts.ArrayStoreInstruction(this.statements.size(), local, 0, value, ref));
              int phiLocal = addLocal();
              this.statements.add(insts.PhiInstruction(statements.size(), phiLocal, new int[]{localsPerRef.get(ref), local}));
              localsPerRef.put(ref, phiLocal);
              this.statements.add(insts.PutInstruction(statements.size(), local, fieldNameToField.get(typeRefToFieldName.get(ref)).getReference()));
        });
        this.statements.add(insts.ReturnInstruction(statements.size()));
        Logger.getGlobal().info("Generated field helper constructor statements: " + this.statements.size());
      }

      @Override public boolean isStatic() {
        return true;
      }

      /**
       * Returns the types that are sub types of the given and the given type itself
       * @return
       */
      private List<TypeReference> getSuitableTypeReferences(TypeReference ref){
        return typeRefToFieldName.keySet().stream().filter(r -> r.equals(ref) || hierarchy.isSubClass(ref, r)).collect(Collectors.toList());
      }
    };
    return constructor;
  }

  static class SubTypeHierarchy {

    static enum Relation {
      NONE, SUB, SUPER, EQ;

      Relation invert(){
        switch (this){
        case SUB:
          return SUPER;
        case SUPER:
          return SUB;
        default:
          return this;
        }
      }
    }

    private Map<TypeReference, Map<TypeReference, Relation>> relations = new HashMap<>();
    private final IClassHierarchy cha;

    SubTypeHierarchy(IClassHierarchy cha) {
      this.cha = cha;
    }

    boolean isSubClass(TypeReference base, TypeReference child){
      return getRelation(base, child) == Relation.SUB;
    }

    /**
     * Only for non primitive types
     */
    Relation getRelation(TypeReference base, TypeReference child){
      if (base.equals(child)){
        return Relation.EQ;
      }
      /*if (base.getDimensionality() != child.getDimensionality()){
        return Relation.NONE;
      }*/
      Optional<Relation> storedRel = getStoredRelationBothDir(base, child);
      Relation rel = Relation.NONE;
      if (!storedRel.isPresent()){
        IClass baseClass = cha.lookupClass(base);
        IClass childClass = cha.lookupClass(child);
        if (baseClass == null || childClass == null){
          return Relation.NONE;
        }
        if (childClass.getSuperclass() == null){
          rel = Relation.SUPER;
        } else if (baseClass.getSuperclass() == null){
          rel = Relation.SUB;
        } else if (baseClass.equals(childClass.getSuperclass()) || childClass.getAllImplementedInterfaces().contains(baseClass)){
          rel = Relation.SUB;
        } else if (baseClass.getSuperclass().equals(childClass) || baseClass.getAllImplementedInterfaces().contains(childClass)){
          rel = Relation.SUPER;
        } else {
          rel = getRelation(base, childClass.getSuperclass().getReference());
          for (IClass inter : childClass.getAllImplementedInterfaces()) {
            if (rel != Relation.NONE){
              break;
            }
            rel = getRelation(base, inter.getReference());
          }
          if (rel == Relation.SUPER){
            rel = Relation.NONE;
          }
        }
        relations.computeIfAbsent(base, t -> new HashMap<>()).put(child, rel);
      } else {
        rel = storedRel.get();
      }
      return rel;
    }

    private Optional<Relation> getStoredRelationBothDir(TypeReference base, TypeReference child){
      Optional<Relation> rel = getStoredRelation(base, child);
      if (!rel.isPresent()){
        rel = getStoredRelation(child, base).map(Relation::invert);
      }
      return rel;
    }

    private Optional<Relation> getStoredRelation(TypeReference base, TypeReference child){
      if (relations.containsKey(base)){
        Map<TypeReference, Relation> subDict = relations.get(base);
        return Optional.ofNullable(subDict.getOrDefault(child, null));
      }
      return Optional.empty();
    }
  }

  private static int createForTypeReference(TypeReference p, AbstractRootMethod m) {
    if (p.isPrimitiveType()) {
      return m.addLocal();
    } else {
      SSANewInstruction n = m.addAllocation(p);
      return (n == null) ? -1 : n.getDef();
    }
  }

  private void createField(TypeReference type){
    if (!typeRefToFieldName.containsKey(type)){
      String fieldName = "field" + fieldNameToField.size();
      IField field = new FieldImpl(this, FieldReference.findOrCreate(getReference(), Atom.findOrCreateUnicodeAtom(fieldName), type),
          ClassConstants.ACC_PUBLIC | ClassConstants.ACC_STATIC, Collections.emptyList());
      fieldNameToField.put(fieldName, field);
      typeRefToFieldName.put(type, fieldName);
    }
  }

  @Override public boolean isPublic() {
    return true;
  }

  @Override public boolean isPrivate() {
    return false;
  }

  @Override public int getModifiers() throws UnsupportedOperationException {
    return 0;
  }

  @Override public IClass getSuperclass() {
    return null;
  }

  @Override public Collection<? extends IClass> getDirectInterfaces() {
    return null;
  }

  @Override public Collection<IClass> getAllImplementedInterfaces() {
    return null;
  }

  @Override public IMethod getMethod(Selector selector) {
    return constructor;
  }

  @Override public IField getField(Atom name) {
    return fieldNameToField.get(name.toString());
  }

  public IField getField(TypeReference type){
    return fieldNameToField.get(typeRefToFieldName.get(type));
  }

  public boolean hasField(TypeReference type){
    return typeRefToFieldName.containsKey(type);
  }

  @Override public IMethod getClassInitializer() {
    return constructor;
  }

  @Override public Collection<IMethod> getDeclaredMethods() {
    return Collections.singleton(constructor);
  }

  @Override public Collection<IField> getAllInstanceFields() {
    return Collections.emptyList();
  }

  @Override public Collection<IField> getAllStaticFields() {
    return fieldNameToField.values();
  }

  @Override public Collection<IField> getAllFields() {
    return fieldNameToField.values();
  }

  @Override public Collection<IMethod> getAllMethods() {
    return getDeclaredMethods();
  }

  @Override public Collection<IField> getDeclaredInstanceFields() {
    return Collections.emptyList();
  }

  @Override public Collection<IField> getDeclaredStaticFields() {
    return getAllFields();
  }

  @Override public boolean isReferenceType() {
    return true;
  }

  public static UninitializedFieldHelperClass createAndAdd(IClassLoader classLoader, IClassHierarchy cha, AnalysisOptions options) {
    UninitializedFieldHelperOptions fieldOptions = options.getFieldHelperOptions();
    if (!fieldOptions.hasName()){
      fieldOptions.setName(generateHelperClassName(cha));
    }
    UninitializedFieldHelperClass klass = new UninitializedFieldHelperClass(
        TypeReference.findOrCreate(classLoader.getReference(),
            fieldOptions.getName()),
        cha, options);
    cha.addClass(klass);
    return klass;
  }

  private static String generateHelperClassName(IClassHierarchy cha){
    Set<String> packageNames = new HashSet<>();
    Iterator<IClass> iterator = cha.iterator();
    while (iterator.hasNext()) {
      IClass next =  iterator.next();
      Atom aPackage = next.getName().getPackage();
      if (aPackage != null) {
        packageNames.add(aPackage.toString());
      }
    }
    String suggestedPkgName = "fieldhelper";
    while (packageNames.contains(suggestedPkgName)){
      suggestedPkgName += ".fieldhelper";
    }
    return suggestedPkgName + ".FieldHelper";
  }

  /**
   * Adds an invocation of the initializer method to the passed root method
   */
  public void addInvocation(AbstractRootMethod root){
    root.addInvocation(new int[0], CallSiteReference.make(0, constructor.getReference(),
        IInvokeInstruction.Dispatch.STATIC));
  }
}
