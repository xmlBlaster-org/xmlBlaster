/*------------------------------------------------------------------------------
Name:      MsgKeyData.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/key/MsgKeyData.h>
#include <util/Constants.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace key {

MsgKeyData::MsgKeyData(Global& global) : KeyData(global)
{
   clientTags_ = "";
}

MsgKeyData::MsgKeyData(const MsgKeyData& key) : KeyData(key)
{
   clientTags_ = key.clientTags_;
}

MsgKeyData& MsgKeyData::operator =(const MsgKeyData& key) 
{
   clientTags_ = key.clientTags_;
   return *this;
}

string MsgKeyData::getOid() const
{
   if (oid_.empty()) {
      generateOid(global_.getStrippedId());
   }
   return oid_;
}

void MsgKeyData::setClientTags(const string& tags)
{
   clientTags_ = tags;
}

string MsgKeyData::getClientTags() const
{
   return clientTags_;
}

string MsgKeyData::toXml() const
{
   return toXml("");
}

string MsgKeyData::toXml(const string& extraOffset) const
{
   string ret;
   string offset = Constants::OFFSET + extraOffset;

   ret += offset + "<key oid='" + oid_ + "'";
   if (!contentMime_.empty())
      ret += " contentMime='" + getContentMime() + "'";
   if (!contentMimeExtended_.empty())
      ret += " contentMimeExtended='" + getContentMimeExtended() + "'";
   if (!getDomain().empty())
      ret += " domain='" + getDomain() + "'";
   if (!getClientTags().empty()) {
      ret += ">";
      ret += offset + extraOffset + Constants::INDENT + getClientTags();
      ret += offset + "</key>";
   }
   else
      ret += "/>";
  return ret;
}

MsgKeyData* MsgKeyData::getClone() const
{
   return new MsgKeyData(*this);
}

}}}} // namespace

