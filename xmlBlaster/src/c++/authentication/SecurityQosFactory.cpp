/*------------------------------------------------------------------------------
Name:      SecurityQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The qos for the security (a subelement of connect qos)
------------------------------------------------------------------------------*/
#include <authentication/SecurityQosFactory.h>
#include <string>
#include <util/StringTrim.h>

namespace org { namespace xmlBlaster { namespace authentication {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::parser;

SecurityQosFactory::SecurityQosFactory(Global& global)
   : XmlHandlerBase(global), ME("SecurityQosFactory-simple"), securityQos_(global)
{
   log_.call(ME, "first constructor");
   inSecurityService_ = false;
   inUser_            = false;
   inPasswd_          = false;
}

SecurityQos SecurityQosFactory::parse(const string& xmlQoS_literal)
{
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
void SecurityQosFactory::startElement(const string &name, const AttributeMap& attrs)
{
   // if (log_.call()) log_.call(ME, "startElement: " + getStartElementAsString(name, attrs));
   if (name.compare("securityService") == 0) {
      inSecurityService_ = true;
      AttributeMap::const_iterator iter = attrs.begin();
      while (iter != attrs.end()) {
         string tmpName = (*iter).first;
         if ( tmpName.compare("type") == 0) securityQos_.type_ = (*iter).second;
         else if (tmpName.compare("version") == 0) {
            securityQos_.version_ = (*iter).second;
         }
         iter++;
      }
      character_.erase();
      return;
   }

   if (name.compare("user") == 0) {
      inUser_ = true;
      character_.erase();
      return;
   }

   if (name.compare("passwd") == 0) {
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
 void SecurityQosFactory::endElement(const string &name)
{
   // log_.call(ME, "endElement");
   if (name.compare("user") == 0) {
      inUser_ = false;
      StringTrim::trim(character_);
      securityQos_.setUserId(character_);
      character_.erase();
      return;
   }

   if (name.compare("passwd") == 0) {
      inPasswd_ = false;
      StringTrim::trim(character_);
      securityQos_.setCredential(character_);
      character_.erase();
      return;
   }

   if (name.compare("securityService") == 0) {
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
