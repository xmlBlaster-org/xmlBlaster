/*------------------------------------------------------------------------------
Name:      UpdateKey.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <client/key/UpdateKey.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::key;

namespace org { namespace xmlBlaster { namespace client { namespace key {

UpdateKey::UpdateKey(Global& global) 
   : MsgKeyBase(global)
{
}

UpdateKey::UpdateKey(Global& global, const MsgKeyData& data) 
   : MsgKeyBase(global, data)
{
}

UpdateKey::UpdateKey(const UpdateKey& key)
   : MsgKeyBase(key)
{
}

UpdateKey& UpdateKey::operator =(const UpdateKey& key)
{
   msgKeyData_ = key.msgKeyData_;
   return *this;
}

bool UpdateKey::isDeadMessage() const
{
   return msgKeyData_.isDeadMessage();
}

bool UpdateKey::isPluginInternal() const
{
   return msgKeyData_.isPluginInternal();
}

string UpdateKey::getClientTags() const
{
   return msgKeyData_.getClientTags();
}

}}}} // namespace




