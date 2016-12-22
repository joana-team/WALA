/*******************************************************************************
 * Copyright (c) 2002,2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation of Readers
 *     Martin Hecker - EnclosingMethod, based on existing Readers
 *******************************************************************************/
package com.ibm.wala.shrikeCT;

/**
 * This class reads EnclosingMethod attributes.
 */
public final class EnclosingMethodReader extends AttributeReader {
  /**
   * Build a reader for the attribute 'iter'.
   */
  public EnclosingMethodReader(ClassReader.AttrIterator iter) throws InvalidClassFileException {
    super(iter, "EnclosingMethod");

    checkSize(attr, 10);
    if (cr.getUInt(attr + 2) != 4) throw new InvalidClassFileException(
        attr + 2,
        "Illegal attribute_length for EnclosingMethod:" + cr.getUShort(attr + 2)
    );
    
  }

  /**
   * @return the name of the  innermost class that encloses the declaration of the current class 
   */
  public String getEnclosingClass() throws InvalidClassFileException {
    ConstantPoolParser cp = cr.getCP();
    return cp.getCPClass(cr.getUShort(attr + 6));
  }

  
  /**
   * @return the name of the method or constructor (a member of the class this.getEnclosingClass()) that encloses this class,
   * or null if this class is not immediately enclosed by a method or constructor.
   */
  public String getEnclosingMethodName() throws InvalidClassFileException {
    ConstantPoolParser cp = cr.getCP();
    final int method_index = cr.getUShort(attr + 8); 
    return method_index == 0 ? null : cp.getCPNATName(method_index);
  }

  /**
   * @return the type of the method or constructor (a member of the class this.getEnclosingClass()) that encloses this class,
   * or null if this class is not immediately enclosed by a method or constructor.
   */
  public String getEnclosingMethodType() throws InvalidClassFileException {
    ConstantPoolParser cp = cr.getCP();
    final int method_index = cr.getUShort(attr + 8); 
    return method_index == 0 ? null : cp.getCPNATType(method_index);
  }
}