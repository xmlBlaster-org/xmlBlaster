/*------------------------------------------------------------------------------
Name:      XmlBlasterAccess.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _CLIENT_XMLBLASTERACCESS_H
#define _CLIENT_XMLBLASTERACCESS_H

#include <util/xmlBlasterDef.h>
#include <util/Global.h>
#include <util/qos/ConnectQos.h>
#include <client/I_ConnectionProblems.h>
#include <client/I_Callback.h>
#include <util/thread/ThreadImpl.h>
#include <util/ReferenceCounterBase.h>
#include <util/ReferenceHolder.h>
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
 * The interface org::xmlBlaster::client::I_CallbackRaw/I_Callback/I_CallbackExtended are enforced by AbstractCallbackExtended
 * is for the protocol drivers.
 */
typedef std::map<std::string, I_Callback*> CallbackMapType;
typedef std::map<std::string, std::string> StringMap;

/**
 * This is the main entry point for programmers to the C++ client library. 
 * 
 * Exactly one Global instance and one instance of this are a pair which can't be
 * mixed with other instances. 
 */
class Dll_Export XmlBlasterAccess : public org::xmlBlaster::client::I_Callback,
                                    public org::xmlBlaster::util::ReferenceCounterBase
{
private:
   std::string ME;

   org::xmlBlaster::util::Global&   global_;
   org::xmlBlaster::util::GlobalRef globalRef_;
   org::xmlBlaster::util::I_Log&    log_;
   std::string  instanceName_;

   /** The cluster node id (name) to which we want to connect, needed for nicer logging, can be null */
   std::string serverNodeId_;
   org::xmlBlaster::util::qos::ConnectQosRef connectQos_;
   /** The return from connect() */
   org::xmlBlaster::util::qos::ConnectReturnQosRef connectReturnQos_;
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

   void cleanup(bool doLock);
   
public:
   /**
    * Create an xmlBlaster accessor. 
    * @param glob Your environment handle or null to use the default org::xmlBlaster::util::Global.instance()
    */
   XmlBlasterAccess(org::xmlBlaster::util::Global& global);

   /**
    * Create an xmlBlaster accessor. 
    * @param glob Your environment handle or null to use the default org::xmlBlaster::util::Global.instance()
    */
   XmlBlasterAccess(org::xmlBlaster::util::GlobalRef global);

   virtual ~XmlBlasterAccess();

   /**
    * Access the global handle of this connection. 
    * The returned Global lifetime is limited by XmlBlasterAccess lifetime.
    * @return The global handle containing the connection specific settings. 
    */
   org::xmlBlaster::util::Global& getGlobal();

   /**
    * Access the client side queue
    * @return null if not configured
    */
   org::xmlBlaster::util::queue::I_Queue* getQueue();

   /**
    * Login to xmlBlaster. 
    * Calling multiple times for changed connections should be possible but is not deeply tested.
    * @param qos Your configuration desire
    * @param client If not null callback messages will be routed to client.update()
    * @return The returned QOS for this connection
    */
   org::xmlBlaster::util::qos::ConnectReturnQos connect(const org::xmlBlaster::util::qos::ConnectQos& qos, org::xmlBlaster::client::I_Callback *clientCb);

   /**
    * Access the current ConnectQos instance. 
    * @return A reference on ConnectQos, you don' need to take care on new/delete, just use it.
    *         Changes made on the instance are seen in the library as well.
    */
   org::xmlBlaster::util::qos::ConnectQosRef getConnectQos();
   org::xmlBlaster::util::qos::ConnectReturnQosRef getConnectReturnQos();

   /**
    * Access the previously with connect() registered callback pointer. 
    * @return Can be NULL
    */
   org::xmlBlaster::client::I_Callback* getCallback();

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
    * Register a listener for to receive information about the progress of incoming data. 
    * Only one listener is supported, the last call overwrites older calls.
    * @param listener Your listener, pass 0 to unregister.
    * @return The previously registered listener or 0
    */
   org::xmlBlaster::client::protocol::I_ProgressListener* registerProgressListener(org::xmlBlaster::client::protocol::I_ProgressListener *listener);

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
    * Your changes outside change the internal sessionName. 
    * @return A reference counted SessionName. 
    */
   org::xmlBlaster::util::SessionNameRef getSessionNameRef();

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

   /**
    * This method synchronously accesses maxEntries messages from any xmlBlaster server side queue.
    * <p>
    * This is a convenience method which uses get() with a specific Qos.
    * <p>Important note:<br />
    * Currently you shouldn't use unlimited timeout==-1 as this could
    * lead to a server side thread leak on client disconnect.
    * As a workaround please use a loop and a timeout of for example 60000
    * and just ignore returned arrays of length 0.
    * </p>
    * @param oid The identifier like 
    *            "topic/hello" to access a history queue,
    *            "client/joe" to access a subject queue or
    *            "client/joe/session/1"
    *            to access a callback queue.
    *            The string must follow the formatting rule of ContextNode.java
    * @param maxEntries The maximum number of entries to retrieve
    * @param timeout The time to wait until return. 
    *                If you choose a negative value it will block until the maxEntries
    *                has been reached.
    *                If the value is '0' (i.e. zero) it will not wait and will correspond to a non-blocking get.
    *                If the value is positive it will block until the specified amount in milliseconds
    *                has elapsed or when the maxEntries has been reached (whichever comes first). 
    * @param consumable  Expressed with 'true' or 'false'.
    *                    If true the entries returned are deleted from the queue
    * @return An array of messages, is never null but may be an array of length=0 if no message is delivered
    * @see org.xmlBlaster.util.context.ContextNode
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.queryspec.QueueQuery.html">engine.qos.queryspec.QueueQuery requirement</a>
    * @see javax.jms.MessageConsumer#receive
    */
   std::vector<org::xmlBlaster::util::MessageUnit> receive(std::string oid, int maxEntries, long timeout, bool consumable);


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
    * Switch callback dispatcher on/off. 
    * This is a convenience function (see ConnectQos). It will update the client side
    * ConnectQos as well so we don't loose the setting on reconnects after server maintenance.
    * @param isActive true: XmlBlaster server delivers callback messages
    *        false: XmlBlaster server keeps messages for this client in the callback queue
    */
   void setCallbackDispatcherActive(bool isActive);

   /**
    * Convenience method to send an administrative command to xmlBlaster. 
    * If the command contains a '=' it is interpreted as a set() call, else it is used as
    * a get() call.
    * @param command for example "client/joe/?dispatcherActive" (a getter) or "client/joe/?dispatcherActive=false" (a setter).
    *        The "__cmd:" is added by us
    *        To enforce a getter or setter you can write "get client/joe/?dispatcherActive" or
    *        "set client/joe/?dispatcherActive=false"
    * @return When setting a value you get the returned state, else the retrieved data
    * @throws XmlBlasterException on problems
    */
   std::string sendAdministrativeCommand(const std::string &command);

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

typedef org::xmlBlaster::util::ReferenceHolder<org::xmlBlaster::client::XmlBlasterAccess> XmlBlasterAccessRef;

}}} // namespaces

#endif
