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

namespace org { namespace xmlBlaster { namespace util { namespace key {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;

QueryKeyData::QueryKeyData(Global& global) : KeyData(global), accessFilterVector_()
{
}

QueryKeyData::QueryKeyData(Global& global, const string& query, const string& queryType) : KeyData(global), accessFilterVector_()
{
   this->queryType_ = checkQueryType(queryType);
   if (isExact()) {
      setOid(query);
   }
   else if (isXPath()) {
      this->queryString_ = query;
   }
   else if (isDomain()) {
      setDomain(query);
   }
   else {
      throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setQueryType",
                                "Your queryType=" + queryType_ + " is invalid, use one of '" + 
                                Constants::EXACT + "' , '" + Constants::XPATH + "' , '" + Constants::D_O_M_A_I_N + "'");
   }
}

QueryKeyData::QueryKeyData(const QueryKeyData& key) : KeyData(key), accessFilterVector_(key.accessFilterVector_)
{
}

QueryKeyData& QueryKeyData::operator =(const QueryKeyData& key) 
{
   accessFilterVector_ = key.accessFilterVector_;
   return *this;
}

string QueryKeyData::checkQueryType(const string& queryType)
{
   string tmp = queryType;
   // transform (tmp.begin(), tmp.end(), tmp.begin(), ::toupper);
   string::iterator iter = tmp.begin();
   while (iter != tmp.end()) {
      *iter = (char)::toupper(*iter);
      iter++;
   }

   if (Constants::EXACT != tmp && Constants::XPATH !=tmp && Constants::D_O_M_A_I_N !=tmp)
      throw XmlBlasterException(USER_ILLEGALARGUMENT, ME + "::setQueryType",
                                "Your queryType=" + queryType + " is invalid, use one of '" + 
                                Constants::EXACT + "' , '" + Constants::XPATH + "' , '" + Constants::D_O_M_A_I_N + "'");
   return tmp;
}

void QueryKeyData::setQueryType(const string& queryType)
{
   queryType_ = checkQueryType(queryType);
}

/**
 * Your XPath query string. 
 * NOTE: You need to set the query type first!
 * @param str Your tags in ASCII XML syntax
 */
void QueryKeyData::setQueryString(const string& tags)
{
   this->queryType_ = Constants::XPATH;
   this->queryString_ = tags;
}

void QueryKeyData::setOid(const string& oid)
{
   this->queryType_ = Constants::EXACT;
   oid_ = oid;
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

string QueryKeyData::toXml() const
{
   return toXml("");
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

QueryKeyData* QueryKeyData::getClone() const
{
   return new QueryKeyData(*this);
}

}}}} // namespace

