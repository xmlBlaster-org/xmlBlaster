/*------------------------------------------------------------------------------
Name:      SubscribeKey.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <client/key/SubscribeKey.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::key;

namespace org { namespace xmlBlaster { namespace client { namespace key {

SubscribeKey::SubscribeKey(Global& global) 
   : UnSubscribeKey(global)
{
}

SubscribeKey::SubscribeKey(Global& global, const string& query, const string& queryType) 
   : UnSubscribeKey(global, query, queryType)
{
}

SubscribeKey::SubscribeKey(Global& global, const QueryKeyData& data) 
   : UnSubscribeKey(global, data)
{
}

SubscribeKey::SubscribeKey(const SubscribeKey& key)
   : UnSubscribeKey(key)
{
}

SubscribeKey& SubscribeKey::operator =(const SubscribeKey& key)
{
   queryKeyData_ = key.queryKeyData_;
   return *this;
}

void SubscribeKey::setDomain(const string& domain)
{
   queryKeyData_.setDomain(domain);
}

string SubscribeKey::getDomain() const
{
   return queryKeyData_.getDomain();
}

}}}} // namespace




