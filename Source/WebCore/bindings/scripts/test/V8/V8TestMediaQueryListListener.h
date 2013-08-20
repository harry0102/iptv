/*
    This file is part of the WebKit open source project.
    This file has been generated by generate-bindings.pl. DO NOT MODIFY!

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
    the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
    Boston, MA 02111-1307, USA.
*/

#ifndef V8TestMediaQueryListListener_h
#define V8TestMediaQueryListListener_h

#include "TestMediaQueryListListener.h"
#include "V8Binding.h"
#include "V8DOMWrapper.h"
#include "WrapperTypeInfo.h"
#include <v8.h>
#include <wtf/HashMap.h>
#include <wtf/text/StringHash.h>

namespace WebCore {

class V8TestMediaQueryListListener {
public:
    static const bool hasDependentLifetime = false;
    static bool HasInstance(v8::Handle<v8::Value>);
    static v8::Persistent<v8::FunctionTemplate> GetRawTemplate();
    static v8::Persistent<v8::FunctionTemplate> GetTemplate();
    static TestMediaQueryListListener* toNative(v8::Handle<v8::Object> object)
    {
        return reinterpret_cast<TestMediaQueryListListener*>(object->GetAlignedPointerFromInternalField(v8DOMWrapperObjectIndex));
    }
    static void derefObject(void*);
    static WrapperTypeInfo info;
    static const int internalFieldCount = v8DefaultWrapperInternalFieldCount + 0;
    static void installPerContextProperties(v8::Handle<v8::Object>, TestMediaQueryListListener*) { }
    static void installPerContextPrototypeProperties(v8::Handle<v8::Object>) { }
private:
    friend v8::Handle<v8::Object> dispatchWrap(TestMediaQueryListListener*, v8::Handle<v8::Object> creationContext, v8::Isolate*);
    static v8::Handle<v8::Object> dispatchWrapCustom(TestMediaQueryListListener*, v8::Handle<v8::Object> creationContext, v8::Isolate*);
    static v8::Handle<v8::Object> wrapSlow(PassRefPtr<TestMediaQueryListListener>, v8::Handle<v8::Object> creationContext, v8::Isolate*);
};


inline v8::Handle<v8::Object> dispatchWrap(TestMediaQueryListListener* impl, v8::Handle<v8::Object> creationContext, v8::Isolate* isolate = 0)
{
    ASSERT(impl);
    ASSERT(DOMDataStore::current(isolate)->get(impl).IsEmpty());
    return V8TestMediaQueryListListener::wrapSlow(impl, creationContext, isolate);
}

inline v8::Handle<v8::Object> toV8Object(TestMediaQueryListListener* impl, v8::Handle<v8::Object> creationContext = v8::Handle<v8::Object>(), v8::Isolate* isolate = 0)
{
    if (UNLIKELY(!impl))
        return v8::Handle<v8::Object>();
    v8::Handle<v8::Object> wrapper = DOMDataStore::current(isolate)->get(impl);
    if (!wrapper.IsEmpty())
        return wrapper;
    return dispatchWrap(impl, creationContext, isolate);
}

inline v8::Handle<v8::Value> toV8(TestMediaQueryListListener* impl, v8::Handle<v8::Object> creationContext = v8::Handle<v8::Object>(), v8::Isolate* isolate = 0)
{
    if (UNLIKELY(!impl))
        return v8NullWithCheck(isolate);
    v8::Handle<v8::Value> wrapper = DOMDataStore::current(isolate)->get(impl);
    if (!wrapper.IsEmpty())
        return wrapper;
    return dispatchWrap(impl, creationContext, isolate);
}

inline v8::Handle<v8::Value> toV8(PassRefPtr< TestMediaQueryListListener > impl, v8::Handle<v8::Object> creationContext = v8::Handle<v8::Object>(), v8::Isolate* isolate = 0)
{
    return toV8(impl.get(), creationContext, isolate);
}

}

#endif // V8TestMediaQueryListListener_h
