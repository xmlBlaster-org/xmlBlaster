/*------------------------------------------------------------------------------
Name:      QueryKeyData.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/key/QueryKeyData.h>
#include <util/Constants.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <algorithm>
#include <cctype>

using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace key {

QueryKeyData::QueryKeyData(Global& global) : KeyData(global), accessFilterVector_()
{
}

QueryKeyData::QueryKeyData(const QueryKeyData& key) : KeyData(key), accessFilterVector_(key.accessFilterVector_)
{
}

QueryKeyData& QueryKeyData::operator =(const QueryKeyData& key) 
{
   accessFilterVector_ = key.accessFilterVector_;
   return *this;
}

void QueryKeyData::setQueryType(const string& queryType)
{
   string tmp = queryType;
   transform (tmp.begin(), tmp.end(), tmp.begin(), toupper);
 
   if (Constants::EXACT != tmp && Constants::XPATH !=tmp)
      throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setQueryType",
                                "Your queryType=" + queryType_ + " is invalid, use one of '" + 
				Constants::EXACT + "' , '" + Constants::XPATH + "'");
   queryType_ = tmp;
}

/**
 * Your XPath query string. 
 * @param str Your tags in ASCII XML syntax
 */
void QueryKeyData::setQueryString(const string& tags)
{
   queryString_ = tags;
}

string QueryKeyData::getQueryString() const
{
   return queryString_;
}

/**
 * Return the filters or array with size==0 if none is specified. 
 * <p />
 * For subscribe() and get() and cluster messages.
 * @return never null
 */
AccessFilterVector QueryKeyData::getAccessFilterVector() const 
{
   return accessFilterVector_;
}

void QueryKeyData::addFilter(const AccessFilterQos& qos) 
{
   accessFilterVector_.insert(accessFilterVector_.end(), qos);
}

string QueryKeyData::toXml(const string& extraOffset) const
{
   string ret;
   string offset = Constants::OFFSET + extraOffset;

   ret += offset + "<key oid='" + oid_ + "'";
   if (!getContentMime().empty())
      ret += " contentMime='" + getContentMime() + "'";
   if (!getContentMimeExtended().empty())
      ret += " contentMimeExtended='" + getContentMimeExtended() + "'";
   if (!getDomain().empty())
      ret += " domain='" + getDomain() + "'";

   if (!getQueryType().empty() && Constants::EXACT != getQueryType())
         ret += " queryType='" + getQueryType() + "'";
   ret += ">";
   if (!queryString_.empty()) {
      ret += offset + Constants::INDENT + getQueryString();
   }

   AccessFilterVector::const_iterator iter = accessFilterVector_.begin();
   while (iter != accessFilterVector_.end()) {
      ret += (*iter).toXml(extraOffset + Constants::INDENT);
      iter++;
   }
   ret += "</key>";
  return ret;
}

}}}} // namespace

