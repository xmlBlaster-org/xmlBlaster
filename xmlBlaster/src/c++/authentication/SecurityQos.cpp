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

SecurityQos::SecurityQos(int args, char *argc[])
   : SaxHandlerBase(args, argc),
     ME("SecurityQos-simple"),
     trim_()
{
   log_.call(ME, "first constructor");
   prep(args, argc);
}

SecurityQos::SecurityQos(const string& xmlQoS_literal, int args=0, char *argc[]=0)
   : SaxHandlerBase(args, argc),
     ME("SecurityQos-simple"),
     trim_()
{
   log_.call(ME, "second constructor");
   prep(args, argc);
   parse(xmlQoS_literal);
}

SecurityQos::SecurityQos(const string& loginName,
                         const string& password,
                         int args=0,
                         char *argc[]=0)
   : SaxHandlerBase(args, argc), ME("SecurityQos-simple"), trim_()
{
   log_.call(ME, "third constructor");
   prep(args, argc);
   user_   = loginName;
   passwd_ = password;
}

SecurityQos::SecurityQos(const SecurityQos& securityQos)
   : SaxHandlerBase(securityQos.args_, argc_), ME("SecurityQos-simple"), trim_()
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
   log_.call(ME, "startElement");
   if (name == XMLString::transcode("securityService")) {
      inSecurityService_ = true;
      unsigned int len = attrs.getLength();

      unsigned int ii=0;
      for (ii = 0; ii < len; ii++) {
         if (attrs.getName(ii) == XMLString::transcode("type")) {
            type_ = trim_.trim(XMLString::transcode(attrs.getValue(ii))); // .trim();
         }
         else if (attrs.getName(ii) == XMLString::transcode("version")) {
            version_ = trim_.trim(XMLString::transcode(attrs.getValue(ii))); //.trim();
         }
      }
      character_.erase();
      return;
   }

   if (name == XMLString::transcode("user")) {
      inUser_ = true;
      character_.erase();
      return;
   }

   if (name == XMLString::transcode("passwd")) {
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
   if (name == XMLString::transcode("user")) {
      inUser_ = false;
      user_ = trim_.trim(character_.c_str()); // .toString().trim();
      character_.erase();
      return;
   }

   if (name == XMLString::transcode("passwd")) {
      inPasswd_ = false;
      passwd_ = trim_.trim(character_.c_str()); // .toString().trim();
      character_.erase();
      return;
   }

   if (name == XMLString::transcode("securityService")) {
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

   cout << "Original:\n" << xml << endl;
   org::xmlBlaster::authentication::SecurityQos qos(xml, args, argv);
   cout << "Result:\n" << qos.toXml() << endl;
   qos.setUserId("AnotherUser");
   qos.setCredential("AnotherPassword");
   cout << qos.toXml() << endl;
   return 0;
}

#endif
