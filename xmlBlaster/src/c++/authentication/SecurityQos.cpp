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

using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace authentication {

SecurityQos::SecurityQos(Global& global,
                         const string& loginName,
                         const string& password)
   : ME("SecurityQos"), global_(global), log_(global.getLog("core"))
{

   string help = global_.getProperty().getStringProperty("Security.Client.DefaultPlugin", "htpasswd,1.0");

   StringStripper stripper(",");
   vector<string> help1 = stripper.strip(help);
   if (help1.size() == 2) {
      type_    = help1[0];
      version_ = help1[1];
   }
   else {
      type_    = "htpasswd";
      version_ = "1.0";
   }

   user_ = global_.getProperty().getStringProperty("user", "");
   if (log_.trace())  log_.trace(ME, string("constructor: 'user' prop is '") + user_ + "'");

   if (user_ == "") {
      user_ = global_.getProperty().getStringProperty("USER", "unknown");
      if (log_.trace())  log_.trace(ME, string("constructor: 'USER' prop is '") + user_ + "'");
   }

   if (loginName != "") user_ = loginName;
   passwd_ =  global_.getProperty().getStringProperty("passwd", "");
   if (password != "") passwd_ = password;
   if (log_.trace())  log_.trace(ME, string("constructor: user is '") + user_ + "'");
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
