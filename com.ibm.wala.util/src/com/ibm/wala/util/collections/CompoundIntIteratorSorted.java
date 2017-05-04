/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     KIT (Karlsruhe) - adaptation for sorted IntIterator
 *******************************************************************************/
package com.ibm.wala.util.collections;

import java.util.NoSuchElementException;

import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.debug.UnimplementedError;
import com.ibm.wala.util.intset.IntIterator;

/**
 * An Iterator which provides a concatenation of two sorted IntIterators.
 */
public class CompoundIntIteratorSorted implements IntIterator {

  final IntIterator A;

  final IntIterator B;
  
  int currentA;
  int currentB;
  
  boolean currentAValid = false;
  boolean currentBValid = false;


  /**
   * @param A the first iterator in the concatenated result. Must be sorted.
   * @param B the second iterator in the concatenated result Must be sorted.
   */
  public CompoundIntIteratorSorted(IntIterator A, IntIterator B) {
    this.A = A;
    this.B = B;
    if (A == null) {
      throw new IllegalArgumentException("null A");
    }
    if (B == null) {
      throw new IllegalArgumentException("null B");
    }
  }


  @Override
  public boolean hasNext() {
    return currentAValid || currentBValid || A.hasNext() || B.hasNext();
  }


  @Override
  public int next() {
    if (!currentAValid && A.hasNext()) {
      currentA = A.next();
      currentAValid = true;
    }
    if (!currentBValid && B.hasNext()) {
      currentB = B.next();
      currentBValid = true;
    }
    if (currentAValid) {
      if (currentBValid) {
        if (currentA <= currentB) {
          currentAValid = false;
          return currentA;
        } else {
          currentBValid = false;
          return currentB;
        }
      } else {
        currentAValid = false;
        return currentA;
      }
    } else if (currentBValid) {
      currentBValid = false;
      return currentB;
    } else {
      throw new NoSuchElementException();
    }
  }

  @Override
  public int hashCode() throws UnimplementedError {
    Assertions.UNREACHABLE("define a custom hash code to avoid non-determinism");
    return 0;
  }
}
