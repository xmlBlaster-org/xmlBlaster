/*------------------------------------------------------------------------------
Name:      SecurityQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The qos for the security (a subelement of connect qos)
------------------------------------------------------------------------------*/

/**
 * Parse the default security handling with loginName and password
 * from the login qos xml string:
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
#include <string>
#include <util/SaxHandlerBase.h>
#include <util/StringTrim.h>


using namespace org::xmlBlaster::util; //<-- VC CRASH

namespace org { namespace xmlBlaster { namespace authentication {

   class Dll_Export SecurityQos
   {
      friend class SecurityQosFactory;
   private:
      const string ME;
      Global&      global_;
      string       type_;
      string       version_;
      string       user_;
      string       passwd_;

      void copy(const SecurityQos& securityQos)
      {
         type_    = securityQos.type_;
         version_ = securityQos.version_;
         user_    = securityQos.user_;
         passwd_  = securityQos.passwd_;
      }

   public:
      SecurityQos(Global& global,
                  const string& loginName="",
                  const string& password="");

      SecurityQos(const SecurityQos& securityQos);

      SecurityQos& operator =(const SecurityQos& securityQos);

      string getPluginVersion() const;

      string getPluginType() const;

      void setUserId(const string& userId);

      string getUserId() const;

      /**
       * @param cred The password
       */
      void setCredential(const string& cred);

      /**
       * @return "" (empty string) (no password is delivered)
       */
      string getCredential() const;

      /**
       * Dump state of this object into a XML ASCII string.
       * <br>
       * @param extraOffset indenting of tags for nice output
       * @return The xml representation
       */
      string toXml(const string& extraOffset="");
   };

}}} // namespaces

#endif
