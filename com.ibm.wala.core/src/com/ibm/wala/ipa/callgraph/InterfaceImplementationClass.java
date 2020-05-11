package com.ibm.wala.ipa.callgraph;

import com.ibm.wala.analysis.reflection.java7.MethodHandles;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.ipa.callgraph.impl.AbstractRootMethod;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.*;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrikeCT.ClassConstants;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.strings.Atom;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder.assignOperator;

/**
 * Allows to implement an interface with a dummy class
 *
 * Parameters in byte code start at index 1
 */
public class InterfaceImplementationClass extends SyntheticClass {

  @FunctionalInterface public interface FunctionBodyGenerator {
    /**
     * Generate the body of the method (must include a return statement)
     *
     * @param klass  klass that the method is implemented in
     * @param method method to add body statements to
     * @param origMethod implemented method
     */
    void generate(InterfaceImplementationClass klass, AbstractRootMethod method, IMethod origMethod);

    /**
     * Generates a generic return statement that returns the value of an unitialized instance
     * field for non-void functions
     */
    static void generateReturn(InterfaceImplementationClass klass, AbstractRootMethod method) {
      if (method.returnsVoid()) {
        method.addReturn();
      } else {
        method.addReturn(klass.addLoadForType(method, method.getReturnType()));
      }
    }

    FunctionBodyGenerator CONNECT_RETURN_WITH_PARAMS = (klass, method, origMethod) -> {
      if (!method.returnsVoid()) {
        int ret = klass.addLoadForType(method, method.getReturnType());
        IntStream.range(0, method.getNumberOfParameters()).forEach(i -> {
          TypeReference type = method.getParameterType(i);
          method.addDummyReturnCondition(type, i + 1, method.getReturnType(), ret);
        });
      }
      generateReturn(klass, method);
    };

    FunctionBodyGenerator DEFAULT = CONNECT_RETURN_WITH_PARAMS;

  }

  private final AnalysisOptions options;
  private final InterfaceImplementationOptions implOptions;
  private final List<String> implementedInterfaces;
  private final FunctionBodyGenerator generator;
  private final IMethod constructor;
  private final Map<Selector, IMethod> methods;
  private final Map<TypeReference, IField> fieldForType;
  private final Map<String, TypeReference> typeForName;
  private final UninitializedFieldState.AssignableTypeReferences assignableTypeReferences;

  private InterfaceImplementationClass(TypeReference t, IClassHierarchy cha, AnalysisOptions options,
      InterfaceImplementationOptions implOptions, List<String> implementedInterfaces, FunctionBodyGenerator generator) {
    super(t, cha);
    this.options = options;
    this.implOptions = implOptions;
    this.implementedInterfaces = implementedInterfaces;
    this.generator = generator;
    this.constructor = createConstructor(t);
    fieldForType = new HashMap<>();
    typeForName = new HashMap<>();
    this.methods = generateMethods(getMethodsToImplement(cha, implementedInterfaces));
    this.assignableTypeReferences = new UninitializedFieldState.AssignableTypeReferences(cha, tr -> true);
  }

  private static Set<IMethod> getMethodsToImplement(IClassHierarchy cha, List<String> implementedInterfaces) {
    return implementedInterfaces.stream().flatMap(i -> getMethodsToImplement(cha, i).stream()).collect(Collectors.toSet());
  }

  private static Set<IMethod> getMethodsToImplement(IClassHierarchy cha, String implementedInterface) {
    return new HashSet<>(cha.lookupClass(implementedInterface).getAllMethods());
  }

  private Map<Selector, IMethod> generateMethods(Set<IMethod> methods) {
    return methods.stream().map(this::generateMethod).collect(Collectors.toMap(IMethod::getSelector, m -> m));
  }

  private IMethod generateMethod(IMethod method) {
    return new AbstractRootMethod(MethodReference.findOrCreate(getReference(), method.getName(), method.getDescriptor()),
        InterfaceImplementationClass.this, getClassHierarchy(), options, new IAnalysisCacheView() {
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
    }) {
      {
        generator.generate(InterfaceImplementationClass.this, this, method);
      }

      @Override public boolean isStatic() {
        return false;
      }
    };
  }

  public IField getField(TypeReference typeReference) {
    return fieldForType.computeIfAbsent(typeReference, t -> {
      String name = typeReference.getName().toString().replaceAll("[^A-Z]", "") + fieldForType.size();
      typeForName.put(name, typeReference);
      return createField(name, typeReference);
    });
  }

  private IField createField(String name, TypeReference type) {
    return new FieldImpl(this, FieldReference.findOrCreate(getReference(), Atom.findOrCreateUnicodeAtom(name), type),
        ClassConstants.ACC_PUBLIC, Collections.emptyList());
  }

  private IMethod createConstructor(TypeReference t) {
    return new AbstractRootMethod(MethodReference.findOrCreate(t, "<init>", "()V"),
        InterfaceImplementationClass.this, InterfaceImplementationClass.this.getClassHierarchy(), options,
        new IAnalysisCacheView() {

          @Override public void invalidate(IMethod method, Context C) {
          }

          @Override public IRFactory<IMethod> getIRFactory() {
            return new DefaultIRFactory();
          }

          @Override public IR getIR(IMethod method) {
            return getIR(method, new MethodHandles.MethodContext(Everywhere.EVERYWHERE, method.getReference()));
          }

          @Override public DefUse getDefUse(IR ir) {
            return new SSACache(getIRFactory(), new AuxiliaryCache(), new AuxiliaryCache())
                .findOrCreateDU(ir, Everywhere.EVERYWHERE);
          }

          @Override public IR getIR(IMethod method, Context context) {
            return getIRFactory().makeIR(method, context, options.getSSAOptions());
          }

          @Override public void clear() {
          }

        }) {
      {
        this.statements.add(insts.ReturnInstruction(statements.size()));
      }

      @Override public boolean isStatic() {
        return false;
      }
    };
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
    return implementedInterfaces.stream().map(i -> getClassHierarchy().lookupClass(i)).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  @Override public Collection<IClass> getAllImplementedInterfaces() {
    return (Collection<IClass>) getDirectInterfaces();
  }

  @Override public IMethod getMethod(Selector selector) {
    return selector.equals(constructor.getSelector()) ? constructor : methods.getOrDefault(selector, null);
  }

  @Override public IField getField(Atom name) {
    return fieldForType.get(typeForName.get(name.toString()));
  }

  @Override public IMethod getClassInitializer() {
    return constructor;
  }

  @Override public Collection<IMethod> getDeclaredMethods() {
    return Stream.concat(Stream.of(constructor), methods.values().stream()).collect(Collectors.toList());
  }

  @Override public Collection<IField> getAllInstanceFields() {
    return fieldForType.values();
  }

  @Override public Collection<IField> getAllStaticFields() {
    return Collections.emptyList();
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

  private static InterfaceImplementationClass createAndAdd(IClassLoader classLoader, IClassHierarchy cha, AnalysisOptions options,
      InterfaceImplementationOptions implOptions, List<String> implementedInterfaces, FunctionBodyGenerator generator) {
    InterfaceImplementationClass klass = new InterfaceImplementationClass(
        TypeReference.findOrCreate(classLoader.getReference(), generateClassName(cha)), cha, options, implOptions,
        implementedInterfaces, generator);
    cha.addClass(klass);
    return klass;
  }

  public static InterfaceImplementationState createAndAddMultiple(IClassLoader classLoader, IClassHierarchy cha,
      AnalysisOptions options) {
    return createAndAddMultiple(classLoader, cha, options, options.getInterfaceImplOptions());
  }

  /**
   * @return interface → class implementing this interface (might not be a bijection)
   */
  public static InterfaceImplementationState createAndAddMultiple(IClassLoader classLoader, IClassHierarchy cha,
      AnalysisOptions options, InterfaceImplementationOptions implOptions) {
    if (implOptions.isEmpty()){
      return new InterfaceImplementationState(Collections.emptyMap());
    }
    if (implOptions.isAllImplementedInSameClass()) {
      InterfaceImplementationClass klass = createAndAdd(classLoader, cha, options, implOptions,
          implOptions.getInterfacesToImplement(), implOptions.getGenerator());
      return new InterfaceImplementationState(
          implOptions.getInterfacesToImplement().stream().collect(Collectors.toMap(i -> cha.lookupClass(i).getReference(), i -> klass)));
    }
    return new InterfaceImplementationState(implOptions.getInterfacesToImplement().stream().collect(
        Collectors.toMap(i -> cha.lookupClass(i).getReference(),
        i -> createAndAdd(classLoader, cha, options, implOptions, Collections.singletonList(i), implOptions.getGenerator()))));
  }

  public static void removeAllInterfaceImplementationClasses(IClassHierarchy cha){
    List<IClass> remove = new ArrayList<>();
    for (IClass iClass : cha) {
      if (iClass instanceof InterfaceImplementationClass){
        remove.add(iClass);
      }
    }
    remove.forEach(cha::removeClass);
  }

  private static int lastClassNum = -1;

  private static String generateClassName(IClassHierarchy cha) {
    String suggestedName;
    do {
      lastClassNum++;
      suggestedName = "interfacehelper.Helper" + lastClassNum;
    } while (cha.lookupClass(suggestedName) != null);
    return suggestedName;
  }

  public AnalysisOptions getOptions() {
    return options;
  }

  public int addLoadForType(AbstractRootMethod method, TypeReference type) {
    return method.addGetInstance(getField(type).getReference(), -1);
  }

  private final Map<TypeReference, Map<PointerKey, Set<PointerKey>>> keysPerKeyPerType = new HashMap<>();

  private PointerKey firstInstance = null; // needed for mode == PER_TYPE

  /**
   * Creates new pointer keys for each interface instance
   */
  public void assign(SSAPropagationCallGraphBuilder builder, UninitializedFieldState state, PointerKey instance, PointerKey key,
      TypeReference type) {
    switch (implOptions.getMode()){
    case USE_UNINITIALIZED_FIELD_HELPER:
      state.assign(builder, key); // might crash
      break;
    case PER_TYPE:
      if (implOptions.getMode() == InterfaceImplementationOptions.Mode.PER_TYPE) {
        // use only a single instance
        if (firstInstance == null) {
          firstInstance = instance;
        } else {
          instance = firstInstance; // easier that keeping two different maps around
        }
      }
    case PER_INSTANCE:
      for (TypeReference t : assignableTypeReferences.getSuitableTypeReferences(type)) {
        for (PointerKey pointerKey : create(builder, instance, t)) {
          builder.getPropagationSystem().newConstraint(key, assignOperator, pointerKey);
        }
      }
      break;
    case PER_KEY:
      for (TypeReference t : assignableTypeReferences.getSuitableTypeReferences(type)) {
        for (PointerKey pointerKey : createKeys(builder, t)) {
          builder.getPropagationSystem().newConstraint(key, assignOperator, pointerKey);
        }
      }
    }
  }

  private Set<PointerKey> create(SSAPropagationCallGraphBuilder builder, PointerKey instance, TypeReference ref) {
    return keysPerKeyPerType.computeIfAbsent(ref, r -> new HashMap<>())
        .computeIfAbsent(instance, rr -> createKeys(builder, ref));
  }

  private Set<PointerKey> createKeys(SSAPropagationCallGraphBuilder builder, TypeReference ref){
    PropagationSystem system = builder.getPropagationSystem();
    return IntStream.range(0, implOptions.getCount()).mapToObj(r -> {
      NewSiteReference site = new NewTypedSiteReference(1, ref);
      CGNode constructorNode = null;   // allocate in the context of the constructor, TODO: correct?
      try {
        constructorNode = builder.getCallGraph()
            .findOrCreateNode(constructor, builder.getCallGraph().getFakeRootNode().getContext());
      } catch (CancelException e) { // should not occur
        throw new RuntimeException(e);
      }

      IClass klass = getClassHierarchy().lookupClass(ref); // lookup the class that we want to create a key for
      if (klass == null || klass.isInterface() || klass.isAbstract()) {
        return null;
      }
      InstanceKey iKey;
      if (klass.isArrayClass()) {
        iKey = new NormalAllocationInNode(constructorNode, site, klass);
      } else {
        iKey = builder.getInstanceKeyForAllocation(constructorNode, site);
      }
      if (iKey == null) { // abstract
        return null;
      }
      site.setInstanceKey(iKey);
      PointerKey def = builder.getPointerKeyForLocal(constructorNode, Integer.MAX_VALUE - r);
      system.newConstraint(def, iKey);

      SSAPropagationCallGraphBuilder.ConstraintVisitor constraintVisitor = new SSAPropagationCallGraphBuilder.ConstraintVisitor(
          builder, constructorNode);
      constraintVisitor.visitNew(new SSANewInstruction(0, Integer.MAX_VALUE, site) {
        @Override public SSAInstruction copyForSSA(SSAInstructionFactory ints, int[] defs, int[] uses) {
          return super.copyForSSA(ints, defs, uses);
        }
      });
      return def;
    }).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  public SSAInvokeInstruction addInvocation(AbstractRootMethod method, IMethod target, int[] parameters){
    IInvokeInstruction.IDispatch mode = IInvokeInstruction.Dispatch.STATIC;
    IClass klass = target.getDeclaringClass();
    if (klass.isInterface()){
      mode = IInvokeInstruction.Dispatch.INTERFACE;
    } else if (!target.isStatic()){
      mode = IInvokeInstruction.Dispatch.VIRTUAL;
    }
    return method.addInvocation(parameters, CallSiteReference.make(0, target.getReference(), mode));
  }
}
