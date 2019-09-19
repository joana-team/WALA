package com.ibm.wala.ipa.callgraph;

import com.ibm.wala.analysis.reflection.java7.MethodHandles;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.callgraph.impl.AbstractRootMethod;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrikeCT.ClassConstants;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

import java.util.*;

/**
 * Allows to convert an uninitialized argument access to an access to an uninitialized field
 */
public class UninitializedFieldClass extends SyntheticClass {

  private final IMethod constructor;
  private final Map<TypeReference, IField> fieldForType;
  private final Map<String, TypeReference> typeForName;
  private final AnalysisOptions options;

  private UninitializedFieldClass(TypeReference t, IClassHierarchy cha, AnalysisOptions options) {
    super(t, cha);
    this.options = options;
    fieldForType = new HashMap<>();
    typeForName = new HashMap<>();
    constructor = createConstructor(t);
  }

  private IField createField(String name, TypeReference type){
    return new FieldImpl(this, FieldReference.findOrCreate(getReference(), Atom.findOrCreateUnicodeAtom(name), type),
        ClassConstants.ACC_PUBLIC | ClassConstants.ACC_STATIC, Collections.emptyList());
  }

  /**
   * Returns an uninitialized field for a given type, creates it if necessary
   */
  public IField getField(TypeReference typeReference){
    return fieldForType.computeIfAbsent(typeReference, t -> {
      String name = typeReference.getName().toString().replaceAll("[^A-Z]", "") + fieldForType.size();
      typeForName.put(name, typeReference);
      return createField(name, typeReference);
    });
  }

  private IMethod createConstructor(TypeReference t){
    AbstractRootMethod constructor =
        new AbstractRootMethod(MethodReference.findOrCreate(t, "<init>", "(" + t.getName().toString() + ")V"), this,
          this.getClassHierarchy(), options, new IAnalysisCacheView(){

      @Override public void invalidate(IMethod method, Context C) {
      }

      @Override public IRFactory<IMethod> getIRFactory() {
        return new DefaultIRFactory();
      }

      @Override public IR getIR(IMethod method) {
        return getIR(method, new MethodHandles.MethodContext(Everywhere.EVERYWHERE, method.getReference()));
      }

      @Override public DefUse getDefUse(IR ir) {
        return new SSACache(getIRFactory(), new AuxiliaryCache(), new AuxiliaryCache()).findOrCreateDU(ir, Everywhere.EVERYWHERE);
      }

      @Override public IR getIR(IMethod method, Context context) {
        return getIRFactory().makeIR(method, context, options.getSSAOptions());
      }

      @Override public void clear() {
      }

    }){
      {
        this.statements.add(insts.ReturnInstruction(statements.size()));
      }

      @Override public boolean isStatic() {
        return false;
      }
    };
    return constructor;
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
    return getClassHierarchy().getRootClass();
  }

  @Override public Collection<? extends IClass> getDirectInterfaces() {
    return Collections.emptySet();
  }

  @Override public Collection<IClass> getAllImplementedInterfaces() {
    return Collections.emptySet();
  }

  @Override public IMethod getMethod(Selector selector) {
    return selector.equals(constructor.getSelector()) ? constructor : null;
  }

  @Override public IField getField(Atom name) {
    return fieldForType.get(typeForName.get(name.toString()));
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
    return fieldForType.values();
  }

  @Override public Collection<IField> getAllFields() {
    return fieldForType.values();
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

  public static UninitializedFieldClass createAndAdd(IClassLoader classLoader, IClassHierarchy cha, AnalysisOptions options) {
    UninitializedFieldHelperOptions fieldOptions = options.getFieldHelperOptions();
    if (!fieldOptions.hasName()){
      fieldOptions.setName(generateHelperClassName(cha));
    }
    UninitializedFieldClass klass = new UninitializedFieldClass(
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

  public AnalysisOptions getOptions() {
    return options;
  }
}
