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

/**
 * <p>
 * The interface org::xmlBlaster::client::I_CallbackRaw/I_Callback/I_CallbackExtenden are enforced by AbstractCallbackExtended
 * is for the InvocationRecorder to playback locally queued messages and for the protocol drivers.
 * </p>
 */

typedef std::map<std::string, I_Callback*> CallbackMapType;

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

   bool isConnected() const;

};

}}} // namespaces

#endif
