/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.ipa.callgraph.pruned;

import com.ibm.wala.ipa.callgraph.CGNode;

/**
 * A ConjunctivePruningPolicy of policies p1 and p2 keeps a node iff
 * both p1 and p2 keep it.
 * @author Martin Mohr &lt;martin.mohr@kit.edu&gt;
 *
 */
public class ConjunctivePruningPolicy implements PruningPolicy {
  private final PruningPolicy p1;
  private final PruningPolicy p2;
  public ConjunctivePruningPolicy(PruningPolicy p1, PruningPolicy p2) {
    this.p1 = p1;
    this.p2 = p2;
  }
  @Override
  public boolean check(CGNode n) {
    return p1.check(n) && p2.check(n);
  }

}
