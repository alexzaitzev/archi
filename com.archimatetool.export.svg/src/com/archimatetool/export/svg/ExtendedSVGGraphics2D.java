/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.export.svg;

import org.apache.batik.svggen.DOMGroupManager;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.eclipse.draw2d.IFigure;
import org.w3c.dom.Element;

import com.archimatetool.editor.diagram.figures.connections.IArchimateConnectionFigure;
import com.archimatetool.editor.diagram.figures.diagram.DiagramModelReferenceFigure;
import com.archimatetool.editor.diagram.figures.elements.IArchimateFigure;
import com.archimatetool.model.IDiagramModelReference;

@SuppressWarnings("nls")
public class ExtendedSVGGraphics2D extends SVGGraphics2D  {
    
    private static final String CONCEPT_ID = "concept-id";
    private static final String VIEW_ID = "view-id";
    
    private IFigure currentFigure;
	
    public ExtendedSVGGraphics2D(SVGGeneratorContext generatorCtx, boolean textAsShapes) {
		super(generatorCtx, textAsShapes);
	}

	public void setCurrentFigure(IFigure figure) {
	    currentFigure = figure;
	}
	
    @Override
    protected void setGeneratorContext(SVGGeneratorContext generatorCtx) {
    	super.setGeneratorContext(generatorCtx);
    	
    	domGroupManager = new DOMGroupManager(gc, domTreeManager) {
    		@Override
    		public void addElement(Element e) {
    		    setId(e);
    			super.addElement(e);
    		}
    		
    		@Override
    		public void addElement(Element e, short m) {
    		    setId(e);
    		    super.addElement(e, m);
    		}
    		
    		private void setId(Element e) {
    		    if(currentFigure instanceof IArchimateFigure) {
                    e.setAttribute(CONCEPT_ID, ((IArchimateFigure)currentFigure).getDiagramModelArchimateObject().getArchimateConcept().getId());
                }
    		    else if(currentFigure instanceof IArchimateConnectionFigure) {
    		        e.setAttribute(CONCEPT_ID, ((IArchimateConnectionFigure)currentFigure).getDiagramModelArchimateConnection().getArchimateConcept().getId());
    		    }
    		    else if(currentFigure instanceof DiagramModelReferenceFigure) {
                    e.setAttribute(VIEW_ID, ((IDiagramModelReference)((DiagramModelReferenceFigure)currentFigure).getDiagramModelObject()).getReferencedModel().getId());
                }
    		}
    	};
    }
}
