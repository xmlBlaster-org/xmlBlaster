/*------------------------------------------------------------------------------
Name:      SecurityQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The qos for the security (a subelement of connect qos)
------------------------------------------------------------------------------*/
#include <authentication/SecurityQos.h>
#include <string>

#include <util/PlatformUtils.hpp>

namespace org { namespace xmlBlaster { namespace authentication {

SecurityQos::SecurityQos(Global& global)
   : SaxHandlerBase(global),
     ME("SecurityQos-simple"),
     trim_()
{
   log_.call(ME, "first constructor");
   prep();
}

SecurityQos::SecurityQos(const string& xmlQoS_literal, Global& global)
   : SaxHandlerBase(global),
     ME("SecurityQos-simple"),
     trim_()
{
   log_.call(ME, "second constructor");
   prep();
   parse(xmlQoS_literal);
}

SecurityQos::SecurityQos(const string& loginName,
                         const string& password,
                         Global& global)
   : SaxHandlerBase(global), ME("SecurityQos-simple"), trim_()
{
   log_.call(ME, "third constructor");
   prep();
   user_   = loginName;
   passwd_ = password;
}

SecurityQos::SecurityQos(const SecurityQos& securityQos)
   : SaxHandlerBase(securityQos.global_), ME("SecurityQos-simple"), trim_()
{
   copy(securityQos);
}

SecurityQos& SecurityQos::operator =(const SecurityQos& securityQos)
{
   copy(securityQos);
   return *this;
}

string SecurityQos::getPluginVersion() const
{
   return version_;
}


string SecurityQos::getPluginType() const
{
   return type_;
}


void SecurityQos::setUserId(const string& userId)
{
   user_ = userId;
}


string SecurityQos::getUserId() const
{
   return user_;
}


/**
 * @param cred The password
 */
void SecurityQos::setCredential(const string& cred)
{
   passwd_ = cred;
}


/**
 * @return "" (empty string) (no password is delivered)
 */
string SecurityQos::getCredential() const
{
   return "";
}

/**
 * Start element, event from SAX parser.
 * <p />
 * @param name Tag name
 * @param attrs the attributes of the tag
 */
void SecurityQos::startElement(const XMLCh* const name, AttributeList& attrs)
{
   if (log_.CALL) log_.call(ME, "startElement");
   if (log_.TRACE) {
      string txt = SaxHandlerBase::getStartElementAsString(name, attrs);
      log_.trace(ME, string("startElement: ") + txt);
   }
   if (SaxHandlerBase::caseCompare(name, "securityService")) {
      inSecurityService_ = true;
      unsigned int len = attrs.getLength();

      unsigned int ii=0;
      for (ii = 0; ii < len; ii++) {
         if (SaxHandlerBase::caseCompare(attrs.getName(ii), "securityService")) {
            type_ = SaxHandlerBase::getStringValue(attrs.getValue(ii));
         }
         else if (SaxHandlerBase::caseCompare(attrs.getName(ii), "version")) {
            version_ = SaxHandlerBase::getStringValue(attrs.getValue(ii));
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
 void SecurityQos::endElement(const XMLCh* const name)
{
   log_.call(ME, "endElement");
   if (SaxHandlerBase::caseCompare(name, "user")) {
      inUser_ = false;
      char *help = trim_.trim(character_.c_str());
      user_ = help;
      delete help;
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "passwd")) {
      inPasswd_ = false;
      char *help = trim_.trim(character_.c_str());
      passwd_ = help;
      delete help;
      character_.erase();
      return;
   }

   if (SaxHandlerBase::caseCompare(name, "securityService")) {
      inSecurityService_ = false;
      character_.erase();
      return;
   }
}


string SecurityQos::toXml()
{
   return toXml("");
}


/**
 * Dump state of this object into a XML ASCII string.
 * <br>
 * @param extraOffset indenting of tags for nice output
 * @return The xml representation
 */
string SecurityQos::toXml(const string& extraOffset)
{
   log_.call(ME, "toXml");
   string ret;
   string offset = "\n   ";
   offset += extraOffset;

   ret += offset + "<securityService type=\"";
   ret += getPluginType() + "\" version=\"" + getPluginVersion()  + "\">";
   ret += offset + "   <![CDATA[";
   ret += offset + "   <user>" + user_ + "</user>";
   ret += offset + "   <passwd>" + passwd_ + "</passwd>";
   ret += offset + "   ]]>";
   ret += offset + "</securityService>";
   return ret;
}

}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST
using namespace std;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
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
      string("<securityService type=\"  simple \" version=\"1.0\">\n") +
      string("   <![CDATA[\n") +
      string("   <passwd>theUsersPwd</passwd>\n") +
      string("   <user>aUser</user>\n") +
      string("   ]]>\n") +
      string("</securityService>");

   Global& glob = Global::getInstance();
   cout << "Original:\n" << xml << endl;
   org::xmlBlaster::authentication::SecurityQos qos(xml, glob);
   cout << "Result:\n" << qos.toXml() << endl;
   qos.setUserId("AnotherUser");
   qos.setCredential("AnotherPassword");
   cout << qos.toXml() << endl;
   return 0;
}

#endif
