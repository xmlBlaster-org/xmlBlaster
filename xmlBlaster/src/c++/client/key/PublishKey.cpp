/*------------------------------------------------------------------------------
Name:      PublishKey.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <client/key/PublishKey.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::key;

namespace org { namespace xmlBlaster { namespace client { namespace key {

PublishKey::PublishKey(Global& global, const string& oid, const string& mime, const string& mimeExt) 
   : MsgKeyBase(global)
{
   if (!oid.empty()) setOid(oid);
   if (!mime.empty()) setContentMime(mime);
   if (!mimeExt.empty()) setContentMimeExtended(mimeExt);
}

PublishKey::PublishKey(Global& global, const MsgKeyData& data) 
   : MsgKeyBase(global, data)
{
}

PublishKey::PublishKey(const PublishKey& key)
   : MsgKeyBase(key)
{
}

PublishKey& PublishKey::operator =(const PublishKey& key)
{
   msgKeyData_ = key.msgKeyData_;
   return *this;
}

void PublishKey::setDomain(const string& domain)
{
   msgKeyData_.setDomain(domain);
}

void PublishKey::setOid(const string& oid)
{
   msgKeyData_.setOid(oid);
}

void PublishKey::setContentMime(const string& contentMime)
{
   msgKeyData_.setContentMime(contentMime);
}

void PublishKey::setContentMimeExtended(const string& contentMimeExtended)
{
   msgKeyData_.setContentMimeExtended(contentMimeExtended);
}

void PublishKey::setClientTags(const string& tags)
{
   msgKeyData_.setClientTags(tags);
}

}}}} // namespace




