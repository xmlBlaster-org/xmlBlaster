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
   : SaxHandlerBase(global), ME("MsgKeyFactory"), msgKeyData_(global)
{
   inKey_ = 0;
   OID                   = XMLString::transcode("oid");
   CONTENT_MIME          = XMLString::transcode("contentMime");
   CONTENT_MIME_EXTENDED = XMLString::transcode("contentMimeExtended");
   D_O_M_A_I_N           = XMLString::transcode("domain");
}

MsgKeyFactory::~MsgKeyFactory() 
{
   delete OID;
   delete CONTENT_MIME;
   delete CONTENT_MIME_EXTENDED;
   delete D_O_M_A_I_N;
}

MsgKeyData MsgKeyFactory::readObject(const string& xmlKey)
{
   if (xmlKey.empty()) init("<key/>");
   else init(xmlKey);      

   if (msgKeyData_.getOid().empty()) {
      msgKeyData_.setOid(msgKeyData_.generateOid(global_.getStrippedId()));
   }
   return msgKeyData_;
}

void MsgKeyFactory::startElement(const XMLCh* const name, AttributeList& attrs) 
{
   log_.call(ME, "startElement");

   if (SaxHandlerBase::caseCompare(name, "key")) {
      msgKeyData_ = MsgKeyData(global_);
      inKey_++;
      if (inKey_ > 1) return;

      if (attrs.getLength() > 0) {
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
}

void MsgKeyFactory::endElement(const XMLCh* const name)
{
   if (SaxHandlerBase::caseCompare(name, "key")) {
      inKey_--;
      if (inKey_ > 0) return; // ignore nested key tags
   }
}


}}}} // namespace

#ifdef _XMLBLASTER_CLASSTEST

#include <util/PlatformUtils.hpp>

using namespace std;
using namespace org::xmlBlaster::util::key;

int main(int args, char* argv[])
{
    // Init the XML platform
    XMLPlatformUtils::Initialize();
    Global& glob = Global::getInstance();
    glob.initialize(args, argv);
    MsgKeyFactory factory(glob);
    string xml;
    xml += string("<key oid='HELLO' contentMime='image/gif' contentMimeExtended='2.0' domain='RUGBY'>\n") +
           "   Bla1\n" +
           "   <a><b></b></a>\n" +
           "   <![CDATA[Bla2]]>\n" +
           "   <c></c>\n" +
           "   Bla3\n" +
           "</key>\n";
    try {
       MsgKeyData key = factory.readObject(xml);
       cout << "INPUT\n" << key.toXml() << endl;
       cout << "RESULT\n" << key.toXml() << endl;
    }
    catch (...) {
       cerr << "ERROR: " << endl;
    }
}


#endif

