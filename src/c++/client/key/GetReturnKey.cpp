/*------------------------------------------------------------------------------
Name:      GetReturnKey.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <client/key/GetReturnKey.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::key;

namespace org { namespace xmlBlaster { namespace client { namespace key {

GetReturnKey::GetReturnKey(Global& global) 
   : UpdateKey(global)
{
}

GetReturnKey::GetReturnKey(Global& global, const MsgKeyData& data) 
   : UpdateKey(global, data)
{
}

GetReturnKey::GetReturnKey(const GetReturnKey& key)
   : UpdateKey(key)
{
}

GetReturnKey& GetReturnKey::operator =(const GetReturnKey& key)
{
   msgKeyData_ = key.msgKeyData_;
   return *this;
}

bool GetReturnKey::isInternal() const
{
   return msgKeyData_.isInternal();
}

}}}} // namespace




