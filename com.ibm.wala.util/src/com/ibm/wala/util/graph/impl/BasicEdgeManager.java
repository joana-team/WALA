/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.util.graph.impl;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.EdgeManager;

/**
 * Simple implementation of an {@link EdgeManager}.
 * @author Martin Mohr &lt;martin.mohr@kit.edu&gt;
 *
 */
public class BasicEdgeManager<T> implements EdgeManager<T> {
  /** maps nodes to their successors */
  private Map<T, Set<T>> succ = HashMapFactory.make();

  /** maps nodes to their predecessors */
  private Map<T, Set<T>> pred = HashMapFactory.make();

  @Override
  public Iterator<T> getPredNodes(T n) {
    return getValues(pred, n);
  }

  @Override
  public int getPredNodeCount(T n) {
    return getSize(pred, n);
  }

  @Override
  public Iterator<T> getSuccNodes(T n) {
    return getValues(succ, n);
  }

  @Override
  public int getSuccNodeCount(T n) {
    return getSize(succ, n);
  }

  @Override
  public void addEdge(T src, T dst) {
    addMapping(succ, src, dst);
    addMapping(pred, dst, src);
  }

  @Override
  public void removeEdge(T src, T dst) throws UnsupportedOperationException {
    removeMapping(succ, src, dst);
    removeMapping(pred, dst, src);
  }

  @Override
  public void removeAllIncidentEdges(T node) throws UnsupportedOperationException {
    removeIncomingEdges(node);
    removeOutgoingEdges(node);
  }

  @Override
  public void removeIncomingEdges(T node) throws UnsupportedOperationException {
    removeAllMappings(pred, node);
    for (Map.Entry<T, Set<T>> e : succ.entrySet()) {
      e.getValue().remove(node);
    }
  }

  @Override
  public void removeOutgoingEdges(T node) throws UnsupportedOperationException {
    removeAllMappings(succ, node);
    for (Map.Entry<T, Set<T>> e : pred.entrySet()) {
      e.getValue().remove(node);
    }
  }

  @Override
  public boolean hasEdge(T src, T dst) {
    Set<T> s = succ.get(src);
    return s != null && s.contains(dst); 
  }

  private static <X,Y> void addMapping(Map<X,Set<Y>> m, X key, Y value) {
    Set<Y> s = m.get(key);
    if (s == null) {
      s = HashSetFactory.make();
      m.put(key, s);
    }
    s.add(value);
  }

  private static <X,Y> void removeMapping(Map<X,Set<Y>> m, X key, Y value) {
    Set<Y> s = m.get(key);
    if (s != null) {
      s.remove(value);
    }
  }

  private static <X,Y> void removeAllMappings(Map<X,Set<Y>> m, X key) {
    m.put(key, HashSetFactory.<Y>make());
  }

  private static <X,Y> Iterator<Y> getValues(Map<X,Set<Y>> m, X key) {
    Set<Y> s = m.get(key);
    if (s == null) {
      return Collections.emptyIterator();
    } else {
      return s.iterator();
    }
  }

  private static <X,Y> int getSize(Map<X,Set<Y>> m, X key) {
    Set<Y> s = m.get(key);
    if (s == null) {
      return 0;
    } else {
      return s.size();
    }
  }

}
