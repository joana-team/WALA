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

import com.ibm.wala.util.graph.AbstractGraph;
import com.ibm.wala.util.graph.EdgeManager;
import com.ibm.wala.util.graph.NodeManager;

/**
 * This is a basic graph implementation which uses the {@link BasicNodeManager}
 * and the {@link BasicEdgeManager}.
 *
 * @author  &lt;martin.mohr@kit.edu&gt;
 *
 * @param <T> type of nodes in the graph
 */
public class BasicGraph<T> extends AbstractGraph<T> {

  private BasicNodeManager<T> nodeMan = new BasicNodeManager<>();
  private BasicEdgeManager<T> edgeMan = new BasicEdgeManager<>();

  @Override
  protected NodeManager<T> getNodeManager() {
    return nodeMan;
  }

  @Override
  protected EdgeManager<T> getEdgeManager() {
    return edgeMan;
  }
}
