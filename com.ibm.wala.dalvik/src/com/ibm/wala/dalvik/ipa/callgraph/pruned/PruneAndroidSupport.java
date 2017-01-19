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
package com.ibm.wala.dalvik.ipa.callgraph.pruned;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.pruned.PruningPolicy;

/**
 * This pruning policy prunes away a node if it is withing the android support library shipped
 * with apps for legacy reasons.
 * @author  Martin Mohr &lt;martin.mohr@kit.edu&gt;
 *
 */
public final class PruneAndroidSupport implements PruningPolicy {
	public static final PruneAndroidSupport INSTANCE = new PruneAndroidSupport();
	private PruneAndroidSupport() {}
	@Override
	public boolean check(CGNode n) {
		return !n.getMethod().getDeclaringClass().getReference().getName().toString().startsWith("Landroid/support");
	}

}
