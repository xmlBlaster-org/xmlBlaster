/*------------------------------------------------------------------------------
Name:      SecurityQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The qos for the security (a subelement of connect qos)
------------------------------------------------------------------------------*/
#include <authentication/SecurityQosFactory.h>
#include <string>
#include <util/StringTrim.h>
#include <util/PlatformUtils.hpp>

namespace org { namespace xmlBlaster { namespace authentication {

SecurityQosFactory::SecurityQosFactory(Global& global)
   : SaxHandlerBase(global), ME("SecurityQosFactory-simple"), securityQos_(global)
{
   log_.call(ME, "first constructor");
   inSecurityService_ = false;
   inUser_            = false;
   inPasswd_          = false;
}

SecurityQos SecurityQosFactory::parse(const string& xmlQoS_literal)
{
   log_.call(ME, "parse");
   // Strip CDATA tags that we are able to parse it:
   string ret = xmlQoS_literal;

   securityQos_ = SecurityQos(global_);

   // xmlQoS_literal = StringHelper.replaceAll(xmlQoS_literal, "<![CDATA[", "");
   string::size_type pos = 0;
   while (pos != ret.npos) {
      pos = ret.find("<![CDATA[");
      if (pos == ret.npos) break;
      ret = ret.erase(pos, 9);
   }

   // xmlQoS_literal = StringHelper.replaceAll(xmlQoS_literal, "]]>", "");
   pos = 0;
   while (pos != ret.npos) {
      pos = ret.find("]]>");
      if (pos == ret.npos) break;
      ret = ret.erase(pos, 3);
   }
   init(ret);
   return securityQos_;
}

/**
 * Start element, event from SAX parser.
 * <p />
 * @param name Tag name
 * @param attrs the attributes of the tag
 */
void SecurityQosFactory::startElement(const XMLCh* const name, AttributeList& attrs)
{
   if (log_.call()) log_.call(ME, "startElement");
   if (log_.trace()) {
      string txt = SaxHandlerBase::getStartElementAsString(name, attrs);
      log_.trace(ME, string("startElement: ") + txt);
   }
   if (SaxHandlerBase::caseCompare(name, "securityService")) {
      inSecurityService_ = true;
      unsigned int len = attrs.getLength();

      unsigned int ii=0;
      for (ii = 0; ii < len; ii++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(ii), "type")) {
            securityQos_.type_ = SaxHandlerBase::getStringValue(attrs.getValue(ii));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(ii), "version")) {
            securityQos_.version_ = SaxHandlerBase::getStringValue(attrs.getValue(ii));
         }
      }
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "user")) {
      inUser_ = true;
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "passwd")) {
      inPasswd_ = true;
      character_.erase();
      return;
   }
}

/**
 * End element, event from SAX parser.
 * <p />
 * @param name Tag name
 */
 void SecurityQosFactory::endElement(const XMLCh* const name)
{
   log_.call(ME, "endElement");
   if (SaxHandlerBase::caseCompare(name, "user")) {
      inUser_ = false;
      securityQos_.setUserId(StringTrim::trim(character_));
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "passwd")) {
      inPasswd_ = false;
      securityQos_.setCredential(StringTrim::trim(character_));
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "securityService")) {
      inSecurityService_ = false;
      character_.erase();
      return;
   }
}

}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST
using namespace std;
using namespace org::xmlBlaster::authentication;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQosFactory */
int main(int args, char* argv[])
{

    // Init the XML platform
    try
    {
       XMLPlatformUtils::Initialize();
    }

    catch(const XMLException& toCatch)
    {
  cout << "Error during platform init! Message:\n"
 << toCatch.getMessage() << endl;
  return 1;
    }

   string xml =
      string("<securityService type=\"  htpasswd \" version=\"1.0\">\n") +
      string("   <![CDATA[\n") +
      string("   <passwd>theUsersPwd</passwd>\n") +
      string("   <user>aUser</user>\n") +
      string("   ]]>\n") +
      string("</securityService>");

   Global& glob = Global::getInstance();
   glob.initialize(args, argv);
   cout << "Original:\n" << xml << endl;
   org::xmlBlaster::authentication::SecurityQosFactory factory(glob);
   SecurityQos qos = factory.parse(xml);
   cout << "Result:\n" << qos.toXml() << endl;
   qos.setUserId("AnotherUser");
   qos.setCredential("AnotherPassword");
   cout << qos.toXml() << endl;
   return 0;
}

#endif
