package com.ibm.wala.ipa.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.TypeReference;

import java.util.*;

/**
 * Cached sub type relation evaluator
 */
public class SubTypeHierarchy {

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

  static class BoundedLinkedMap<K, V> extends LinkedHashMap<K, V> {

    static final long serialVersionUID = 1097234606234l;

    private final int size;

    private static final int DEFAULT_SIZE = 100;

    BoundedLinkedMap(int size) {
      this.size = size;
    }

    BoundedLinkedMap(){
      this(DEFAULT_SIZE);
    }

    @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
      if (size < size()){
        return remove(eldest.getKey()) != null;
      }
      return false;
    }
  }

  private BoundedLinkedMap<TypeReference, Map<TypeReference, Relation>> relations = new BoundedLinkedMap<>();
  private BoundedLinkedMap<TypeReference, Set<TypeReference>> subTypes = new BoundedLinkedMap<>();
  public final IClassHierarchy cha;

  public SubTypeHierarchy(IClassHierarchy cha) {
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

  public Set<TypeReference> getSubTypes(TypeReference base){
    return subTypes.computeIfAbsent(base, b -> {
      Set<TypeReference> refs = new HashSet<>();
      for (IClass iClass : cha) {
        if (isSubClass(base, iClass.getReference())){
          refs.add(iClass.getReference());
        }
      }
      return refs;
    });
  }
}
