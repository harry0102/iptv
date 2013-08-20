/*
 * $RCSfile: PaintServerReference.java,v $
 *
 * Copyright  1990-2009 Sun Microsystems, Inc. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License version
 * 2 only, as published by the Free Software Foundation. 
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is
 * included at /legal/license.txt). 
 * 
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA 
 * 
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, CA 95054 or visit www.sun.com if you need additional
 * information or have any questions. 
 */
package com.sun.perseus.model;

import org.w3c.dom.DOMException;

import com.sun.perseus.j2d.RGB;
import com.sun.perseus.j2d.Transform;

import com.sun.perseus.j2d.PaintDef;
import com.sun.perseus.j2d.PaintServer;
import com.sun.perseus.j2d.PaintTarget;

/**
 * The <code>PaintServerReference</code> object is used to handle
 * references from a <code>GraphicsNode</code> to an actual 
 * <code>Paint</code> implementation. This is needed because
 * paint references are not always resolved, for example when 
 * a document has forward references to paint servers.
 *
 * @version $Id: PaintServerReference.java,v 1.5 2006/06/29 10:47:33 ln156897 Exp $
 */
public class PaintServerReference { 
    /**
     * Paint used when the paint server reference has not been resolved
     * yet. Fully transparent black.
     */
    static final RGB UNRESOLVED_PAINT = new RGB(0, 0, 0);
    
    static class Unresolved implements IDRef, PaintServer {
        /**
         * The referenced id.
         */
        String idRef;
        
        /**
         * The CompositeGraphicsNode using this paint server reference.
         */
        PaintTarget paintTarget;

        /**
         * The requested paintType, i.e., the paint traitName.
         */
        String paintType;

        /**
         * @param ownerDocument the document this reference is part of.
         * @param paintTarget the node which references the paint server.
         * @param paintType the name of the paint trait (e.g., "fill" or 
         *        "stroke")
         * @param idRef the value of the referenced paint server id.
         * @param IllegalArgumentException if ownerDocument, referencingNode,
         *        or idRef is null.
         */
        public Unresolved(final DocumentNode ownerDocument,
                          final PaintTarget paintTarget,
                          final String paintType,
                          final String idRef) {
            if (ownerDocument == null 
                || 
                paintTarget == null
                ||
                idRef == null
                ||
                paintType == null) {
                throw new IllegalArgumentException();
            }
            
            this.paintTarget = paintTarget;
            this.idRef = idRef;
            this.paintType = paintType;
            ownerDocument.resolveIDRef(this, idRef);
        }
        
        /**
         * <code>IDRef</code> implementation.
         *
         * @param ref the resolved referenced (mapped from the id passed to 
         *        the setIdRef method).
         */
        public void resolveTo(final ElementNode ref) {
            if (!(ref instanceof PaintServer)) {
                throw new DOMException(
                        DOMException.INVALID_STATE_ERR,
                        Messages.formatMessage(
                            Messages.ERROR_INVALID_PAINT_SERVER_REFERENCE,
                            new String[] {
                                idRef,
                                ref.getNamespaceURI(),
                                ref.getLocalName()
                            }));
            }
            
            paintTarget.onPaintServerUpdate(paintType, (PaintServer) ref);
        }
        
        /**
         * Returns the id that is referenced by this <code>IDRef</code>.
         *
         * @return the id referenced.
         */
        public String getIdRef() {
            return idRef;
        }
        
        /**
         * @return the PaintDef generated by the server.
         */
        public PaintDef getPaintDef() {
            return UNRESOLVED_PAINT;
        }
        
        /**
         * @param paintType a key that the PaintTarget can use to characterize 
         *        its interest in the PaintServer. For example, a PaintTarget 
         *        may be interested in the Paint both for stroking and filling 
         *        purposes.
         * @param paintTarget the PaintServerTarget listening to changes to the
         *        paint generated by this PaintServer.
         */
        public void setPaintTarget(final String paintType,
                                   final PaintTarget paintTarget) {
            if (paintTarget != this.paintTarget) {
                throw new Error();
            }
        }
        
        /**
         * Called when the PaintServer is no longer used
         */
        public void dispose() {
        }
    }
    
    /**
     * If the requested idRef is resolved, this method checks it is a reference
     * to a PaintServer and returns that PaintServer.
     *
     * If the resquested idRef cannot be resolved, then the method returns and 
     * PaintServerReference.Unresolved instance.
     *
     * @param doc the Document scope.
     * @param paintTarget the PaintServer observer.
     * @param traitName the name of the trait for which the server is seeked.
     * @param idRef the id of the paint server reference.
     */
    public static PaintServer resolve(final DocumentNode doc,
                                      final PaintTarget paintTarget,
                                      final String traitName,
                                      final String idRef) {
        // Check if the paint server reference is already resolved.
        ElementNode ref = (ElementNode) doc.getElementById(idRef);
        if (ref != null) {
            if (!(ref instanceof PaintElement)) {
                throw new DOMException(
                        DOMException.INVALID_STATE_ERR,
                        Messages.formatMessage(
                            Messages.ERROR_INVALID_PAINT_SERVER_REFERENCE,
                            new String[] {
                                idRef,
                                ref.getNamespaceURI(),
                                ref.getLocalName()
                            }));
            } else {
                return ((PaintElement) ref).getPaintServer(traitName, 
                                                           paintTarget);
            }
        } 
        
        // The paint server reference is not resolved yet. We create an 
        // unresolved PaintServer.
        return new PaintServerReference.Unresolved(doc, 
                                                   paintTarget, 
                                                   traitName, 
                                                   idRef);
    }
}
