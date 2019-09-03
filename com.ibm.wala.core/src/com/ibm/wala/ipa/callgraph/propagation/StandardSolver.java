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
package com.ibm.wala.ipa.callgraph.propagation;


import com.ibm.wala.fixpoint.IFixedPointStatement;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.UninitializedFieldHelperClass;
import com.ibm.wala.ipa.callgraph.UninitializedFieldHelperOptions;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * standard fixed-point iterative solver for pointer analysis
 */
public class StandardSolver extends AbstractPointsToSolver {

  private static final boolean DEBUG_PHASES = DEBUG || false;

  private final UninitializedFieldHelperClass fieldHelperClass;
  
  public StandardSolver(PropagationSystem system, PropagationCallGraphBuilder builder) {
    super(system, builder);
    this.fieldHelperClass = builder.getOptions().getFieldHelperOptions().getHelperClass(); // passing it this way is far easier
  }

  /*
   * @see com.ibm.wala.ipa.callgraph.propagation.IPointsToSolver#solve()
   */
  @Override
  public void solve(IProgressMonitor monitor) throws IllegalArgumentException, CancelException {
    UninitializedFieldHelperOptions.UninitializedFieldState uninitializedFieldState = new UninitializedFieldHelperOptions.UninitializedFieldState(fieldHelperClass);
    Set<CGNode> discoveredNodes = new HashSet<>(getBuilder().getDiscoveredNodes());
    solveImpl(monitor, uninitializedFieldState);
    uninitializedFieldState.setKeysToReplace(uninitializedFieldState.getRecordedKeys().stream().filter(k -> !this.hasPointsToSetFor(k)).collect(Collectors.toSet()));
    /*getSystem().getFixedPointSystem().getStatements()
        .forEachRemaining(n -> getSystem().getFixedPointSystem()
          .removeStatement((IFixedPointStatement<PointsToSetVariable>) n));*/
    getSystem().initializeWorkList();
    getBuilder().setDiscoveredNodes(discoveredNodes);
    getBuilder().clearAlreadyVisited();
    solveImpl(monitor, uninitializedFieldState);
  }

  private void solveImpl(IProgressMonitor monitor,
      UninitializedFieldHelperOptions.UninitializedFieldState uninitializedFieldState) throws IllegalArgumentException, CancelException {
    getBuilder().uninitializedFieldState = uninitializedFieldState;
    int i = 0;
    do {
      i++;

      if (DEBUG_PHASES) {
        System.err.println("Iteration " + i);
      }
      getSystem().solve(monitor);
      if (DEBUG_PHASES) {
        System.err.println("Solved " + i);
      }

      if (getBuilder().getOptions().getMaxNumberOfNodes() > -1) {
        if (getBuilder().getCallGraph().getNumberOfNodes() >= getBuilder().getOptions().getMaxNumberOfNodes()) {
          if (DEBUG) {
            System.err.println("Bail out from call graph limit" + i);
          }
          throw CancelException.make("reached call graph size limit");
        }
      }

      // Add constraints until there are no new discovered nodes
      if (DEBUG_PHASES) {
        System.err.println("adding constraints");
      }
      getBuilder().addConstraintsFromNewNodes(monitor);

      // getBuilder().callGraph.summarizeByPackage();
      
      if (DEBUG_PHASES) {
        System.err.println("handling reflection");
      }
      if (i <= getBuilder().getOptions().getReflectionOptions().getNumFlowToCastIterations()) {
        getReflectionHandler().updateForReflection(monitor);
      }
      // Handling reflection may have discovered new nodes!
      if (DEBUG_PHASES) {
        System.err.println("adding constraints again");
      }
      getBuilder().addConstraintsFromNewNodes(monitor);

      if (monitor != null) { monitor.worked(i); }
      // Note that we may have added stuff to the
      // worklist; so,
    } while (!getSystem().emptyWorkList());
    getBuilder().uninitializedFieldState = null;
  }

  /**
   * @return is the points to set non empty?
   */
  boolean hasPointsToSetFor(PointerKey key){
    return getSystem().findOrCreatePointsToSet(key).size() > 0;
  }
}
