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
package com.ibm.wala.ssa.analysis;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.wala.analysis.stackMachine.AbstractIntStackMachine;
import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.fixedpoint.impl.DefaultFixedPointSolver;
import com.ibm.wala.fixpoint.BooleanVariable;
import com.ibm.wala.fixpoint.UnaryOr;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.CancelRuntimeException;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;

/**
 * Eliminate dead assignments (phis) from an SSA IR.
 */
public class TrivialPhiElimination {

  private static final boolean DEBUG = false;
  private static final int NONE = AbstractIntStackMachine.IGNORE - 1;
  {
    assert !AbstractIntStackMachine.isSpecialValueNumber(NONE);
  }

  /**
   * eliminate dead phis from an ir
   * @throws IllegalArgumentException  if ir is null
   */
  public static void perform(IR ir) {
    if (ir == null) {
      throw new IllegalArgumentException("ir is null");
    }
    final DefUse DU = new DefUse(ir);
    final SymbolTable symbolTable = ir.getSymbolTable();
    
    
    final int[] actualValue = new int[symbolTable.getMaxValueNumber()+1];
    for (int i = 0; i < actualValue.length; i++) {
      actualValue[i] = i;
    }
    
    final Set<SSAPhiInstruction> workQueue = new TreeSet<>(new Comparator<SSAPhiInstruction>() {
      @Override
      public int compare(SSAPhiInstruction o1, SSAPhiInstruction o2) {
        return Integer.compare(o2.getDef(), o1.getDef());
      }
    });
    
    final Set<SSAPhiInstruction> trivial = new HashSet<>();
    
    for (Iterator it = ir.iteratePhis(); it.hasNext();) {
      final SSAPhiInstruction phi = (SSAPhiInstruction) it.next();
      final boolean isNew = workQueue.add(phi);
      assert isNew;
    }
    
    while (!workQueue.isEmpty()) {
      final SSAPhiInstruction phiDef; {
        Iterator<SSAPhiInstruction> it = workQueue.iterator();
        phiDef = it.next();
        it.remove();
      }
      assert (SSAPhiInstruction) DU.getDef(phiDef.getDef()) == phiDef;
      final boolean isNewTrivialPhi = updateActualValueIfIsNewTrivial(phiDef, actualValue);
      if (isNewTrivialPhi) {
        trivial.add(phiDef);
        for (Iterator<SSAInstruction> it = DU.getUses(phiDef.getDef()); it.hasNext();) {
          final SSAInstruction instruction = it.next();
          if (instruction instanceof SSAPhiInstruction) {
            workQueue.add((SSAPhiInstruction) instruction);
          }
        }
      }
    }
    
    removeTrivialPhis(ir, trivial);
    updateTrivialPhiReferences(ir, actualValue);
  }
  
  private static void updateTrivialPhiReferences(IR ir, int[] actualValue) {
    for( Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext(); ) {
      final SSAInstruction instruction = it.next();
      instruction.substitudeUses(actualValue);
    }
  }
  
  public static boolean updateActualValueIfIsNewTrivial(SSAPhiInstruction phi, int[] actualValues) {
    final int originalDef = phi.getDef();
    assert (!AbstractIntStackMachine.isSpecialValueNumber(phi.getDef()));
    final int def = actualValues[originalDef];
    assert (!AbstractIntStackMachine.isSpecialValueNumber(def));
    int other = NONE;
    for (int i = 0; i < phi.getNumberOfUses(); i++) {
      final int originalUse = phi.getUse(i);
      if (AbstractIntStackMachine.isSpecialValueNumber(originalUse)) {
        return false;
      }
      final int use = actualValues[originalUse];
      assert use != NONE;
      if (!(use == def || use == other)) {
        if (other == NONE) {
          other = use;
        } else {
          return false;
        }
      }
    }
    int actualValue = (other == NONE) ? def : other;
    if (actualValues[originalDef] != actualValue) {
      actualValues[originalDef] = actualValue;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Perform the transformation
   * @param ir IR to transform
   * @param trivial the set of trivial phi instructions
   */
  private static void removeTrivialPhis(IR ir, Set<SSAPhiInstruction> trivial) {
    final ControlFlowGraph cfg = ir.getControlFlowGraph();
    if (DEBUG) {
      System.err.println("eliminateTrivialPhis: " + trivial);
    }
    
    for (Iterator x = cfg.iterator(); x.hasNext();) {
      BasicBlock b = (BasicBlock) x.next();

      if (b.hasPhi()) {
        int removed = b.removePhis(trivial);
        if (DEBUG) {
          System.err.println("Removed " + removed + " phis from " + b);
        }
      }
    }
  }
}
