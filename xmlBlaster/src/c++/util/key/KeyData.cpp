/*------------------------------------------------------------------------------
Name:      KeyData.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/key/KeyData.h>
#include <util/Constants.h>
#include <util/Timestamp.h> 
#include <util/Global.h>
#include <util/lexical_cast.h>



using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace key {

Dll_Export const char* CONTENTMIME_DEFAULT = "text/plain";
Dll_Export const char* DEFAULT_DOMAIN = "";
Dll_Export const char* QUERYTYPE_DEFAULT = Constants::EXACT;

void KeyData::init() 
{
   oid_                 = "";
   contentMime_         = "";
   contentMimeExtended_ = "";
   domain_              = DEFAULT_DOMAIN;
   isGeneratedOid_      = false;
   queryType_           = QUERYTYPE_DEFAULT;
   queryString_         = "";
}

void KeyData::copy(const KeyData& key) 
{
   oid_                 = key.oid_;
   contentMime_         = key.contentMime_;
   contentMimeExtended_ = key.contentMimeExtended_;
   domain_              = key.domain_;
   isGeneratedOid_      = key.isGeneratedOid_;
   queryType_           = key.queryType_;
   queryString_         = key.queryString_;
}

KeyData::KeyData(Global& global)
   : ME("KeyData"), global_(global), log_(global.getLog("org.xmlBlaster.util.key"))
{
}


KeyData::KeyData(const KeyData& key)
   : ME(key.ME), global_(key.global_), log_(key.log_)
{
   copy(key);
}

KeyData& KeyData::operator =(const KeyData& key) 
{
   copy(key);
   return *this;
}

KeyData::~KeyData() 
{
}

void KeyData::setOid(const string& oid)
{
   oid_ = oid;
}

string KeyData::getOid() const
{
   return oid_;
}

bool KeyData::isDeadMessage() const
{
   return Constants::OID_DEAD_LETTER == oid_;
}

bool KeyData::isPluginInternal() const
{
   if (oid_.empty()) return false;
   return ( (oid_.find(Constants::INTERNAL_OID_PREFIX_FOR_PLUGINS) == 0) ||
            (oid_.find(Constants::INTERNAL_OID_PREFIX_FOR_CORE   ) != 0) );
}

bool KeyData::isInternal() const
{
   return (oid_.empty()) ? false : (oid_.find(Constants::INTERNAL_OID_PREFIX_FOR_CORE) == 0);
}

bool KeyData::isAdministrative() const
{
   return (oid_.empty()) ? false : (oid_.find(Constants::INTERNAL_OID_ADMIN_CMD) == 0);
}

void KeyData::setContentMime(const string& contentMime)
{
   contentMime_ = contentMime;
}

string KeyData::getContentMime() const
{  
   return (contentMime_.empty()) ? CONTENTMIME_DEFAULT : contentMime_;
}

void KeyData::setContentMimeExtended(const string& contentMimeExtended)
{
   contentMimeExtended_ = contentMimeExtended;
}

string KeyData::getContentMimeExtended() const
{
   return contentMimeExtended_;
}

void KeyData::setDomain(const string& domain)
{
   domain_ = domain;
}

string KeyData::getDomain() const
{
   return domain_;
}

bool KeyData::isDefaultDomain() const
{
   if (domain_.empty() || domain_ == DEFAULT_DOMAIN) return true;
   return false;
}

string KeyData::getQueryType() const
{
   return queryType_;
}

bool KeyData::isExact() const
{
   return Constants::EXACT == queryType_;
}

bool KeyData::isQuery() const
{
   return Constants::XPATH == queryType_ || 
          Constants::REGEX == queryType_;
}

bool KeyData::isXPath() const
{
   return Constants::XPATH == queryType_;
}

bool KeyData::isDomain() const
{
   return Constants::D_O_M_A_I_N == queryType_;
}

int KeyData::size() const
{
   return (int)toXml().length();
}

KeyData* KeyData::getClone() const
{
   return new KeyData(*this);
}

string KeyData::toXml() const
{
   return toXml("");
}

std::string KeyData::toXml(const std::string& /*extraOffset*/) const
{
   return "<error>KeyData::toXml: PLEASE IMPLEMENT IN_BASE CLASS</error>";
}

string KeyData::generateOid(const string& uniquePrefix) const
{
   string ret;
   Timestamp timestamp = TimestampFactory::getInstance().getTimestamp();
   oid_ += uniquePrefix + "-" + lexical_cast<std::string>(timestamp);
   isGeneratedOid_ = true;
   return oid_;
}

bool KeyData::isGeneratedOid() const
{
   return isGeneratedOid_;
}

}}}} // namespaces

