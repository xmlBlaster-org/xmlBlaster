/*------------------------------------------------------------------------------
Name:      ReferenceCounterBase.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper class to be used as a base for classes to be reference counted
Version:   $Id$
------------------------------------------------------------------------------*/

#ifndef _UTIL_REFERENCECOUNTERBASE_H
#define _UTIL_REFERENCECOUNTERBASE_H

#include <util/XmlBCfg.h>

// 

namespace org { namespace xmlBlaster { namespace util {

/**
 * Reference counter class to be used as a base class for such classes which you need to
 * have a reference counter on. It works together with ReferenceHolder.
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

class Dll_Export ReferenceCounterBase 
{
private:
   mutable int  refCount_;
//   bool shareable_;

public:
   ReferenceCounterBase();
   ReferenceCounterBase(const ReferenceCounterBase& ref);
   ReferenceCounterBase& operator =(const ReferenceCounterBase& ref);
   virtual  ~ReferenceCounterBase();
   void addReference() const;
   void removeReference();
//   void markUnshareable();
//   bool isShareable() const;
//   bool isShared() const;
};


}}} // namespace

#endif
