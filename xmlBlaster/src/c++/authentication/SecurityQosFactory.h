/*------------------------------------------------------------------------------
Name:      SecurityQosFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The Factory for the simple QosSecurityQos
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

#ifndef _AUTHENTICATION_SECURITYQOSFACTORY_H
#define _AUTHENTICATION_SECURITYQOSFACTORY_H

#include <authentication/SecurityQos.h>

using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace authentication {

   class Dll_Export SecurityQosFactory: public util::SaxHandlerBase
   {
   private:
      const string ME;

      // helper flags for SAX parsing
      bool inSecurityService_;
      bool inUser_;
      bool inPasswd_;

      SecurityQos securityQos_;

   public:
      SecurityQosFactory(Global& global);

      SecurityQos parse(const string& xmlQoS_literal);

      /**
       * Start element, event from SAX parser.
       * <p />
       * @param name Tag name
       * @param attrs the attributes of the tag
       */
      void startElement(const XMLCh* const name, AttributeList& attrs);

      /**
       * End element, event from SAX parser.
       * <p />
       * @param name Tag name
       */

       void endElement(const XMLCh* const name);

   };

}}} // namespaces

#endif
