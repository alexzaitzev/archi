/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.editor.diagram.figures;

import org.eclipse.draw2d.IFigure;

/**
 * Interface to notifiy that an IFigure is about to be painted in its paintFigure(Graphics) method
 * 
 * @author Phillip Beauvoir
 */
public interface IPaintFigureListener {
    void notifyPaint(IFigure figure);
}
