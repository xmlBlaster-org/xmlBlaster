/*------------------------------------------------------------------------------
Name:      SecurityQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The qos for the security (a subelement of connect qos)
------------------------------------------------------------------------------*/

/**
 * Parse the default security handling with loginName and password
 * from the login qos xml std::string:
 * <pre>
 *  &lt;securityService type="simple" version="1.0">
 *     &lt;user>aUser&lt;/user>
 *     &lt;passwd>theUsersPwd&lt;/passwd>
 *  &lt;/securityService>
 * </pre>
 */
#ifndef _AUTHENTICATION_SECURITY_QOS_H
#define _AUTHENTICATION_SECURITY_QOS_H

#include <util/xmlBlasterDef.h>
#include <util/I_Log.h>
#include <string>

namespace org { namespace xmlBlaster { namespace authentication {

   class Dll_Export SecurityQos
   {
      friend class SecurityQosFactory;
   private:
      const std::string ME;
      org::xmlBlaster::util::Global& global_;
      org::xmlBlaster::util::I_Log& log_;
      std::string       type_;
      std::string       version_;
      std::string       user_;
      std::string       passwd_;

      void copy(const SecurityQos& securityQos)
      {
         type_    = securityQos.type_;
         version_ = securityQos.version_;
         user_    = securityQos.user_;
         passwd_  = securityQos.passwd_;
      }

   public:
      /**
       * @param loginName The authentication user ID
       * @param passwd  The password (for name/password based credential plugins)
       * @param pluginTypeVersion The authentication plugin to be used on server side, for example "htpasswd,1.0"
       */
      SecurityQos(org::xmlBlaster::util::Global& global,
                  const std::string& loginName="",
                  const std::string& password="",
                  const std::string& pluginTypeVersion="");

      SecurityQos(const SecurityQos& securityQos);

      SecurityQos& operator =(const SecurityQos& securityQos);

      std::string getPluginVersion() const;

      std::string getPluginType() const;

      void setUserId(const std::string& userId);

      std::string getUserId() const;

      /**
       * @param cred The password
       */
      void setCredential(const std::string& cred);

      /**
       * @return "" (empty std::string) (no password is delivered)
       */
      std::string getCredential() const;

      /**
       * Dump state of this object into a XML ASCII std::string.
       * <br>
       * @param extraOffset indenting of tags for nice output
       * @return The xml representation
       */
      std::string toXml(const std::string& extraOffset="");
   };

}}} // namespaces

#endif
