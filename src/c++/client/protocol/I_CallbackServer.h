/*------------------------------------------------------------------------------
Name:      I_CallbackServer.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface for clients, used by xmlBlaster to send messages back
------------------------------------------------------------------------------*/


/**
 * This is the client callback interface to xmlBlaster.
 * <p />
 * All callback protocol drivers are accessed through these methods.
 * We need it to decouple the protocol specific stuff
 * (like RemoteException from SOCKET or CORBA exceptions) from
 * our c++ client code.
 * <p />
 * Note that you don't need this code, you can access xmlBlaster
 * with your own lowlevel SOCKET or CORBA coding as well.
 *
 * @see org.xmlBlaster.client.protocol.I_XmlBlasterConnection
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>.
 */

#ifndef _CLIENT_PROTOCOL_I_CALLBACKSERVER
#define _CLIENT_PROTOCOL_I_CALLBACKSERVER

#include <util/xmlBlasterDef.h>
#include <string>
#include <client/I_Callback.h>
#include <client/protocol/I_ProgressListener.h>

namespace org { namespace xmlBlaster { namespace client { namespace protocol {

   class Dll_Export I_CallbackServer
   {
   public:

      virtual ~I_CallbackServer() 
      {
      }

      /**
       * Initialize and start the callback server.
       * <p />
       * This is guaranteed to be invoked after the default constructor.
       * @param glob The global handle with your environment settings
       * @param name The login name of the client, for logging only
       * @param client Your implementation to receive the callback messages from xmlBlaster
       */
      virtual void initialize(const std::string& name, org::xmlBlaster::client::I_Callback &client) = 0;

      /**
       * Returns the 'well known' protocol type.
       * @return E.g. "RMI", "SOCKET", "XMLRPC"
       */
      virtual std::string getCbProtocol() = 0;
         
      /**
       * Returns the current callback address.
       * @return "rmi://develop.MarcelRuff.info:1099/xmlBlasterCB", "127.128.2.1:7607", "http://XMLRPC"
       *         or null if not known
       */
      virtual std::string getCbAddress() = 0;
   
      /**
       * Stop the server
       * @return true if everything went fine.
       */
      virtual bool shutdownCb() = 0;

      /**
       * Register a listener for to receive information about the progress of incoming data. 
       * Only one listener is supported, the last call overwrites older calls.
       * @param listener Your listener, pass 0 to unregister.
       * @return The previously registered listener or 0
       */
      virtual org::xmlBlaster::client::protocol::I_ProgressListener* registerProgressListener(org::xmlBlaster::client::protocol::I_ProgressListener *listener) = 0;
};

}}}} // namespaces

#endif
