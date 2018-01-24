/*******************************************************************************
 * Copyright (c) 2002 - 2017 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Martin Hecker, KIT - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.ssa.analysis;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.wala.analysis.stackMachine.AbstractIntStackMachine;
import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SymbolTable;

/**
 * Eliminate trivial phis from an SSA IR.
 * 
 * See, e.g., [1] or [2]
 * 
 * [1] Sebastian Buchwald, Denis Lohner, Sebastian Ullrich,
 *     Verified Construction of Static Single Assignment Form
 *     http://dx.doi.org/10.1145/2892208.2892211
 * [2] Matthias Braun, Sebastian Buchwald, Sebastian Hack, Roland Leißa, Christoph Mallon, Andreas Zwinkau,
 *     Simple and Efficient Construction of Static Single Assignment Form
 *     http://dx.doi.org/10.1007/978-3-642-37051-9_6
 */
public class TrivialPhiElimination {

  private static final boolean DEBUG = false;
  private static final int NONE = AbstractIntStackMachine.IGNORE - 1;
  {
    assert !AbstractIntStackMachine.isSpecialValueNumber(NONE);
    assert NONE < 0;
  }

  /**
   * eliminate trivial phis from an ir
   * @throws IllegalArgumentException  if ir is null
   */
  public static void perform(IR ir) {
    if (ir == null) {
      throw new IllegalArgumentException("ir is null");
    }
    final DefUse DU = new DefUse(ir);
    final SymbolTable symbolTable = ir.getSymbolTable();
    
    // A mapping of every value to it's "actual" value, i.e.: the value number
    // that denotes its value after the removal of trivial phis.
    // Initially: the identity map.
    // Only values defined by trivial PhiAssignments will be modified.
    final int[] actualValue = new int[symbolTable.getMaxValueNumber()+1];
    for (int i = 0; i < actualValue.length; i++) {
      actualValue[i] = i;
    }
    
    final Set<SSAPhiInstruction> workQueue = new TreeSet<>(new Comparator<SSAPhiInstruction>() {
      @Override
      public int compare(SSAPhiInstruction o1, SSAPhiInstruction o2) {
        // TODO: this probably should  be topologically sorted by the def-use graph, or something.
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
          // TODO: is this instruction != phiDef sufficient to guarantee termination?!?!
          if (instruction != phiDef && instruction instanceof SSAPhiInstruction) {
            workQueue.add((SSAPhiInstruction) instruction);
          }
        }
      }
    }
    
    assert isConsistent(actualValue, DU);
    
    removeTrivialPhis(ir, trivial);
    updateTrivialPhiReferences(ir, actualValue);
    
    assert isConsistent(ir);
  }
  
  private static boolean isConsistent(IR ir) {
    final DefUse defUse = new DefUse(ir);
    final SymbolTable symbolTable = ir.getSymbolTable();
    for (Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext(); ) {
      final SSAInstruction instruction = it.next();
      if (instruction.hasDef()) {
        for (int i = 0; i < instruction.getNumberOfDefs(); i++) {
          assert defUse.getDef(instruction.getDef(i)) == instruction;
        }
      }
      for (int i = 0; i < instruction.getNumberOfUses(); i++) {
        final int use = instruction.getUse(i);
        assert AbstractIntStackMachine.isSpecialValueNumber(use)
            || symbolTable.isConstant(use)
            || symbolTable.isParameter(use)
            || defUse.getDef(use) != null;
      }
    }
    return true;
  }
  
  private static boolean isConsistent(int[] actualValue, DefUse defUse) {
    for(int v = 0; v < actualValue.length; v++) {
      assert actualValue[v] >= 0;
      assert actualValue[v] <  actualValue.length;
      assert actualValue[actualValue[v]] == actualValue[v];
      if (actualValue[v] != v) {
        final SSAPhiInstruction phi = (SSAPhiInstruction) defUse.getDef(v);
        for (int i = 0; i < phi.getNumberOfUses(); i++) {
          assert actualValue[phi.getUse(i)] == actualValue[v];
        }
      }
    }
    return true;
  }
  
  /**
   * Update the references to (i.e.: usages of) removed trivial phis values to their actual values.
   * @param ir IR to transform
   * @param actualValue the mapping of value numbers to their actual values.
   */
  private static void updateTrivialPhiReferences(IR ir, int[] actualValue) {
    for( Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext(); ) {
      final SSAInstruction instruction = it.next();
      instruction.substitudeUses(actualValue);
    }
  }
  
  private static boolean updateActualValueIfIsNewTrivial(SSAPhiInstruction phi, int[] actualValues) {
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
   * Perform the removal of trivial phis.
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
