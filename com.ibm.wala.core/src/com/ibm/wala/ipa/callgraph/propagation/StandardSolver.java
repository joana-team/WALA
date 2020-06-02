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


import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;

import java.util.HashSet;
import java.util.Set;

/**
 * standard fixed-point iterative solver for pointer analysis
 */
public class StandardSolver extends AbstractPointsToSolver {

  private static final boolean DEBUG_PHASES = DEBUG || false;
  
  public StandardSolver(PropagationSystem system, PropagationCallGraphBuilder builder) {
    super(system, builder);
  }

  /*
   * @see com.ibm.wala.ipa.callgraph.propagation.IPointsToSolver#solve()
   */
  @Override
  public void solve(IProgressMonitor monitor) throws IllegalArgumentException, CancelException {
    UninitializedFieldHelperOptions fieldHelperOptions = getBuilder().getOptions().getFieldHelperOptions();
    if (fieldHelperOptions.isEmpty()){
      solveImpl(monitor, UninitializedFieldState.createDummy());
    } else {
      fieldHelperOptions.setRoot(getBuilder().getCallGraph().getFakeRootNode());
      UninitializedFieldState uninitializedFieldState = new UninitializedFieldState(fieldHelperOptions, new SubTypeHierarchy(getBuilder().getClassHierarchy()));
      Set<CGNode> discoveredNodes = new HashSet<>(getBuilder().getDiscoveredNodes());
      solveImpl(monitor, uninitializedFieldState);

      solveImplWithUninitializedKeys(monitor, uninitializedFieldState, discoveredNodes);
      while (uninitializedFieldState.hasKeyWithEmptySet()) {
        //System.out.println(uninitializedFieldState.getKeysWithEmptySet());
        // start a new run over all nodes, in case we missed an affected node
        getSystem().initForFirstSolve();
        solveImplWithUninitializedKeys(monitor, uninitializedFieldState, discoveredNodes);
        if (!uninitializedFieldState.hasKeyWithEmptySet()) {
          break; // we did not collect anything new
        }
        // we did collect something new, use this information
        solveImplWithUninitializedKeys(monitor, uninitializedFieldState, discoveredNodes);
      }
    }
  }

  private void solveImplWithUninitializedKeys(IProgressMonitor monitor,  UninitializedFieldState uninitializedFieldState, Set<CGNode> discoveredNodes)
      throws CancelException {
    uninitializedFieldState.filterRecorded(k -> !this.hasPointsToSetFor(k));
    discoveredNodes.addAll(uninitializedFieldState.getCGNodesWithReplacements());
    getBuilder().setDiscoveredNodes(discoveredNodes);
    getBuilder().removeFromAlreadyVisitedNodes(uninitializedFieldState.getCGNodesWithReplacements());
    solveImpl(monitor, uninitializedFieldState);
  }

  private void solveImpl(IProgressMonitor monitor,
      UninitializedFieldState uninitializedFieldState) throws IllegalArgumentException, CancelException {
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
