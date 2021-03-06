/*
 Copyright (C) 2010 Nokia Corporation and/or its subsidiary(-ies)

 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Library General Public
 License as published by the Free Software Foundation; either
 version 2 of the License, or (at your option) any later version.

 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Library General Public License for more details.

 You should have received a copy of the GNU Library General Public License
 along with this library; see the file COPYING.LIB.  If not, write to
 the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 Boston, MA 02110-1301, USA.
 */

#include "config.h"

#if USE(COORDINATED_GRAPHICS)
#include "CoordinatedGraphicsLayer.h"

#include "BackingStore.h"
#include "CoordinatedTile.h"
#include "FloatQuad.h"
#include "Frame.h"
#include "FrameView.h"
#include "GraphicsContext.h"
#include "GraphicsLayer.h"
#include "LayerTreeCoordinatorProxyMessages.h"
#include "Page.h"
#include "TextureMapperPlatformLayer.h"
#include "WebPage.h"
#include <wtf/CurrentTime.h>
#include <wtf/HashMap.h>
#include <wtf/text/CString.h>

using namespace WebKit;

namespace WebCore {

static HashMap<WebLayerID, CoordinatedGraphicsLayer*>& layerByIDMap()
{
    static HashMap<WebLayerID, CoordinatedGraphicsLayer*> globalMap;
    return globalMap;
}

static WebLayerID toWebLayerID(GraphicsLayer* layer)
{
    return layer ? toCoordinatedGraphicsLayer(layer)->id() : 0;
}

void CoordinatedGraphicsLayer::didChangeLayerState()
{
    m_shouldSyncLayerState = true;
    if (client())
        client()->notifyFlushRequired(this);
}

void CoordinatedGraphicsLayer::didChangeAnimations()
{
    m_shouldSyncAnimations = true;
    if (client())
        client()->notifyFlushRequired(this);
}

void CoordinatedGraphicsLayer::didChangeChildren()
{
    m_shouldSyncChildren = true;
    if (client())
        client()->notifyFlushRequired(this);
}

#if ENABLE(CSS_FILTERS)
void CoordinatedGraphicsLayer::didChangeFilters()
{
    m_shouldSyncFilters = true;
    if (client())
        client()->notifyFlushRequired(this);
}
#endif

void CoordinatedGraphicsLayer::didChangeImageBacking()
{
    m_shouldSyncImageBacking = true;
    if (client())
        client()->notifyFlushRequired(this);
}

void CoordinatedGraphicsLayer::setShouldUpdateVisibleRect()
{
    m_shouldUpdateVisibleRect = true;
    for (size_t i = 0; i < children().size(); ++i)
        toCoordinatedGraphicsLayer(children()[i])->setShouldUpdateVisibleRect();
    if (replicaLayer())
        toCoordinatedGraphicsLayer(replicaLayer())->setShouldUpdateVisibleRect();
}

void CoordinatedGraphicsLayer::didChangeGeometry()
{
    didChangeLayerState();
    setShouldUpdateVisibleRect();
}

CoordinatedGraphicsLayer::CoordinatedGraphicsLayer(GraphicsLayerClient* client)
    : GraphicsLayer(client)
    , m_maskTarget(0)
    , m_inUpdateMode(false)
    , m_shouldUpdateVisibleRect(true)
    , m_shouldSyncLayerState(true)
    , m_shouldSyncChildren(true)
    , m_shouldSyncFilters(true)
    , m_shouldSyncImageBacking(true)
    , m_shouldSyncAnimations(true)
    , m_fixedToViewport(false)
    , m_canvasNeedsDisplay(false)
    , m_coordinator(0)
    , m_contentsScale(1)
    , m_compositedNativeImagePtr(0)
    , m_canvasPlatformLayer(0)
    , m_animationStartedTimer(this, &CoordinatedGraphicsLayer::animationStartedTimerFired)
{
    static WebLayerID nextLayerID = 1;
    m_id = nextLayerID++;
    layerByIDMap().add(id(), this);
}

CoordinatedGraphicsLayer::~CoordinatedGraphicsLayer()
{
    layerByIDMap().remove(id());

    if (m_coordinator) {
        purgeBackingStores();
        m_coordinator->detachLayer(this);
    }
    willBeDestroyed();
}

bool CoordinatedGraphicsLayer::setChildren(const Vector<GraphicsLayer*>& children)
{
    bool ok = GraphicsLayer::setChildren(children);
    if (!ok)
        return false;
    for (unsigned i = 0; i < children.size(); ++i) {
        CoordinatedGraphicsLayer* child = toCoordinatedGraphicsLayer(children[i]);
        child->didChangeLayerState();
    }
    didChangeChildren();
    return true;
}

void CoordinatedGraphicsLayer::addChild(GraphicsLayer* layer)
{
    GraphicsLayer::addChild(layer);
    toCoordinatedGraphicsLayer(layer)->didChangeLayerState();
    didChangeChildren();
}

void CoordinatedGraphicsLayer::addChildAtIndex(GraphicsLayer* layer, int index)
{
    GraphicsLayer::addChildAtIndex(layer, index);
    toCoordinatedGraphicsLayer(layer)->didChangeLayerState();
    didChangeChildren();
}

void CoordinatedGraphicsLayer::addChildAbove(GraphicsLayer* layer, GraphicsLayer* sibling)
{
    GraphicsLayer::addChildAbove(layer, sibling);
    toCoordinatedGraphicsLayer(layer)->didChangeLayerState();
    didChangeChildren();
}

void CoordinatedGraphicsLayer::addChildBelow(GraphicsLayer* layer, GraphicsLayer* sibling)
{
    GraphicsLayer::addChildBelow(layer, sibling);
    toCoordinatedGraphicsLayer(layer)->didChangeLayerState();
    didChangeChildren();
}

bool CoordinatedGraphicsLayer::replaceChild(GraphicsLayer* oldChild, GraphicsLayer* newChild)
{
    bool ok = GraphicsLayer::replaceChild(oldChild, newChild);
    if (!ok)
        return false;
    didChangeChildren();
    toCoordinatedGraphicsLayer(oldChild)->didChangeLayerState();
    toCoordinatedGraphicsLayer(newChild)->didChangeLayerState();
    return true;
}

void CoordinatedGraphicsLayer::removeFromParent()
{
    if (CoordinatedGraphicsLayer* parentLayer = toCoordinatedGraphicsLayer(parent()))
        parentLayer->didChangeChildren();
    GraphicsLayer::removeFromParent();

    didChangeLayerState();
}

void CoordinatedGraphicsLayer::setPosition(const FloatPoint& p)
{
    if (position() == p)
        return;

    GraphicsLayer::setPosition(p);
    didChangeGeometry();
}

void CoordinatedGraphicsLayer::setAnchorPoint(const FloatPoint3D& p)
{
    if (anchorPoint() == p)
        return;

    GraphicsLayer::setAnchorPoint(p);
    didChangeGeometry();
}

void CoordinatedGraphicsLayer::setSize(const FloatSize& size)
{
    if (this->size() == size)
        return;

    GraphicsLayer::setSize(size);
    setNeedsDisplay();
    if (maskLayer())
        maskLayer()->setSize(size);
    didChangeGeometry();
}

void CoordinatedGraphicsLayer::setTransform(const TransformationMatrix& t)
{
    if (transform() == t)
        return;

    GraphicsLayer::setTransform(t);
    didChangeGeometry();
}

void CoordinatedGraphicsLayer::setChildrenTransform(const TransformationMatrix& t)
{
    if (childrenTransform() == t)
        return;

    GraphicsLayer::setChildrenTransform(t);
    didChangeGeometry();
}

void CoordinatedGraphicsLayer::setPreserves3D(bool b)
{
    if (preserves3D() == b)
        return;

    GraphicsLayer::setPreserves3D(b);
    didChangeGeometry();
}

void CoordinatedGraphicsLayer::setMasksToBounds(bool b)
{
    if (masksToBounds() == b)
        return;
    GraphicsLayer::setMasksToBounds(b);
    didChangeGeometry();
}

void CoordinatedGraphicsLayer::setDrawsContent(bool b)
{
    if (drawsContent() == b)
        return;
    GraphicsLayer::setDrawsContent(b);

    didChangeLayerState();
}

void CoordinatedGraphicsLayer::setContentsVisible(bool b)
{
    if (contentsAreVisible() == b)
        return;
    GraphicsLayer::setContentsVisible(b);

    didChangeLayerState();
}

void CoordinatedGraphicsLayer::setContentsOpaque(bool b)
{
    if (contentsOpaque() == b)
        return;
    if (m_mainBackingStore)
        m_mainBackingStore->setSupportsAlpha(!b);
    GraphicsLayer::setContentsOpaque(b);
    didChangeLayerState();
}

void CoordinatedGraphicsLayer::setBackfaceVisibility(bool b)
{
    if (backfaceVisibility() == b)
        return;

    GraphicsLayer::setBackfaceVisibility(b);
    didChangeLayerState();
}

void CoordinatedGraphicsLayer::setOpacity(float opacity)
{
    if (this->opacity() == opacity)
        return;

    GraphicsLayer::setOpacity(opacity);
    didChangeLayerState();
}

void CoordinatedGraphicsLayer::setContentsRect(const IntRect& r)
{
    if (contentsRect() == r)
        return;

    GraphicsLayer::setContentsRect(r);
    didChangeLayerState();
}

void CoordinatedGraphicsLayer::setContentsNeedsDisplay()
{
    m_canvasNeedsDisplay = true;
    if (client())
        client()->notifyFlushRequired(this);
}

void CoordinatedGraphicsLayer::setContentsToCanvas(PlatformLayer* platformLayer)
{
    m_canvasPlatformLayer = platformLayer;
    m_canvasNeedsDisplay = true;
    if (client())
        client()->notifyFlushRequired(this);
}

#if ENABLE(CSS_FILTERS)
bool CoordinatedGraphicsLayer::setFilters(const FilterOperations& newFilters)
{
    if (filters() == newFilters)
        return true;
    didChangeFilters();
    return GraphicsLayer::setFilters(newFilters);
}
#endif


void CoordinatedGraphicsLayer::setContentsToImage(Image* image)
{
    if (image) {
        // This code makes the assumption that pointer equality on a NativeImagePtr is a valid way to tell if the image is changed.
        // This assumption is true in Qt, GTK and EFL.
        NativeImagePtr newNativeImagePtr = image->nativeImageForCurrentFrame();
        if (!newNativeImagePtr)
            return;

        if (newNativeImagePtr == m_compositedNativeImagePtr)
            return;

        m_compositedImage = image;
        m_compositedNativeImagePtr = newNativeImagePtr;
    } else {
        m_compositedImage = 0;
        m_compositedNativeImagePtr = 0;
    }

    GraphicsLayer::setContentsToImage(image);
    didChangeImageBacking();
}

void CoordinatedGraphicsLayer::setMaskLayer(GraphicsLayer* layer)
{
    if (layer == maskLayer())
        return;

    GraphicsLayer::setMaskLayer(layer);

    if (!layer)
        return;

    layer->setSize(size());
    CoordinatedGraphicsLayer* CoordinatedGraphicsLayer = toCoordinatedGraphicsLayer(layer);
    CoordinatedGraphicsLayer->setMaskTarget(this);
    CoordinatedGraphicsLayer->didChangeLayerState();
    didChangeLayerState();

}

bool CoordinatedGraphicsLayer::shouldDirectlyCompositeImage(Image* image) const
{
    if (!image || !image->isBitmapImage())
        return false;

    enum { kMaxDimenstionForDirectCompositing = 2000 };
    if (image->width() > kMaxDimenstionForDirectCompositing || image->height() > kMaxDimenstionForDirectCompositing)
        return false;

    return true;
}

void CoordinatedGraphicsLayer::setReplicatedByLayer(GraphicsLayer* layer)
{
    if (layer == replicaLayer())
        return;

    GraphicsLayer::setReplicatedByLayer(layer);
    didChangeLayerState();
}

void CoordinatedGraphicsLayer::setNeedsDisplay()
{
    setNeedsDisplayInRect(IntRect(IntPoint::zero(), IntSize(size().width(), size().height())));
}

void CoordinatedGraphicsLayer::setNeedsDisplayInRect(const FloatRect& rect)
{
    if (m_mainBackingStore)
        m_mainBackingStore->invalidate(IntRect(rect));
    didChangeLayerState();
}

WebLayerID CoordinatedGraphicsLayer::id() const
{
    return m_id;
}

void CoordinatedGraphicsLayer::flushCompositingState(const FloatRect& rect)
{
    if (CoordinatedGraphicsLayer* mask = toCoordinatedGraphicsLayer(maskLayer()))
        mask->flushCompositingStateForThisLayerOnly();

    if (CoordinatedGraphicsLayer* replica = toCoordinatedGraphicsLayer(replicaLayer()))
        replica->flushCompositingStateForThisLayerOnly();

    m_coordinator->syncFixedLayers();

    flushCompositingStateForThisLayerOnly();

    for (size_t i = 0; i < children().size(); ++i)
        children()[i]->flushCompositingState(rect);
}

CoordinatedGraphicsLayer* toCoordinatedGraphicsLayer(GraphicsLayer* layer)
{
    return static_cast<CoordinatedGraphicsLayer*>(layer);
}

void CoordinatedGraphicsLayer::syncChildren()
{
    if (!m_shouldSyncChildren)
        return;
    m_shouldSyncChildren = false;
    Vector<WebLayerID> childIDs;
    for (size_t i = 0; i < children().size(); ++i)
        childIDs.append(toWebLayerID(children()[i]));

    m_coordinator->syncLayerChildren(m_id, childIDs);
}

#if ENABLE(CSS_FILTERS)
void CoordinatedGraphicsLayer::syncFilters()
{
    if (!m_shouldSyncFilters)
        return;
    m_shouldSyncFilters = false;
    m_coordinator->syncLayerFilters(m_id, filters());
}
#endif

void CoordinatedGraphicsLayer::syncImageBacking()
{
    if (!m_shouldSyncImageBacking)
        return;
    m_shouldSyncImageBacking = false;

    releaseImageBackingIfNeeded();

    if (m_compositedNativeImagePtr) {
        ASSERT(!m_mainBackingStore);
        ASSERT(m_compositedImage);

        m_layerInfo.imageBackingStoreID = m_coordinator->adoptImageBackingStore(m_compositedImage.get());
    }

    // This method changes m_layerInfo.imageBackingStoreID.
    didChangeLayerState();
}

void CoordinatedGraphicsLayer::syncLayerState()
{
    if (!m_shouldSyncLayerState)
        return;
    m_shouldSyncLayerState = false;
    m_layerInfo.fixedToViewport = fixedToViewport();

    m_layerInfo.anchorPoint = anchorPoint();
    m_layerInfo.backfaceVisible = backfaceVisibility();
    m_layerInfo.childrenTransform = childrenTransform();
    m_layerInfo.contentsOpaque = contentsOpaque();
    m_layerInfo.contentsRect = contentsRect();
    m_layerInfo.drawsContent = drawsContent();
    m_layerInfo.contentsVisible = contentsAreVisible();
    m_layerInfo.mask = toWebLayerID(maskLayer());
    m_layerInfo.masksToBounds = masksToBounds();
    m_layerInfo.opacity = opacity();
    m_layerInfo.pos = position();
    m_layerInfo.preserves3D = preserves3D();
    m_layerInfo.replica = toWebLayerID(replicaLayer());
    m_layerInfo.size = size();
    m_layerInfo.transform = transform();
    m_coordinator->syncLayerState(m_id, m_layerInfo);
}

void CoordinatedGraphicsLayer::syncAnimations()
{
    if (!m_shouldSyncAnimations)
        return;

    m_shouldSyncAnimations = false;

    m_coordinator->setLayerAnimations(m_id, m_animations);
}

void CoordinatedGraphicsLayer::syncCanvas()
{
    if (!m_canvasNeedsDisplay)
        return;

    if (!m_canvasPlatformLayer)
        return;

#if USE(GRAPHICS_SURFACE)
    uint32_t frontBuffer = m_canvasPlatformLayer->copyToGraphicsSurface();
    GraphicsSurfaceToken token = m_canvasPlatformLayer->graphicsSurfaceToken();

    m_coordinator->syncCanvas(m_id, IntSize(size().width(), size().height()), token, frontBuffer);
#endif
    m_canvasNeedsDisplay = false;
}

void CoordinatedGraphicsLayer::flushCompositingStateForThisLayerOnly()
{
    syncImageBacking();
    syncLayerState();
    syncAnimations();
    computeTransformedVisibleRect();
    syncChildren();
#if ENABLE(CSS_FILTERS)
    syncFilters();
#endif
    updateContentBuffers();
    syncCanvas();
}

void CoordinatedGraphicsLayer::releaseImageBackingIfNeeded()
{
    if (!m_layerInfo.imageBackingStoreID)
        return;

    ASSERT(m_coordinator);
    m_coordinator->releaseImageBackingStore(m_layerInfo.imageBackingStoreID);
    m_layerInfo.imageBackingStoreID = 0;
}

void CoordinatedGraphicsLayer::tiledBackingStorePaintBegin()
{
}

void CoordinatedGraphicsLayer::setRootLayer(bool isRoot)
{
    m_layerInfo.isRootLayer = isRoot;
    didChangeLayerState();
}

void CoordinatedGraphicsLayer::setVisibleContentRectTrajectoryVector(const FloatPoint& trajectoryVector)
{
    if (m_mainBackingStore)
        m_mainBackingStore->coverWithTilesIfNeeded(trajectoryVector);
}

void CoordinatedGraphicsLayer::setContentsScale(float scale)
{
    m_contentsScale = scale;
    adjustContentsScale();
}

float CoordinatedGraphicsLayer::effectiveContentsScale()
{
    return shouldUseTiledBackingStore() ? m_contentsScale : 1;
}

void CoordinatedGraphicsLayer::adjustContentsScale()
{
    if (!drawsContent() || !contentsAreVisible() || m_size.isEmpty())
        return;

    if (!m_mainBackingStore || m_mainBackingStore->contentsScale() == effectiveContentsScale())
        return;

    // Between creating the new backing store and painting the content,
    // we do not want to drop the previous one as that might result in
    // briefly seeing flickering as the old tiles may be dropped before
    // something replaces them.
    m_previousBackingStore = m_mainBackingStore.release();

    // No reason to save the previous backing store for non-visible areas.
    m_previousBackingStore->removeAllNonVisibleTiles();

    createBackingStore();
}

void CoordinatedGraphicsLayer::createBackingStore()
{
    m_mainBackingStore = adoptPtr(new TiledBackingStore(this, CoordinatedTileBackend::create(this)));
    m_mainBackingStore->setSupportsAlpha(!contentsOpaque());
    m_mainBackingStore->setContentsScale(effectiveContentsScale());
}

void CoordinatedGraphicsLayer::tiledBackingStorePaint(GraphicsContext* context, const IntRect& rect)
{
    if (rect.isEmpty())
        return;
    paintGraphicsLayerContents(*context, rect);
}

void CoordinatedGraphicsLayer::tiledBackingStorePaintEnd(const Vector<IntRect>& /* updatedRects */)
{
}

bool CoordinatedGraphicsLayer::tiledBackingStoreUpdatesAllowed() const
{
    if (!m_inUpdateMode)
        return false;
    return m_coordinator->layerTreeTileUpdatesAllowed();
}

IntRect CoordinatedGraphicsLayer::tiledBackingStoreContentsRect()
{
    return IntRect(0, 0, size().width(), size().height());
}

bool CoordinatedGraphicsLayer::shouldUseTiledBackingStore()
{
    return !selfOrAncestorHaveNonAffineTransforms();
}

IntRect CoordinatedGraphicsLayer::tiledBackingStoreVisibleRect()
{
    if (!shouldUseTiledBackingStore())
        return tiledBackingStoreContentsRect();

    // Non-invertible layers are not visible.
    if (!m_layerTransform.combined().isInvertible())
        return IntRect();

    // Return a projection of the visible rect (surface coordinates) onto the layer's plane (layer coordinates).
    // The resulting quad might be squewed and the visible rect is the bounding box of this quad,
    // so it might spread further than the real visible area (and then even more amplified by the cover rect multiplier).
    return enclosingIntRect(m_layerTransform.combined().inverse().clampedBoundsOfProjectedQuad(FloatQuad(FloatRect(m_coordinator->visibleContentsRect()))));
}

Color CoordinatedGraphicsLayer::tiledBackingStoreBackgroundColor() const
{
    return contentsOpaque() ? Color::white : Color::transparent;
}

PassOwnPtr<WebCore::GraphicsContext> CoordinatedGraphicsLayer::beginContentUpdate(const WebCore::IntSize& size, int& atlas, WebCore::IntPoint& offset)
{
    return m_coordinator->beginContentUpdate(size, contentsOpaque() ? 0 : ShareableBitmap::SupportsAlpha, atlas, offset);
}

void CoordinatedGraphicsLayer::createTile(int tileID, const SurfaceUpdateInfo& updateInfo, const WebCore::IntRect& tileRect)
{
    m_coordinator->createTile(id(), tileID, updateInfo, tileRect);
}

void CoordinatedGraphicsLayer::updateTile(int tileID, const SurfaceUpdateInfo& updateInfo, const IntRect& tileRect)
{
    m_coordinator->updateTile(id(), tileID, updateInfo, tileRect);
}

void CoordinatedGraphicsLayer::removeTile(int tileID)
{
    m_coordinator->removeTile(id(), tileID);
}

void CoordinatedGraphicsLayer::updateContentBuffers()
{
    if (!drawsContent()) {
        m_mainBackingStore.clear();
        m_previousBackingStore.clear();
        return;
    }

    m_inUpdateMode = true;
    // This is the only place we (re)create the main tiled backing store, once we
    // have a remote client and we are ready to send our data to the UI process.
    if (!m_mainBackingStore)
        createBackingStore();
    m_mainBackingStore->updateTileBuffers();
    m_inUpdateMode = false;

    // The previous backing store is kept around to avoid flickering between
    // removing the existing tiles and painting the new ones. The first time
    // the visibleRect is full painted we remove the previous backing store.
    if (m_mainBackingStore->visibleAreaIsCovered())
        m_previousBackingStore.clear();
}

void CoordinatedGraphicsLayer::purgeBackingStores()
{
    m_mainBackingStore.clear();
    m_previousBackingStore.clear();

    releaseImageBackingIfNeeded();

    didChangeLayerState();
    didChangeChildren();
}

void CoordinatedGraphicsLayer::setCoordinator(WebKit::CoordinatedGraphicsLayerClient* coordinator)
{
    m_coordinator = coordinator;
}

void CoordinatedGraphicsLayer::adjustVisibleRect()
{
    if (m_mainBackingStore)
        m_mainBackingStore->coverWithTilesIfNeeded();
}

bool CoordinatedGraphicsLayer::hasPendingVisibleChanges()
{
    if (opacity() < 0.01 && !m_animations.hasActiveAnimationsOfType(AnimatedPropertyOpacity))
        return false;

    for (size_t i = 0; i < children().size(); ++i) {
        if (toCoordinatedGraphicsLayer(children()[i])->hasPendingVisibleChanges())
            return true;
    }

    if (!m_shouldSyncLayerState && !m_shouldSyncChildren && !m_shouldSyncFilters && !m_shouldSyncImageBacking && !m_shouldSyncAnimations && !m_canvasNeedsDisplay)
        return false;

    return selfOrAncestorHaveNonAffineTransforms() || !tiledBackingStoreVisibleRect().isEmpty();

}

void CoordinatedGraphicsLayer::computeTransformedVisibleRect()
{
    if (!m_shouldUpdateVisibleRect)
        return;

    m_shouldUpdateVisibleRect = false;
    m_layerTransform.setLocalTransform(transform());
    m_layerTransform.setPosition(position());
    m_layerTransform.setAnchorPoint(anchorPoint());
    m_layerTransform.setSize(size());
    m_layerTransform.setFlattening(!preserves3D());
    m_layerTransform.setChildrenTransform(childrenTransform());
    m_layerTransform.combineTransforms(parent() ? toCoordinatedGraphicsLayer(parent())->m_layerTransform.combinedForChildren() : TransformationMatrix());

    // The combined transform will be used in tiledBackingStoreVisibleRect.
    adjustVisibleRect();
    adjustContentsScale();
}

static PassOwnPtr<GraphicsLayer> createCoordinatedGraphicsLayer(GraphicsLayerClient* client)
{
    return adoptPtr(new CoordinatedGraphicsLayer(client));
}

void CoordinatedGraphicsLayer::initFactory()
{
    GraphicsLayer::setGraphicsLayerFactory(createCoordinatedGraphicsLayer);
}

bool CoordinatedGraphicsLayer::selfOrAncestorHaveNonAffineTransforms()
{
    if (m_animations.hasActiveAnimationsOfType(AnimatedPropertyWebkitTransform))
        return true;

    if (!m_layerTransform.combined().isAffine())
        return true;

    return false;
}

bool CoordinatedGraphicsLayer::addAnimation(const KeyframeValueList& valueList, const IntSize& boxSize, const Animation* anim, const String& keyframesName, double delayAsNegativeTimeOffset)
{
    ASSERT(!keyframesName.isEmpty());

    if (!anim || anim->isEmptyOrZeroDuration() || valueList.size() < 2 || (valueList.property() != AnimatedPropertyWebkitTransform && valueList.property() != AnimatedPropertyOpacity))
        return false;

    bool listsMatch = false;
    bool ignoredHasBigRotation;

    if (valueList.property() == AnimatedPropertyWebkitTransform)
        listsMatch = validateTransformOperations(valueList, ignoredHasBigRotation) >= 0;

    m_lastAnimationStartTime = WTF::currentTime() - delayAsNegativeTimeOffset;
    m_animations.add(GraphicsLayerAnimation(keyframesName, valueList, boxSize, anim, m_lastAnimationStartTime, listsMatch));
    m_animationStartedTimer.startOneShot(0);
    didChangeAnimations();
    return true;
}

void CoordinatedGraphicsLayer::pauseAnimation(const String& animationName, double time)
{
    m_animations.pause(animationName, time);
    didChangeAnimations();
}

void CoordinatedGraphicsLayer::removeAnimation(const String& animationName)
{
    m_animations.remove(animationName);
    didChangeAnimations();
}

void CoordinatedGraphicsLayer::animationStartedTimerFired(Timer<CoordinatedGraphicsLayer>*)
{
    client()->notifyAnimationStarted(this, m_lastAnimationStartTime);
}
}
#endif
