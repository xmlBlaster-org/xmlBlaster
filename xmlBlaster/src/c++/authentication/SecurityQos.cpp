/*------------------------------------------------------------------------------
Name:      SecurityQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The qos for the security (a subelement of connect qos)
------------------------------------------------------------------------------*/
#include <authentication/SecurityQos.h>
#include <string>

namespace org { namespace xmlBlaster { namespace authentication {

SecurityQos::SecurityQos(Global& global,
                         const string& loginName,
                         const string& password)
   : ME("SecurityQos-simple"), global_(global)
{
   type_    = "simple";
   version_ = "1.0";
   user_   = loginName;
   passwd_ = password;
}

SecurityQos::SecurityQos(const SecurityQos& securityQos)
   : ME("SecurityQos-simple"), global_(securityQos.global_)
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

