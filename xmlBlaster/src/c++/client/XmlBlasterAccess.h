/*------------------------------------------------------------------------------
Name:      XmlBlasterAccess.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _CLIENT_XMLBLASTERACCESS_H
#define _CLIENT_XMLBLASTERACCESS_H

#include <util/xmlBlasterDef.h>
#include <util/qos/ConnectQos.h>
#include <client/I_ConnectionProblems.h>
#include <client/I_Callback.h>
#include <util/thread/ThreadImpl.h>
#include <string>
#include <vector>
#include <map>

/* The following comment is used by doxygen for the main html page: */
/*! \mainpage Hints about the C++ client library usage.
 *
 * \section intro_sec The C++ client library
 *
 * The xmlBlaster C++ client library supports access to xmlBlaster with asynchronous callbacks,
 * client side queuing and fail safe reconnect using the CORBA or SOCKET protocol plugin.
 * Details about compilation and its usage can be found in the 
 * http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.cpp.html requirement.
 *
 * As a C++ developer your entry point to use is the class org::xmlBlaster::client::XmlBlasterAccess and
 * a complete overview demo code is HelloWorld2.cpp
 *
 * \section c_sec The C client library
 * The C client library offers some basic functionality like the SOCKET protocol with
 * the struct #XmlBlasterAccessUnparsed or persistent queues with struct #I_Queue.
 * These features are heavily used by the C++ library.
 * If you need a tiny xmlBlaster access you can choose to use the C client library directly
 * without any C++ code.
 *
 * For details read the
 * http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html requirement and the
 * http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.queue.html requirement.
 * and look at the API documentation at http://www.xmlblaster.org/xmlBlaster/doc/doxygen/c/html/index.html
 */

// Note: I_ConnectionProblems.h includes I_ConnectionsHandler.h includes I_XmlBlasterConnection.h
//       which includes all EraseQos, SubscribeKey etc.
//       -> We could try to avoid this by forward declaration, but all cpp files must
//          then include them thereselves.

// Declare classes without the need to include them in this header file
namespace org { namespace xmlBlaster { namespace util {
   class MessageUnit;
}}}
namespace org { namespace xmlBlaster { namespace util { namespace dispatch {
   class DispatchManager;
   class ConnectionsHandler;
}}}}
namespace org { namespace xmlBlaster { namespace client { namespace protocol {
   class I_CallbackServer;
}}}}

namespace org { namespace xmlBlaster { namespace client {

/*
 * <p>
 * The interface org::xmlBlaster::client::I_CallbackRaw/I_Callback/I_CallbackExtenden are enforced by AbstractCallbackExtended
 * is for the InvocationRecorder to playback locally queued messages and for the protocol drivers.
 * </p>
 */

typedef std::map<std::string, I_Callback*> CallbackMapType;
typedef std::map<std::string, std::string> StringMap;

/**
 * This is the main entry point for programmers to the C++ client library. 
 */
class Dll_Export XmlBlasterAccess : public org::xmlBlaster::client::I_Callback
{
private:
   std::string ME;
   /** The cluster node id (name) to which we want to connect, needed for nicer logging, can be null */
   std::string serverNodeId_;
   org::xmlBlaster::util::qos::ConnectQos connectQos_;
   /** The return from connect() */
   org::xmlBlaster::util::qos::ConnectReturnQos connectReturnQos_;
   /** The dispatcher framework **/
   org::xmlBlaster::util::dispatch::DispatchManager* dispatchManager_;
   /** The callback server */
   org::xmlBlaster::client::protocol::I_CallbackServer* cbServer_;
   /** The connection server for this address */
   org::xmlBlaster::util::dispatch::ConnectionsHandler* connection_;

   /** Used to callback the clients default update() method (as given on connect()) */
   org::xmlBlaster::client::I_Callback* updateClient_;
   
   /** 
    * Used to temporary store the fail safe notification address (if any). Once initFailsafe is called, this
    * pointer is set to NULL again. This way connection_.initFailsafe will be invoked even if the user has
    * called XmlBlasterAccess::initFailsafe before the connection_ member has been created.
    */
   org::xmlBlaster::client::I_ConnectionProblems* connectionProblems_;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log&    log_;
   std::string  instanceName_;
   CallbackMapType subscriptionCallbackMap_;
   org::xmlBlaster::util::thread::Mutex updateMutex_;
   /** this makes sure only one invocation is done at a time on this connection. The update method is not blocked by this mutex. The shutdown is blocked */
   org::xmlBlaster::util::thread::Mutex invocationMutex_;

   /**
    * Private copy constructor, clones are not supported
    */
   XmlBlasterAccess(const XmlBlasterAccess &global);

   /**
    * Private assignment operator, clones are not supported
    */
   XmlBlasterAccess& operator =(const XmlBlasterAccess &);
   
public:
   /**
    * Create an xmlBlaster accessor. 
    * @param glob Your environment handle or null to use the default org::xmlBlaster::util::Global.instance()
    * @param instanceName is the name to give to this instance of xmlBlasterAccess. It is used to std::map the
    * connections to a particular instance of XmlBlasterAccess (there will be one connection set 
    * such instance name. This way you can use the same connections for several instances of xmlBlasterAccess
    * provided they all have the same name. This name is also used to identify instances on logging and when
    * throwing exceptions.
    */
   XmlBlasterAccess(org::xmlBlaster::util::Global& global);

   virtual ~XmlBlasterAccess();

   /**
    * Login to xmlBlaster
    * @param qos Your configuration desire
    * @param client If not null callback messages will be routed to client.update()
    */
   org::xmlBlaster::util::qos::ConnectReturnQos connect(const org::xmlBlaster::util::qos::ConnectQos& qos, org::xmlBlaster::client::I_Callback *clientCb);

   /**
    * Extracts address data from org::xmlBlaster::util::qos::ConnectQos (or adds default if missing)
    * and instantiate a callback server as specified in org::xmlBlaster::util::qos::ConnectQos
    */
   void createDefaultCbServer();

   /**
    * Create a new instance of the desired protocol driver like CORBA or RMI driver using the plugin loader. 
    * @param type  E.g. "IOR" or "SOCKET", if null we use the same protocol as our client access.
    * @param version The version of the driver, e.g. "1.0"
    */
   org::xmlBlaster::client::protocol::I_CallbackServer* initCbServer(const std::string& loginName, const std::string& type, const std::string& version);

   /**
    * Initializes the little client helper framework for authentication.
    * <p />
    * The first goal is a proper loginQoS xml std::string for authentication.
    * <p />
    * The second goal is to intercept the messages for encryption (or whatever the
    * plugin supports).
    * <p />
    * See xmlBlaster.properties, for example:
    * <pre>
    *   Security.Client.DefaultPlugin=gui,1.0
    *   Security.Client.Plugin[gui][1.0]=org.xmlBlaster.authentication.plugins.gui.ClientSecurityHelper
    * </pre>
    */
   void initSecuritySettings(const std::string& secMechanism, const std::string& secVersion);

   /**
    * Logout from the server. 
    * <p>
    * Depending on your arguments, the callback server is removed as well, releasing all CORBA/RMI/XmlRpc threads.
    * Note that this kills the server ping thread as well (if in failsafe mode)
    * </p>
    * @param qos The disconnect quality of service
    * @param flush Flushed pending publishOneway() messages if any
    * @param shutdown shutdown lowlevel connection as well (e.g. CORBA connection)
    * @param shutdownCb shutdown callback server as well (if any was established)
    * @return <code>true</code> successfully logged out<br />
    *         <code>false</code> failure on logout
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">interface.disconnect requirement</a>
    */
   bool disconnect(const org::xmlBlaster::util::qos::DisconnectQos& qos, bool flush=true, bool shutdown=true, bool shutdownCb=true);

   /**
    * Create a descriptive ME, for logging only
    * @return e.g. "/node/heron/client/joe/3"
    */
   std::string getId();

   /**
    * The public session ID of this login session. 
    */
   std::string getSessionName();

   /**
    * Access the login name.
    * @return your login name or null if you are not logged in
    */
   std::string getLoginName();

   /**
    * Allows to set the node name for nicer logging.
    */
   void setServerNodeId(const std::string& nodeId);

   /**
    * The cluster node id (name) to which we want to connect.
    * <p />
    * Needed only for nicer logging when running in a cluster.<br />
    * Is configurable with "-server.node.id golan"
    * @return e.g. "golan", defaults to "xmlBlaster"
    */
   std::string getServerNodeId() const;

   /**
    * Put the given message entry into the queue
    */
   // org::xmlBlaster::util::queue::MsgQueueEntry queueMessage(const org::xmlBlaster::util::queue::MsgQueueEntry& entry);

   /**
    * Put the given message entry into the queue
    */
   // std::vector<MsgQueueEntry*> queueMessage(const std::vector<MsgQueueEntry*>& entries);

   // org::xmlBlaster::client::qos::SubscribeReturnQos
//   std::string subscribe(const std::string& xmlKey, const std::string& qos);
   org::xmlBlaster::client::qos::SubscribeReturnQos subscribe(const org::xmlBlaster::client::key::SubscribeKey& key, const org::xmlBlaster::client::qos::SubscribeQos& qos, I_Callback *callback=0);

//   std::vector<org::xmlBlaster::util::MessageUnit> get(const std::string&  xmlKey, const std::string& qos);
   std::vector<org::xmlBlaster::util::MessageUnit> get(const org::xmlBlaster::client::key::GetKey& key, const org::xmlBlaster::client::qos::GetQos& qos);

   // org::xmlBlaster::client::qos::UnSubscribeReturnQos[]
//   std::vector<std::string> unSubscribe(const std::string&  xmlKey, const std::string&  qos);
   std::vector<org::xmlBlaster::client::qos::UnSubscribeReturnQos> unSubscribe(const org::xmlBlaster::client::key::UnSubscribeKey& key, const org::xmlBlaster::client::qos::UnSubscribeQos& qos);

   // org::xmlBlaster::client::qos::PublishReturnQos
//   std::string publish(const org::xmlBlaster::util::MessageUnit& msgUnit);
   org::xmlBlaster::client::qos::PublishReturnQos publish(const org::xmlBlaster::util::MessageUnit& msgUnit);

   void publishOneway(const std::vector<org::xmlBlaster::util::MessageUnit>& msgUnitArr);

//   std::vector<std::string> publishArr(const std::vector<org::xmlBlaster::util::MessageUnit>& msgUnitArr);
   std::vector<org::xmlBlaster::client::qos::PublishReturnQos> publishArr(const std::vector<org::xmlBlaster::util::MessageUnit> &msgUnitArr);

   // org::xmlBlaster::client::qos::EraseReturnQos[]
//   std::vector<std::string> erase(const std::string& xmlKey, const std::string& qos);
   std::vector<org::xmlBlaster::client::qos::EraseReturnQos> erase(const org::xmlBlaster::client::key::EraseKey& key, const org::xmlBlaster::client::qos::EraseQos& qos);


   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message.
    * @see org.xmlBlaster.client.I_Callback#update(String, org::xmlBlaster::client::key::UpdateKey, byte[], org::xmlBlaster::client::qos::UpdateQos)
    */
   std::string update(const std::string &sessionId, org::xmlBlaster::client::key::UpdateKey &updateKey, const unsigned char *content, long contentSize, org::xmlBlaster::client::qos::UpdateQos &updateQos);

   /**
    * Command line usage.
    */
   static std::string usage();

   /**
    * used to initialize the failsafe behaviour of the client.
    * If connectionProblems is not NULL, then the passed object will be notified for connection lost
    * and reconnected events.
    */
    void initFailsafe(I_ConnectionProblems* connectionProblems=NULL);

    std::string ping();

   /**
    * Flushes all entries in the queue, i.e. the entries of the queue are sent to xmlBlaster.
    * If the queue is empty or NULL, then 0 is returned. If the state is in POLLING or DEAD, then -1 is
    * returned.. This method blocks until all entries in the queue have been sent.
    */
   long flushQueue();

   /**
    * Same as isAlive() || isPolling()
    * @return true if connect() call was successful, even if we are polling
    */
   bool isConnected() const;

   /**
    * Check if we are 'online'. 
    * @return true if connected with server and ready
    */
   bool isAlive() const;

   /**
    * Check if we are polling for the server. 
    * @return true if polling for the server
    */
   bool isPolling() const;

   /**
    * Check if this handle is still useful
    * @return true if we have given up
    */
   bool isDead() const;

   /**
    * Get connection status string for logging. 
    * @return "ALIVE" | "POLLING" | "DEAD"
    */
   std::string getStatusString() const;

   /**
    * Disconnect and cleanup client side resources but keep our login session on server side. 
    * <p>
    * As the login session on server side stays alive, all subscriptions stay valid
    * and callback messages are queued by the server.
    * If you connect at a later time the server sends us all queued messages.
    * </p>
    * <p>
    * Once you have called this method the XmlBlasterAccess instance
    * becomes invalid and any further invocation results in 
    * an XmlBlasterException to be thrown.
    * </p>
    * @param map The properties to pass while leaving server.
    *        Currently this argument has no effect.
    */
   void leaveServer(const StringMap &map);
};

}}} // namespaces

#endif
