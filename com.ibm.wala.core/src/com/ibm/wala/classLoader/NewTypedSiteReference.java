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

package com.ibm.wala.classLoader;

import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.types.TypeReference;

/**
 * Represents a textual allocation site
 * 
 * Note that the identity of a {@link NewTypedSiteReference} depends on two things: the program counter, and the containing {@link IR}. Thus, it suffices
 * to defines equals() and hashCode() from ProgramCounter, since this class does not maintain a pointer to the containing IR (or
 * CGNode) anyway. If using a hashtable of NewSiteReference from different IRs, you probably want to use a wrapper which also holds
 * a pointer to the governing CGNode.
 *
 * Difference to {@link NewSiteReference}: hashCode and equals also consider the type.
 * Needed for {@link com.ibm.wala.ipa.callgraph.UninitializedFieldState}
 */
public class NewTypedSiteReference extends NewSiteReference {

  private InstanceKey instanceKey;

  /**
   * @param programCounter bytecode index of the allocation site
   * @param declaredType declared type that is allocated
   */
  public NewTypedSiteReference(int programCounter, TypeReference declaredType) {
    super(programCounter, declaredType);
  }

  public static NewTypedSiteReference make(int programCounter, TypeReference declaredType) {
    return new NewTypedSiteReference(programCounter, declaredType);
  }

  @Override public int hashCode() {
    return super.hashCode() * 31 + getDeclaredType().hashCode();
  }

  @Override public boolean equals(Object obj) {
    return super.equals(obj) && obj instanceof NewTypedSiteReference &&
        getDeclaredType().equals(((NewTypedSiteReference) obj).getDeclaredType());
  }

  @Override public boolean hasInstanceKey() {
    return instanceKey != null;
  }

  @Override public void setInstanceKey(InstanceKey instanceKey) {
    this.instanceKey = instanceKey;
  }

  @Override public InstanceKey getInstanceKey() {
    return instanceKey;
  }
}
