/*-----------------------------------------------------------------------------
Name:      I_Timeout.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
-----------------------------------------------------------------------------*/

#ifndef _UTIL_I_TIMEOUT_H
#define _UTIL_I_TIMEOUT_H

namespace org { namespace xmlBlaster { namespace util {

/**
 * Abstract class you need to implement to receive timeout notifications
 * see the omonimous I_Timeout.java.
 *
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */
class I_Timeout {
   public:
      /**
      * You will be notified about the timeout through this method.
      * @param userData You get bounced back your userData which you passed
      *                 with Timeout.addTimeoutListener()
      */
      virtual void timeout(void *userData) = 0;
};

}}}; // namespaces

#endif
