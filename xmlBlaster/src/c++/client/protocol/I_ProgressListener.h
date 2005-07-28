/*------------------------------------------------------------------------------
Name:      I_ProgressListener.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _CLIENT_PROTOCOL_I_PROGRESSLISTENER
#define _CLIENT_PROTOCOL_I_PROGRESSLISTENER

#include <string>

namespace org { namespace xmlBlaster { namespace client { namespace protocol {

/**
 * This interface is used to register listeners about incoming data. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
class Dll_Export I_ProgressListener
{
   public:
      virtual ~I_ProgressListener() {}

      /**
       * Notification about the current data progress. 
       * <p />
       * The interval of notification is arbitrary and not guaranteed,
       * each protocol driver may choose other strategies.
       * @param name A qualifying name about the incoming request, can be empty.
       * @param currBytesRead The number of bytes received up to now
       * @param numBytes The overall number of bytes
       */
      virtual void progress(const std::string& name, unsigned long currBytesRead, unsigned long numBytes) = 0;
};

}}}} // namespaces

#endif
