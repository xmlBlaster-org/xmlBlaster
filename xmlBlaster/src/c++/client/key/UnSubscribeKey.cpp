/*------------------------------------------------------------------------------
Name:      UnSubscribeKey.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <client/key/UnSubscribeKey.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::key;

namespace org { namespace xmlBlaster { namespace client { namespace key {

UnSubscribeKey::UnSubscribeKey(Global& global) 
   : ME("UnSubscribeKey"), global_(global), log_(global_.getLog("org.xmlBlaster.client")), queryKeyData_(global_)
{
}

UnSubscribeKey::UnSubscribeKey(Global& global, const string& query, const string& queryType) 
   : ME("UnSubscribeKey"), global_(global), log_(global_.getLog("org.xmlBlaster.client")),
     queryKeyData_(global_, query, queryType)
{
}

UnSubscribeKey::UnSubscribeKey(Global& global, const QueryKeyData& data) 
   : ME("UnSubscribeKey"), global_(global), log_(global_.getLog("org.xmlBlaster.client")), queryKeyData_(data)
{
}

UnSubscribeKey::UnSubscribeKey(const UnSubscribeKey& key)
   : ME(key.ME), global_(key.global_), log_(key.log_), queryKeyData_(key.queryKeyData_)
{
}

UnSubscribeKey& UnSubscribeKey::operator =(const UnSubscribeKey& key)
{
   queryKeyData_ = key.queryKeyData_;
   return *this;
}

void UnSubscribeKey::setOid(const string& oid)
{
   queryKeyData_.setOid(oid);
}

string UnSubscribeKey::getOid() const
{
   return queryKeyData_.getOid();
}

string UnSubscribeKey::getQueryType() const
{
   return queryKeyData_.getQueryType();
}

void UnSubscribeKey::setQueryString(const string& tags)
{
   queryKeyData_.setQueryString(tags);
}

string UnSubscribeKey::getQueryString() const
{
   return queryKeyData_.getQueryString();
}

void UnSubscribeKey::setDomain(const string& domain)
{
   queryKeyData_.setDomain(domain);
}

string UnSubscribeKey::getDomain() const
{
   return queryKeyData_.getDomain();
}

string UnSubscribeKey::toXml(const string& extraOffset) const
{
   return queryKeyData_.toXml(extraOffset);
}

string UnSubscribeKey::wrap(const string& str)
{
   queryKeyData_.setQueryString(str);
   return queryKeyData_.toXml();
}

const QueryKeyData& UnSubscribeKey::getData() const
{
   return queryKeyData_;
}

}}}} // namespace




