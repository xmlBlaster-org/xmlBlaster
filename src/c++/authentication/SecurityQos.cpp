/*------------------------------------------------------------------------------
Name:      SecurityQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The qos for the security (a subelement of connect qos)
------------------------------------------------------------------------------*/
#include <authentication/SecurityQos.h>
#include <string>
#include <util/StringStripper.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace authentication {

using namespace std;
using namespace org::xmlBlaster::util;

SecurityQos::SecurityQos(Global& global,
                         const string& loginName,
                         const string& password,
                         const string& pluginTypeVersion)
   : ME("SecurityQos"), global_(global), log_(global.getLog("org.xmlBlaster.authentication"))
{

   string tv = (pluginTypeVersion == "") ? "htpasswd,1.0" : pluginTypeVersion;
   string help = global_.getProperty().getStringProperty("Security.Client.DefaultPlugin", tv);

   StringStripper stripper(",");
   vector<std::string> help1 = stripper.strip(help);
   if (help1.size() == 2) {
      type_    = help1[0];
      version_ = help1[1];
   }
   else {
      type_    = "htpasswd";
      version_ = "1.0";
   }

   if (loginName != "") {
      user_ = loginName;
   }
   else {
      SessionName sessionName(global_);
      user_ = sessionName.getSubjectId();
   }

   passwd_ =  global_.getProperty().getStringProperty("passwd", "");
   if (password != "") passwd_ = password;

   if (log_.trace())  log_.trace(ME, string("constructor: type=" + type_ + " and version=" + version_ + " userId=") + user_);
}

SecurityQos::SecurityQos(const SecurityQos& securityQos)
   : ME("SecurityQos"), global_(securityQos.global_), log_(securityQos.log_)
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
 * Dump state of this object into a XML ASCII string.
 * <br>
 * @param extraOffset indenting of tags for nice output
 * @return The xml representation
 */
string SecurityQos::toXml(const string& extraOffset)
{
   string ret;
   string offset = Constants::OFFSET + extraOffset;
   string offset2 = offset + Constants::INDENT;

   ret += offset + "<securityService type=\"";
   ret += getPluginType() + "\" version=\"" + getPluginVersion()  + "\">";
   ret += offset2 + "<![CDATA[";
   ret += offset2 + "<user>" + user_ + "</user>";
   ret += offset2 + "<passwd>" + passwd_ + "</passwd>";
   ret += offset2 + "]]>";
   ret += offset + "</securityService>";
   return ret;
}

}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using namespace org::xmlBlaster::authentication;

int main(int args, char* argv[])
{
    try {
       Global& glob = Global::getInstance();
       glob.initialize(args, argv);

       SecurityQos qos(glob);
       cout << "the default: " << endl << qos.toXml() << endl;

       SecurityQos qos1(glob, "this_is_user", "this_is_passwd");
       cout << "the default: " << endl << qos1.toXml() << endl;

    }

    catch(...)
    {
       cout << "Error during execution" << endl;
       return 1;
    }

   return 0;
}

#endif
