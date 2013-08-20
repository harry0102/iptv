/*
 * $RCSfile: ImageNode.java,v $
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

import com.sun.perseus.j2d.Box;
import com.sun.perseus.j2d.GraphicsProperties;
import com.sun.perseus.j2d.ViewportProperties;
import com.sun.perseus.j2d.PaintTarget;
import com.sun.perseus.j2d.RasterImage;
import com.sun.perseus.j2d.RenderGraphics;
import com.sun.perseus.j2d.Transform;
import com.sun.perseus.j2d.PaintServer;
import com.sun.perseus.j2d.RGB;

import org.w3c.dom.svg.SVGRect;
import org.w3c.dom.svg.SVGRGBColor;

import com.sun.perseus.util.SVGConstants;
import com.sun.perseus.util.RunnableQueue;
import com.sun.perseus.util.RunnableQueue.RunnableHandler;

import org.w3c.dom.DOMException;


/**
 * This class models the SVG Tiny <code>&lt;image&gt;</code>.
 *
 * A value of 0 on the width and height attributes disables the 
 * rendering of the Image object. Negative width or height values
 * on the <code>ImageNode</code> are illegal.
 *
 * If the referenced <code>Image</code> is null, this node will draw
 * a gray box using the current fill value
 *
 * @version $Id: ImageNode.java,v 1.17 2006/06/29 10:47:32 ln156897 Exp $
 */
public class ImageNode extends AbstractRenderingNode 
        implements RasterImageConsumer, ViewportNode, PaintTarget {
    /**
     * width and height are required on <image>
     */
    static final String[] REQUIRED_TRAITS
        = {SVGConstants.SVG_WIDTH_ATTRIBUTE,
           SVGConstants.SVG_HEIGHT_ATTRIBUTE};

    /**
     * xlink:href is required on <image>
     */
    static final String[][] REQUIRED_TRAITS_NS
        = { {SVGConstants.XLINK_NAMESPACE_URI, 
             SVGConstants.SVG_HREF_ATTRIBUTE} };

    /**
     * The viewport defined by the image element
     */
    protected float x, y, width, height;

    /**
     * The painted Image. The image is built from the 
     * href attribute.
     */
    protected RasterImage image;

    /**
     * Controls whether the image has been loaded or
     * not.
     */
    protected boolean imageLoaded;

    /**
     * The reference to the image data. This can be using 
     * base64 PNG or JPEG encoding or an absolute or relative
     * URI.
     */
    protected String href;

    /**
     * The absolute URI is computed from the href and the
     * node's base URI.
     */
    protected String absoluteURI;

    /**
     * Controls whether the image is aligned in the viewport
     * or if it is just scaled to the viewport.
     */
    protected int align = StructureNode.ALIGN_XMIDYMID;

    /**
     * Constant used to identify broken URI values
     */
    protected static final String BROKEN_URI = "broken uri";

    /**
     * The current viewport fill color.
     */
    protected PaintServer viewportFill = INITIAL_VIEWPORT_FILL;

    /**
     * The current viewport fill opacity.
     */
    protected float viewportFillOpacity = INITIAL_VIEWPORT_FILL_OPACITY;

    // =========================================================================
    // Image specific
    // =========================================================================

    /**
     * Constructor.
     *
     * @param ownerDocument this element's owner <code>DocumentNode</code>
     */
    public ImageNode(final DocumentNode ownerDocument) {
        super(ownerDocument);
        image = ownerDocument.getImageLoader().getLoadingImage();
        
        // Initially, the image's width and height are zero, so we
        // set the corresponding bits accordingly.
        canRenderState |= CAN_RENDER_ZERO_WIDTH_BIT;
        canRenderState |= CAN_RENDER_ZERO_HEIGHT_BIT;
    }

    /**
     * @return the SVGConstants.SVG_IMAGE_TAG value
     */
    public String getLocalName() {
        return SVGConstants.SVG_IMAGE_TAG;
    }


    /**
     * Used by <code>DocumentNode</code> to create a new instance from
     * a prototype <code>ImageNode</code>.
     *
     * @param doc the <code>DocumentNode</code> for which a new node is
     *        should be created.
     * @return a new <code>ImageNode</code> for the requested document.
     */
    public ElementNode newInstance(final DocumentNode doc) {
        return new ImageNode(doc);
    }

    /**
     * @param newX the new position of this image's upper left
     *        corner on the x-axis
     */
    public void setX(final float newX) {
        if (newX == x) {
            return;
        }
        modifyingNode();
        this.x = newX;
        modifiedNode();
    }

    /**
     * @param newY the new postion of this image's upper left 
     *        corner on the y-axis
     */
    public void setY(final float newY) {
        if (newY == y) {
            return;
        }
        modifyingNode();
        
        this.y = newY;
        modifiedNode();
    }

    /**
     * @return this image's upper left corner position on the x-axis
     */
    public float getX() {
        return x;
    }

    /**
     * @return this image's upper left corner position on the y-axis
     */
    public float getY() {
        return y;
    }

    /**
     * @param newWidth this node's new width. Should be strictly positive
     */
    public void setWidth(final float newWidth) {
        if (newWidth < 0) {
            throw new IllegalArgumentException();
        }

        if (newWidth == width) {
            return;
        }

        modifyingNode();
        this.width = newWidth;
        computeCanRenderWidthBit(width);
        modifiedNode();
    }

    /**
     * @return this node's width
     */
    public float getWidth() {
        return width;
    }

    /**
     * @param newHeight the new height for this image's node
     *        Should be strictly positive.
     */
    public void setHeight(final float newHeight) {
        if (newHeight < 0) {
            throw new IllegalArgumentException();
        }

        if (newHeight == height) {
            return;
        }

        modifyingNode();
        this.height = newHeight;
        computeCanRenderHeightBit(height);
        modifiedNode();
    }

    /**
     * @return this image's height
     */
    public float getHeight() {
        return height;
    }

    /**
     * Returns the value of the given Object-valued property.
     *
     * @return the value of the given Object-valued property.
     */
    protected Object getPropertyState(final int propertyIndex) {
        switch (propertyIndex) {
        case PROPERTY_VIEWPORT_FILL:
            return viewportFill;
        default: 
            return super.getPropertyState(propertyIndex);
        }
    }

    /**
     * Returns the value of the given float-valued property.
     *
     * @return the value of the given property.
     */
    protected float getFloatPropertyState(final int propertyIndex) {
        switch (propertyIndex) {
        case PROPERTY_VIEWPORT_FILL_OPACITY:
            return viewportFillOpacity;
        default: 
            return super.getFloatPropertyState(propertyIndex);
        }
    }

    /**
     * Sets the computed value of the given Object-valued property.
     *
     * @param propertyIndex the property index
     * @param propertyValue the computed value of the property.
     */
    protected void setPropertyState(final int propertyIndex,
                                    final Object propertyValue) {
        switch (propertyIndex) {
        case PROPERTY_VIEWPORT_FILL:
            this.viewportFill = ((PaintServer) propertyValue);
            break;
        default: 
            super.setPropertyState(propertyIndex, propertyValue);
            break;
        }
    }

    /**
     * Sets the computed value of the given float-valued property.
     *
     * @param propertyIndex the property index
     * @param propertyValue the computed value of the property.
     */
    protected void setFloatPropertyState(final int propertyIndex,
                                          final float propertyValue) {
        switch (propertyIndex) {
        case PROPERTY_VIEWPORT_FILL_OPACITY:
            this.viewportFillOpacity = propertyValue;
	    break;
        default: 
            super.setFloatPropertyState(propertyIndex, propertyValue);
            break;
        }
    }

    /**
     * Checks the state of the Object-valued property.
     *
     * @param propertyIndex the property index
     * @param propertyValue the computed value of the property.
     */
    protected boolean isPropertyState(final int propertyIndex,
                                      final Object propertyValue) {
        switch (propertyIndex) {
        case ViewportNode.PROPERTY_VIEWPORT_FILL:
            return viewportFill == propertyValue;
        default: 
            return super.isPropertyState(propertyIndex, propertyValue);
        }
    }

    /**
     * Checks the state of the float property value.
     *
     * @param propertyIndex the property index
     * @param propertyValue the computed value of the property.
     */
    protected boolean isFloatPropertyState(final int propertyIndex,
                                           final float propertyValue) {
        switch (propertyIndex) {
        case ViewportNode.PROPERTY_VIEWPORT_FILL_OPACITY:
            return viewportFillOpacity == propertyValue;
        default: 
            return super.isFloatPropertyState(propertyIndex, propertyValue);
        }
    }

    /**
     * Recomputes all inherited properties.
     */
    void recomputeInheritedProperties() {
        ModelNode p = ownerDocument;
        if (parent != null) {
            p = parent;
        }
        recomputePropertyState(PROPERTY_VIEWPORT_FILL, 
                               p.getPropertyState(PROPERTY_VIEWPORT_FILL));
        recomputeFloatPropertyState(PROPERTY_VIEWPORT_FILL_OPACITY,
                               p.getFloatPropertyState(PROPERTY_VIEWPORT_FILL_OPACITY));

	super.recomputeInheritedProperties();
    }

    /**
     * @param newViewportFill new viewport-fill color
     */
    public void setViewportFill(final PaintServer newViewportFill) {
        if (!isInherited(PROPERTY_VIEWPORT_FILL) && equal(newViewportFill, viewportFill)) {
            return;
        }

        modifyingNode();
        if (viewportFill != null) {
            viewportFill.dispose();
        }
        
        //renderingDirty();
	this.viewportFill = newViewportFill;
        setInheritedQuiet(PROPERTY_VIEWPORT_FILL, false);
        propagatePropertyState(PROPERTY_VIEWPORT_FILL, viewportFill);
        modifiedNode();
    }

    /**
     * @return this node's viewport fill property
     */
    public PaintServer getViewportFill() {
        return viewportFill;
    }

    /**
     * @return the current viewport-fill opacity property value.
     */
    public float getViewportFillOpacity() {
        return viewportFillOpacity;
    }

    /**
     * Setting the opacity property clears the inherited and color
     * relative states (they are set to false).
     *
     * @param newViewportFillOpacity the new viewport-fill-opacity property
     */
    public void setViewportFillOpacity(float newViewportFillOpacity) {
        if (!isInherited(PROPERTY_VIEWPORT_FILL_OPACITY) 
            && 
            newViewportFillOpacity == getViewportFillOpacity()) {
            return;
        }
        modifyingNode();
        if (newViewportFillOpacity > 1) {
            newViewportFillOpacity = 1;
        } else if (newViewportFillOpacity < 0) {
            newViewportFillOpacity = 0;
        }
        //renderingDirty();
        setInheritedQuiet(PROPERTY_VIEWPORT_FILL_OPACITY, false);
	viewportFillOpacity = newViewportFillOpacity;
        propagateFloatPropertyState(PROPERTY_VIEWPORT_FILL_OPACITY, 
                                    viewportFillOpacity);
        modifiedNode();
    }

    /**
     * Sets this node's parent but does not generate change 
     * events.
     * 
     * @param newParent the node's new parent node.
     */
    protected void setParentQuiet(final ModelNode newParent) {
        super.setParentQuiet(newParent);

        // Reset the image if this node has a different parent that
        // impacts the computation of the absoluteURI
        if (href != null && !href.equals(absoluteURI)) {
            willNeedImage();
        }
    }

    /**
     * @param newHref the new reference for this node's image.
     * @throws IllegalArgumentException if the href cannot be resolved
     *         into an absoluteURI and paintNeedsLoad is set to true.
     */
    public void setHref(final String newHref) {
        if (newHref == null) {
            throw new IllegalArgumentException();
        }

        if (equal(newHref, href)) {
            return;
        }

        modifyingNode();
        this.href = newHref;
        this.image = ownerDocument.getImageLoader().getLoadingImage();
        ownerDocument.getImageLoader().removeRasterImageConsumer(absoluteURI, 
                                                                 this);
        this.absoluteURI = null;
        this.imageLoaded = false;

        // Only declare the new URI to load if this node is
        // part of the document tree and if its href is set 
        // to a non-null value.
        if (isInDocumentTree() && href != null) {
            willNeedImage();
        }
        modifiedNode();
    }

    /**
     * Implementation. Declares to the ImageLoader that an image will be 
     * needed.
     */
    void willNeedImage() {
        imageLoaded = false;
        image = ownerDocument.getImageLoader().getLoadingImage();

        absoluteURI
            = ownerDocument.getImageLoader().resolveURI(href, getURIBase());
        
        if (absoluteURI == null) {
            if (paintNeedsLoad) {
                throw new IllegalArgumentException();
            } else {
                absoluteURI = BROKEN_URI;
                imageLoaded = true;
                image = ownerDocument.getImageLoader().getBrokenImage();
                return;
            }
        } else {
            ownerDocument.getImageLoader().addRasterImageConsumer(absoluteURI, 
                                                                  this);
            ownerDocument.getImageLoader().needsURI(absoluteURI);
        }
    }

    /**
     * @return this node's image reference. This should be a URI
     */
    public String getHref() {
        return href;
    }

    /**
     * @return this node's <tt>Image</tt> resource. May be null
     *         in case no uri has been set or if loading the 
     *         image data failed.
     */
    public RasterImage getImage() {
        return image;
    }

    /**
     * Sets the node's image if the computed absolute URI
     * is equal to the input uri.
     *
     * @param image the new node image. Should not be null.
     * @param uri the uri <code>image</code> corresponds to. This is
     *        passed in case the node's uri changed between a call
     *        to the <code>ImageLoader</code>'s <code>needsURI</code>
     *        and a call to <code>setImage</code>.
     */
    public void setImage(final RasterImage image, final String uri) {
        // This is done to handle situations where the href/parent
        // or other change occur while the image is loading.
        if (absoluteURI != null && absoluteURI.equals(uri)) {
            if (this.image != image) {
                modifyingNode();
                this.image = image;
                modifiedNode();
            }
        }
    }

    /**
     * @return the associated RunnableQueue. If not null, setImage should
     *         be called from within the runnable queue.
     */
    public RunnableQueue getUpdateQueue() {
        return ownerDocument.getUpdateQueue();
    }

    /**
     * @return the associated RunnableHandler, in case the associated 
     *         RunnableQueue is not null.
     */
    public RunnableHandler getRunnableHandler() {
        return ownerDocument.getRunnableHandler();
    }

    /**
     * @param newAlign one of StructureNode.ALIGN_NONE or 
     *        StructureNode.ALIGN_XMIDYMID
     */
    public void setAlign(final int newAlign) {
        if (newAlign == align) {
            return;
        }
        modifyingNode();
        this.align = newAlign;
        modifiedNode();
    }

    /**
     * @return the image's align property
     * @see StructureNode#ALIGN_NONE
     * @see StructureNode#ALIGN_XMIDYMID
     */
    public int getAlign() {
        return align;
    }


    /**
     * Paints this node into the input RenderGraphics. 
     *
     * @param rg this node is painted into this <tt>RenderGraphics</tt>
     * @param gp the <code>GraphicsProperties</code> controlling the operation's
     *        rendering
     * @param pt the <code>PaintTarget</code> for the paint operation.
     * @param txf the <code>Transform</code> from user space to device space for
     *        the paint operation.
     */
    void paintRendered(final RenderGraphics rg,
                       final GraphicsProperties gp,
                       final PaintTarget pt,
                       final Transform tx) {
        if (!gp.getVisibility()) {
            return;
        }

        rg.setTransform(tx);
	rg.setOpacity(getOpacity());
	if (viewportFill != null) {
	    // Set fill and opacity values to viewport-fill and viewport-fill-opacity values,
	    // so we can use fillRect() to fill the viewport
            rg.setFill(viewportFill);
            rg.setFillOpacity(viewportFillOpacity);

            // Fill viewport before drawing image
	    //
            rg.fillRect(x, y, width, height, 0, 0);
        }

        if (!imageLoaded) {
            loadImage();
        }

        //
        // Scale the image so that it fits into width/height
        // and is centered
        //
        int iw = image.getWidth();
        int ih = image.getHeight();

        if (align == StructureNode.ALIGN_NONE) {
            rg.drawImage(image, x, y, width, height);
        } else {
            float ws = width / iw;
            float hs = height / ih;
            float is = ws;
            if (hs < ws) {
                is = hs;
            }

            float oh = ih * is;
            float ow = iw * is;
            float dx = (width - ow) / 2;
            float dy = (height - oh) / 2;

            rg.drawImage(image, (x + dx), (y + dy), ow, oh);
        }
    }
    
    /**
     * Implementation. Loads the image through the ownerDocument's 
     * ImageLoader. If this image's paintNeedsLoad is true, this
     * will block until the image data has been retrieved. Otherwise,
     * a request to asynchronously load the image is placed.
     */
    public void loadImage() {
        // Note that if absoluteURI is null, it is because either href is null
        // or this node is not hooked into the tree. In these cases, we stick
        // to the loadingImage
        if (absoluteURI != null) {
            if (paintNeedsLoad) {
                image = ownerDocument.getImageLoader()
                    .getImageAndWait(absoluteURI);
                if (image == ownerDocument.getImageLoader().getBrokenImage()) {
                    throw new IllegalStateException();
                }
            } else {
                ownerDocument.getImageLoader().getImageLater(absoluteURI, this);
            }
        }
        imageLoaded = true;
    }

    /**
     * An <code>ImageNode</code> has something to render 
     *
     * @return true
     */
    public boolean hasNodeRendering() {
        return true;
    }

    /**
     * Returns true if this node is hit by the input point. The input point
     * is in viewport space. 
     *  
     * @param pt the x/y coordinate. Should never be null and be
     *        of size two. If not, the behavior is unspecified.
     *        The x/y coordinate is in viewport space.
     *
     * @return true if the node is hit by the input point. 
     * @see #nodeHitAt
     */
    protected boolean isHitVP(float[] pt) {
        if (!getVisibility()) {
                return false;
        }
        getInverseTransformState().transformPoint(pt, ownerDocument.upt);
        pt = ownerDocument.upt;
        return (pt[0] > x && pt[0] < (x + width)
                && pt[1] > y && pt[1] < (y + height));
    }

    /**
     * Returns true if the proxy node is hit by the input point. The input point
     * is in viewport space.
     *  
     * @param pt the x/y coordinate. Should never be null and be
     *        of size two. If not, the behavior is unspecified.
     *        The x/y coordinate is in viewport space.
     * @param proxy the tested <code>ElementNodeProxy</code>.
     * @return true if the node is hit by the input point. 
     * @see #nodeHitAt
     */
    protected boolean isProxyHitVP(float[] pt, 
                                   AbstractRenderingNodeProxy proxy) {
        proxy.getInverseTransformState().transformPoint(pt, ownerDocument.upt);
        pt = ownerDocument.upt;
        return (pt[0] > x && pt[0] < (x + width)
                && pt[1] > y && pt[1] < (y + height));
    }

    /**
     * Supported traits: x, y, width, height, viewport-fill, viewport-fill-opacity
     *
     * @param traitName the name of the trait which the element may support.
     * @return true if this element supports the given trait in one of the
     *         trait accessor methods.
     */
    boolean supportsTrait(final String traitName) {
        if (SVGConstants.SVG_X_ATTRIBUTE == traitName
            ||
            SVGConstants.SVG_Y_ATTRIBUTE == traitName
            ||
            SVGConstants.SVG_WIDTH_ATTRIBUTE == traitName
            ||
            SVGConstants.SVG_HEIGHT_ATTRIBUTE == traitName
            ||
            SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE == traitName
            ||
            SVGConstants.SVG_VIEWPORT_FILL_OPACITY_ATTRIBUTE == traitName
            ||
            SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE == traitName) {
            return true;
        } else {
            return super.supportsTrait(traitName);
        }
    }

    /**
     * @return an array of traits that are required by this element.
     */
    public String[] getRequiredTraits() {
        return REQUIRED_TRAITS;
    }

    /**
     * @return an array of namespaceURI, localName trait pairs required by
     *         this element.
     */
    public String[][] getRequiredTraitsNS() {
        return REQUIRED_TRAITS_NS;
    }

    /**
     * Supported traits: viewport-fill, viewport-fill-opacity
     *
     * @param name the requested trait name (e.g., "viewport-fill-opacity").
     * @return the trait's value, as a string (e.g., "1.0").
     *
     * @throws DOMException with error code NOT_SUPPORTED_ERROR if the requested
     * trait is not supported on this element or null.
     * @throws DOMException with error code TYPE_MISMATCH_ERR if requested
     * trait's computed value cannot be converted to a String (SVG Tiny only).
     */
    String getSpecifiedTraitImpl(final String name)
        throws DOMException {
        if ((SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE == name
             &&
             isInherited(PROPERTY_VIEWPORT_FILL))
            ||
            (SVGConstants.SVG_VIEWPORT_FILL_OPACITY_ATTRIBUTE == name
             &&
             isInherited(PROPERTY_VIEWPORT_FILL_OPACITY))) {
            return SVGConstants.CSS_INHERIT_VALUE;
        } else {
            return super.getSpecifiedTraitImpl(name);
        }
    }

    /**
     * Supported traits: xlink:href
     *
     * @param namespaceURI the trait's namespace.
     * @param traitName the name of the trait which the element may support.
     * @return true if this element supports the given trait in one of the
     *         trait accessor methods.
     */
    boolean supportsTraitNS(final String namespaceURI,
                            final String traitName) {
        if (SVGConstants.XLINK_NAMESPACE_URI == namespaceURI
            &&
            SVGConstants.SVG_HREF_ATTRIBUTE == traitName) {
            return true;
        } else {
            return super.supportsTraitNS(namespaceURI, traitName);
        }
    }

    /**
     * ImageNode handles x, y, width, height, viewport-fill,
     * and viewport-fill-opacity traits.
     * Other traits are handled by the super class.
     *
     * @param name the requested trait's name.
     * @return the requested trait's value.
     *
     * @throws DOMException with error code NOT_SUPPORTED_ERROR if the requested
     * trait is not supported on this element or null.
     * @throws DOMException with error code TYPE_MISMATCH_ERR if requested
     * trait's computed value cannot be converted to a String (SVG Tiny only).
     */
    public String getTraitImpl(final String name)
        throws DOMException {
        if (SVGConstants.SVG_X_ATTRIBUTE == name) {
            return Float.toString(getX());
        } else if (SVGConstants.SVG_Y_ATTRIBUTE == name) {
            return Float.toString(getY());
        } else if (SVGConstants.SVG_WIDTH_ATTRIBUTE == name) {
            return Float.toString(getWidth());
        } else if (SVGConstants.SVG_HEIGHT_ATTRIBUTE == name) {
            return Float.toString(getHeight());
        } else if (SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE == name) {
            return alignToStringTrait(align);
        } else if (SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE == name) {
            return toString(getViewportFill());
        } else if (SVGConstants.SVG_VIEWPORT_FILL_OPACITY_ATTRIBUTE == name) {
            return Float.toString(getViewportFillOpacity());
        } else {
            return super.getTraitImpl(name);
        }
    }

    /**
     * ImageNode handles the xlink href attribute
     *
     * @param namespaceURI the requested trait's namespace URI.
     * @param name the requested trait's local name (i.e., un-prefixed, as 
     *        "href")
     * @return the requested trait's string value.
     *
     * @throws DOMException with error code NOT_SUPPORTED_ERROR if the requested
     *         trait is not supported on this element or null.
     * @throws DOMException with error code TYPE_MISMATCH_ERR if requested
     *         trait's computed value cannot be converted to a String (SVG Tiny
     *         only).
     * @throws SecurityException if the application does not have the necessary
     *         privilege rights to access this (SVG) content.
     */
    String getTraitNSImpl(String namespaceURI, String name)
        throws DOMException {
        if (SVGConstants.XLINK_NAMESPACE_URI == namespaceURI
            &&
            SVGConstants.SVG_HREF_ATTRIBUTE == name) {
            if (href == null) {
                return "";
            } 
            return href;
        } else {
            return super.getTraitNSImpl(namespaceURI, name);
        }
    }

    /**
     * ImageNode handles x, y, width, height, viewport-fill,
     * and viewport-fill-opacity traits.
     * Other attributes are handled by the super class.
     *
     * @param name the requested trait name.
     * @param the requested trait's floating point value.
     *
     * @throws DOMException with error code NOT_SUPPORTED_ERROR if the requested
     * trait is not supported on this element or null.
     * @throws DOMException with error code TYPE_MISMATCH_ERR if requested
     * trait's computed value cannot be converted to a float
     * @throws SecurityException if the application does not have the necessary
     * privilege rights to access this (SVG) content.
     */
    float getFloatTraitImpl(final String name)
        throws DOMException {
        if (SVGConstants.SVG_X_ATTRIBUTE == name) {
            return getX();
        } else if (SVGConstants.SVG_Y_ATTRIBUTE == name) {
            return getY();
        } else if (SVGConstants.SVG_WIDTH_ATTRIBUTE == name) {
            return getWidth();
        } else if (SVGConstants.SVG_HEIGHT_ATTRIBUTE == name) {
            return getHeight();
        } else if (SVGConstants.SVG_VIEWPORT_FILL_OPACITY_ATTRIBUTE == name) {
            return getViewportFillOpacity();
        } else {
            return super.getFloatTraitImpl(name);
        }
    }

    /**
     * Supported color traits: viewport-fill
     *
     * @param name the requested trait's name.
     * @return the requested trait's value, as an <code>SVGRGBColor</code>.
     *
     * @throws DOMException with error code NOT_SUPPORTED_ERROR if the requested
     * trait is not supported on this element or null.
     * @throws DOMException with error code TYPE_MISMATCH_ERR if requested
     * trait's computed value cannot be converted to {@link
     * org.w3c.dom.svg.SVGRGBColor SVGRGBColor}
     * @throws SecurityException if the application does not have the necessary
     * privilege rights to access this (SVG) content.
     */
    SVGRGBColor getRGBColorTraitImpl(String name)
        throws DOMException {
        if (SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE.equals(name)) {
            return toSVGRGBColor(SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE,
                                 getViewportFill());
        } else {
            return super.getRGBColorTraitImpl(name);
        }
    }

    /**
     * @param traitName the trait name.
     */
    TraitAnim createTraitAnimImpl(final String traitName) {
        if (SVGConstants.SVG_X_ATTRIBUTE == traitName
            ||
            SVGConstants.SVG_Y_ATTRIBUTE == traitName
            ||
            SVGConstants.SVG_WIDTH_ATTRIBUTE == traitName
            ||
            SVGConstants.SVG_VIEWPORT_FILL_OPACITY_ATTRIBUTE == traitName
	    ||
            SVGConstants.SVG_HEIGHT_ATTRIBUTE == traitName) {
            return new FloatTraitAnim(this, traitName, TRAIT_TYPE_FLOAT);
        } else if (SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE 
                == traitName) {
            return new StringTraitAnim(this, NULL_NS, traitName);
        } else if (SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE == traitName) {
           return new FloatTraitAnim(this, traitName, TRAIT_TYPE_SVG_RGB_COLOR); 
        } else {
            return super.createTraitAnimImpl(traitName);
        }
    }

    /**
     * @param traitName the trait name.
     * @param traitNamespace the trait's namespace. Should not be null.
     */
    TraitAnim createTraitAnimNSImpl(final String traitNamespace, 
                                    final String traitName) {
        if (traitNamespace == SVGConstants.XLINK_NAMESPACE_URI
            &&
            traitName == SVGConstants.SVG_HREF_ATTRIBUTE) {
            return new StringTraitAnim(this, traitNamespace, traitName);
        }

        return super.createTraitAnimNSImpl(traitNamespace, traitName);
    }

    /**
     * Set the trait value as float.
     *
     * @param name the trait's name.
     * @param value the trait's value.
     *
     * @throws DOMException with error code NOT_SUPPORTED_ERROR if the requested
     * trait is not supported on this element.
     * @throws DOMException with error code TYPE_MISMATCH_ERR if the requested
     * trait's value cannot be specified as a float
     * @throws DOMException with error code INVALID_ACCESS_ERR if the input
     * value is an invalid value for the given trait.
     */
    void setFloatArrayTrait(final String name, final float[][] value)
        throws DOMException {
        if (SVGConstants.SVG_X_ATTRIBUTE == name) {
            setX(value[0][0]);
        } else if (SVGConstants.SVG_Y_ATTRIBUTE == name) {
            setY(value[0][0]);
        } else if (SVGConstants.SVG_WIDTH_ATTRIBUTE == name) {
            checkPositive(name, value[0][0]);
            setWidth(value[0][0]);
        } else if (SVGConstants.SVG_HEIGHT_ATTRIBUTE == name) {
            checkPositive(name, value[0][0]);
            setHeight(value[0][0]);
        } else if (SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE == name) {
            if (value == null || value.length == 0) {
                setViewportFill(null);
            } else {
                setViewportFill(toRGB(name, value));
            }
        } else if (SVGConstants.SVG_VIEWPORT_FILL_OPACITY_ATTRIBUTE == name) {
            setViewportFillOpacity(value[0][0]);
        } else {
            super.setFloatArrayTrait(name, value);
        }
    }

    /**
     * Validates the input trait value.
     *
     * @param traitName the name of the trait to be validated.
     * @param value the value to be validated
     * @param reqNamespaceURI the namespace of the element requesting 
     *        validation.
     * @param reqLocalName the local name of the element requesting validation.
     * @param reqTraitNamespace the namespace of the trait which has the values
     *        value on the requesting element.
     * @param reqTraitName the name of the trait which has the values value on 
     *        the requesting element.
     * @throws DOMException with error code INVALID_ACCESS_ERR if the input
     *         value is incompatible with the given trait.
     */
    public float[][] validateFloatArrayTrait(
            final String traitName,
            final String value,
            final String reqNamespaceURI,
            final String reqLocalName,
            final String reqTraitNamespace,
            final String reqTraitName) throws DOMException {
        if (SVGConstants.SVG_X_ATTRIBUTE == traitName
            ||
            SVGConstants.SVG_Y_ATTRIBUTE == traitName) {
            return new float[][]{{parseFloatTrait(traitName, value)}};
        } else if (SVGConstants.SVG_WIDTH_ATTRIBUTE == traitName
                   ||
                   SVGConstants.SVG_HEIGHT_ATTRIBUTE == traitName) {
            return new float[][] {{parsePositiveFloatTrait(traitName, value)}};
        } else if (SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE == traitName) {
            RGB color = ViewportProperties.INITIAL_VIEWPORT_FILL;
            if (SVGConstants.CSS_INHERIT_VALUE.equals(value)) {
                color = (RGB) getInheritedPropertyState(PROPERTY_VIEWPORT_FILL);
            } else {
                color = parseColorTrait
                    (SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE, value);
            }

	    // A value of "none" is not checked for, even though
	    // it is a valid viewport-fill value, it is not considered
	    // valid for animations. So here color will compute to "null"
	    // for a viewport-fill of "none"
	    //
            if (color == null) {
                throw illegalTraitValue(traitName, value);
            }
            return new float[][] {
                        {color.getRed(), color.getGreen(), color.getBlue()}
                    };
        } else if (SVGConstants.SVG_VIEWPORT_FILL_OPACITY_ATTRIBUTE == traitName) {
            float v = ViewportNode.INITIAL_VIEWPORT_FILL_OPACITY;
            if (SVGConstants.CSS_INHERIT_VALUE.equals(value)) {
                if (parent != null) {
                    v = getInheritedFloatPropertyState(PROPERTY_VIEWPORT_FILL_OPACITY);
                }
            } else {
                v = parseFloatTrait(traitName, value);
                if (v < 0) {
                    v = 0;
                } else if (v > 1) {
                    v = 1;
                }
            }
            return new float[][] {{v}};
        } else {
            return super.validateFloatArrayTrait(traitName,
                                                 value,
                                                 reqNamespaceURI,
                                                 reqLocalName,
                                                 reqTraitNamespace,
                                                 reqTraitName);
        }
    }

    /**
     * Validates the input trait value.
     *
     * @param namespaceURI the trait's namespace URI.
     * @param traitName the name of the trait to be validated.
     * @param value the value to be validated
     * @param reqNamespaceURI the namespace of the element requesting 
     *        validation.
     * @param reqLocalName the local name of the element requesting validation.
     * @param reqTraitNamespace the namespace of the trait which has the values
     *        value on the requesting element.
     * @param reqTraitName the name of the trait which has the values value on 
     *        the requesting element.
     * @throws DOMException with error code INVALID_ACCESS_ERR if the input
     *         value is incompatible with the given trait.
     */
    String validateTraitNS(final String namespaceURI,
                           final String traitName,
                           final String value,
                           final String reqNamespaceURI,
                           final String reqLocalName,
                           final String reqTraitNamespace,
                           final String reqTraitName) throws DOMException {
        if (namespaceURI != null) {
            return super.validateTraitNS(namespaceURI,
                                         traitName,
                                         value,
                                         reqNamespaceURI,
                                         reqLocalName,
                                         reqTraitNamespace,
                                         reqTraitName);
        }

        if (SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE == traitName
            ||
            SVGConstants.SVG_VIEWPORT_FILL_OPACITY_ATTRIBUTE == traitName) {
            throw unsupportedTraitType(traitName, TRAIT_TYPE_FLOAT);
        }

        if (SVGConstants.SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE
                .equals(traitName)) {
            if (!SVGConstants
                .SVG_SVG_PRESERVE_ASPECT_RATIO_DEFAULT_VALUE.equals(value)
                &&
                !SVGConstants.SVG_NONE_VALUE.equals(value)) {
                throw illegalTraitValue(traitName, value);
            }
            return value;
        } else {
            return super.validateTraitNS(namespaceURI,
                                         traitName,
                                         value,
                                         reqNamespaceURI,
                                         reqLocalName,
                                         reqTraitNamespace,
                                         reqTraitName);
        }
    }

    /**
     * ImageNode handles x, y, rx, ry, width, height,
     * viewport-fill and viewport-fill-opacity traits.
     * Other traits are handled by the super class.
     *
     * @param name the trait's name.
     * @param value the new trait string value.
     *
     * @throws DOMException with error code NOT_SUPPORTED_ERROR if the requested
     * trait is not supported on this element or null.
     * @throws DOMException with error code TYPE_MISMATCH_ERR if the requested
     * trait's value cannot be specified as a String
     * @throws DOMException with error code INVALID_ACCESS_ERR if the input
     * value is an invalid value for the given trait or null.
     * @throws DOMException with error code NO_MODIFICATION_ALLOWED_ERR: if
     * attempt is made to change readonly trait.
     */
    public void setTraitImpl(final String name, final String value)
        throws DOMException {
        if (SVGConstants.SVG_X_ATTRIBUTE == name) {
            setX(parseFloatTrait(name, value));
        } else if (SVGConstants.SVG_Y_ATTRIBUTE == name) {
            setY(parseFloatTrait(name, value));
        } else if (SVGConstants.SVG_WIDTH_ATTRIBUTE == name) {
            setWidth(parsePositiveFloatTrait(name, value));
        } else if (SVGConstants.SVG_HEIGHT_ATTRIBUTE == name) {
            setHeight(parsePositiveFloatTrait(name, value));
        } else if (SVGConstants
                   .SVG_PRESERVE_ASPECT_RATIO_ATTRIBUTE.equals(name)) {
            if (SVGConstants
                .SVG_SVG_PRESERVE_ASPECT_RATIO_DEFAULT_VALUE.equals(value)) {
                setAlign(StructureNode.ALIGN_XMIDYMID);
            } else if (SVGConstants.SVG_NONE_VALUE.equals(value)) {
                setAlign(StructureNode.ALIGN_NONE);
            } else {
                throw illegalTraitValue(name, value);
            }
        } else if (SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE == name) {

            // ========================= viewport-fill =========================== //

            if (SVGConstants.CSS_INHERIT_VALUE.equals(value)) {
                setInherited(PROPERTY_VIEWPORT_FILL, true);
            } else if (SVGConstants.CSS_NONE_VALUE.equals(value)) {
                setViewportFill(null);
            } else {
                PaintServer vFill = parsePaintTrait
                    (SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE, this, value);
                if (vFill != null) {
                    setViewportFill(vFill);
                }
            }
        } else if (SVGConstants.SVG_VIEWPORT_FILL_OPACITY_ATTRIBUTE == name) {

            // ====================== viewport-fill-opacity ======================= //

            if (SVGConstants.CSS_INHERIT_VALUE.equals(value)) {
                setFloatInherited(PROPERTY_VIEWPORT_FILL_OPACITY, true);
            } else {
                setViewportFillOpacity(parseFloatTrait(name, value));
            }
        } else {
            super.setTraitImpl(name, value);
        }
    }

    /**
     * ImageNode supports the xlink:href trait.
     *
     * @param namespaceURI the trait's namespace.
     * @param name the trait's local name (un-prefixed, e.g., "href");
     * @param value the new trait value (e.g., "http://www.sun.com/mypng.png")
     *
     * @throws DOMException with error code NOT_SUPPORTED_ERROR if the requested
     * trait is not supported on this element or null.
     * @throws DOMException with error code TYPE_MISMATCH_ERR if the requested
     * trait's value cannot be specified as a String
     * @throws DOMException with error code INVALID_ACCESS_ERR if the input
     * value is an invalid value for the given trait or null.
     * @throws DOMException with error code NO_MODIFICATION_ALLOWED_ERR: if
     * attempt is made to change readonly trait.
     * @throws SecurityException if the application does not have the necessary
     * privilege rights to access this (SVG) content.
     */
    public void setTraitNSImpl(final String namespaceURI, 
                               final String name, 
                               final String value)
        throws DOMException {
        try {
            if (SVGConstants.XLINK_NAMESPACE_URI == namespaceURI
                &&
                SVGConstants.SVG_HREF_ATTRIBUTE == name) {
                setHref(value);
            } else {
                super.setTraitNSImpl(namespaceURI, name, value);
            }
        } catch (IllegalArgumentException iae) {
            throw illegalTraitValue(name, value);
        }
    }

    /**
     * ImageNode handles x, y, rx, ry, width, height,
     * viewport-fill and viewport-fill-opacity traits.
     * Other traits are handled by the super class.
     *
     * @param name the trait's name.
     * @param value the new trait's floating point value.
     *
     * @throws DOMException with error code NOT_SUPPORTED_ERROR if the requested
     * trait is not supported on this element.
     * @throws DOMException with error code TYPE_MISMATCH_ERR if the requested
     * trait's value cannot be specified as a float
     * @throws DOMException with error code INVALID_ACCESS_ERR if the input
     * value is an invalid value for the given trait.
     * @throws SecurityException if the application does not have the necessary
     * privilege rights to access this (SVG) content.
     */
    public void setFloatTraitImpl(final String name, final float value)
        throws DOMException {
        try {
            if (SVGConstants.SVG_X_ATTRIBUTE == name) {
                setX(value);
            } else if (SVGConstants.SVG_Y_ATTRIBUTE == name) {
                setY(value);
            } else if (SVGConstants.SVG_WIDTH_ATTRIBUTE == name) {
                setWidth(value);
            } else if (SVGConstants.SVG_HEIGHT_ATTRIBUTE == name) {
                setHeight(value);
            } else if (SVGConstants.SVG_VIEWPORT_FILL_OPACITY_ATTRIBUTE == name) {
                setViewportFillOpacity(value);
            } else {
                super.setFloatTraitImpl(name, value);
            }
        } catch (IllegalArgumentException iae) {
            throw illegalTraitValue(name, Float.toString(value));
        }
    }

    /**
     * @param name the name of the trait to convert.
     * @param value the float trait value to convert.
     */
    String toStringTrait(final String name, final float[][] value) {
        if (SVGConstants.SVG_X_ATTRIBUTE == name
            ||
            SVGConstants.SVG_Y_ATTRIBUTE == name
            ||
            SVGConstants.SVG_WIDTH_ATTRIBUTE == name
            ||
            SVGConstants.SVG_VIEWPORT_FILL_OPACITY_ATTRIBUTE == name
            ||
            SVGConstants.SVG_HEIGHT_ATTRIBUTE == name) {
            return Float.toString(value[0][0]);
        } else if (SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE == name) {
	    // Unlike SVG_FILL_ATTRIBUTE, SVG_VIEWPORT_FILL_ATTRIBUTE can be
	    // null.
	    if (value == null || value.length == 0) {
                return SVGConstants.CSS_NONE_VALUE;
            } else {
                return toRGBString(name, value);
            }
        } else {
            return super.toStringTrait(name, value);
        }
    }

    /**
     * Set the trait value as {@link org.w3c.dom.svg.SVGRGBColor SVGRGBColor}.
     *
     * Supported color traits: viewport-fill
     *
     * @param name the name of the trait to set.
     * @param value the value of the trait to set.
     *
     * @throws DOMException with error code NOT_SUPPORTED_ERROR if the requested
     * trait is not supported on this element or null.
     * @throws DOMException with error code TYPE_MISMATCH_ERR if the requested
     * trait's value cannot be specified as an {@link
     * org.w3c.dom.svg.SVGRGBColor SVGRGBColor}
     * @throws DOMException with error code INVALID_ACCESS_ERR if the input
     * value is null.
     * @throws SecurityException if the application does not have the necessary
     * privilege rights to access this (SVG) content.
     */
    void setRGBColorTraitImpl(final String name, final SVGRGBColor color)
        throws DOMException {
        try {
            // We use .equals here because the name string may not have been
            // interned.
            if (SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE.equals(name)) {
                setViewportFill((RGB) color);
            } else {
                super.setRGBColorTraitImpl(name, color);
            } 
        } catch (IllegalArgumentException iae) {
            throw new DOMException(DOMException.INVALID_ACCESS_ERR, 
                                   iae.getMessage());
        }
    }
    
    /**
     * @param paintType the key provided by the PaintTarget when it subscribed 
     *        to associated PaintServer.
     * @param paintServer the PaintServer generating the update.
     */
    public void onPaintServerUpdate(final String paintType,
                                    final PaintServer paintServer) {
        if (paintType == SVGConstants.SVG_VIEWPORT_FILL_ATTRIBUTE) {
            setViewportFill(paintServer);
        } else {
            throw new Error();
        }
    }

    /**
     * @param bbox the bounding box to which this node's bounding box should be
     *        appended. That bounding box is in the target coordinate space. It 
     *        may be null, in which case this node should create a new one.
     * @param t the transform from the node coordinate system to the coordinate
     *        system into which the bounds should be computed.
     * @return the bounding box of this node, in the target coordinate space, 
     */
    Box addNodeBBox(Box bbox, 
                    final Transform t) {
        return addTransformedBBox(bbox, x, y, width, height, t);
    }
}
