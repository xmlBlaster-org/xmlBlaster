/*------------------------------------------------------------------------------
Name:      MsgKeyBase.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <client/key/MsgKeyBase.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::key;

namespace org { namespace xmlBlaster { namespace client { namespace key {

MsgKeyBase::MsgKeyBase(Global& global) 
   : ME("MsgKeyBase"), global_(global), log_(global_.getLog("org.xmlBlaster.client")), msgKeyData_(global_)
{
}

MsgKeyBase::MsgKeyBase(Global& global, const MsgKeyData& data) 
   : ME("MsgKeyBase"), global_(global), log_(global_.getLog("org.xmlBlaster.client")), msgKeyData_(data)
{
}

MsgKeyBase::MsgKeyBase(const MsgKeyBase& key)
   : ME(key.ME), global_(key.global_), log_(key.log_), msgKeyData_(key.msgKeyData_)
{
}

MsgKeyBase& MsgKeyBase::operator =(const MsgKeyBase& key)
{
   msgKeyData_ = key.msgKeyData_;
   return *this;
}

const MsgKeyData& MsgKeyBase::getData() const
{
   return msgKeyData_;
}

string MsgKeyBase::getOid() const
{
   return msgKeyData_.getOid();
}

string MsgKeyBase::getContentMime() const
{
   return msgKeyData_.getContentMime();
}

string MsgKeyBase::getContentMimeExtended() const
{
   return msgKeyData_.getContentMimeExtended();
}

string MsgKeyBase::getDomain() const
{
   return msgKeyData_.getDomain();
}
string MsgKeyBase::toXml(const string& extraOffset) const
{
   return msgKeyData_.toXml(extraOffset);
}

}}}} // namespace




