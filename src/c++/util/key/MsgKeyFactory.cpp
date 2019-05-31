/*------------------------------------------------------------------------------
Name:      MsgKeyFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/key/MsgKeyFactory.h>
# include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace key {

MsgKeyFactory::MsgKeyFactory(Global& global) 
   : XmlHandlerBase(global), ME("MsgKeyFactory"), msgKeyData_(global)
{
   inKey_ = 0;
   OID                   = "oid";
   CONTENT_MIME          = "contentMime";
   CONTENT_MIME_EXTENDED = "contentMimeExtended";
   D_O_M_A_I_N           = "domain";
   clientTags_           = "";
}

MsgKeyFactory::~MsgKeyFactory() 
{
   clientTags_  = "";
}

MsgKeyData MsgKeyFactory::readObject(const string& xmlKey)
{
   clientTags_        = "";
   clientTagsOffset_  = "";
   clientTagsDepth_   = 0;
   msgKeyData_.setOid("");
//   msgKeyData_.setQueryType("");
   msgKeyData_.setContentMime("");
   msgKeyData_.setContentMimeExtended("");
   msgKeyData_.setDomain("");

   if (xmlKey.empty()) init("<key/>");
   else init(xmlKey);      

   if (msgKeyData_.getOid().empty()) {
      msgKeyData_.setOid(msgKeyData_.generateOid(global_.getStrippedId()));
   }
   return msgKeyData_;
}

void MsgKeyFactory::startElement(const string &name, const parser::AttributeMap &attrs)
{
   if (log_.call()) log_.call(ME, "startElement: " + getStartElementAsString(name, attrs));
   if (name.compare("key") == 0) {
      msgKeyData_ = MsgKeyData(global_);
      inKey_++;
      if (inKey_ > 1) return;

      if (attrs.size() > 0) {
         string tmp;
         if ( getStringAttr(attrs, OID, tmp) ) 
            msgKeyData_.setOid(tmp); // it is already trimmed 
         // if ( getStringAttr(attrs, "queryType", tmp) )
         //    msgKeyData_.setQueryType(tmp);
         if ( getStringAttr(attrs, CONTENT_MIME, tmp) )
            msgKeyData_.setContentMime(tmp); // it is already trimmed 
         if ( getStringAttr(attrs, CONTENT_MIME_EXTENDED, tmp) )
            msgKeyData_.setContentMimeExtended(tmp); // it is already trimmed 
         if ( getStringAttr(attrs, D_O_M_A_I_N, tmp) )
            msgKeyData_.setDomain(tmp); // it is already trimmed 
      }
      character_.erase();
      return;
   }
   // then it must be a part of a client tag
   clientTagsDepth_++;
   clientTagsOffset_ += "  ";
   clientTags_ += clientTagsOffset_ + getStartElementAsString(name, attrs) + "\n";
   character_.erase();
}

void MsgKeyFactory::endElement(const string &name)
{
   if (name.compare("key") == 0) {
      inKey_--;
      if (clientTags_.length() > 0) msgKeyData_.setClientTags(clientTags_);
      if (inKey_ > 0) return; // ignore nested key tags
   }
   if (character_.length() > 0) clientTags_ += clientTagsOffset_ + "  " + character_ + "\n";
   clientTags_ += clientTagsOffset_ + "</" + name + ">\n";
   clientTagsDepth_--;
   for (int i=0; i <clientTagsDepth_; i++) clientTagsOffset_ = "  ";
   character_.erase();
 }


}}}} // namespace

