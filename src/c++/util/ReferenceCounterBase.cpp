/*------------------------------------------------------------------------------
Name:      ReferenceCounterBase.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper class to be used as a base for classes to be reference counted
Version:   $Id$
------------------------------------------------------------------------------*/

#include <util/ReferenceCounterBase.h>

namespace org { namespace xmlBlaster { namespace util {

/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

ReferenceCounterBase::ReferenceCounterBase()
   : refCount_(0)
{
}

ReferenceCounterBase::ReferenceCounterBase(const ReferenceCounterBase& /*ref*/)
   :   refCount_(0)
{
}


ReferenceCounterBase& ReferenceCounterBase::operator =(const ReferenceCounterBase& /*ref*/)
{
   return *this;
}


ReferenceCounterBase::~ReferenceCounterBase()
{
}


void ReferenceCounterBase::addReference() const
{
   ++refCount_;
}

void ReferenceCounterBase::removeReference()
{
   if (--refCount_ == 0) delete this;
}

/*
void ReferenceCounterBase::markUnshareable()
{
   shareable_ = false;
}

bool ReferenceCounterBase::isShareable() const
{
   return shareable_;
}

bool ReferenceCounterBase::isShared() const
{
   return refCount_ > 1;
}

*/

}}} // namespace

