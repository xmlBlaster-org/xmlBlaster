/*------------------------------------------------------------------------------
Name:      XmlBlasterAccess.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#ifndef _CLIENT_XMLBLASTERACCESS_H
#define _CLIENT_XMLBLASTERACCESS_H

#include <util/xmlBlasterDef.h>
#include <util/qos/ConnectQos.h>
#include <util/MessageUnit.h>
#include <util/dispatch/DeliveryManager.h>
#include <client/protocol/I_CallbackServer.h>
#include <client/protocol/CbServerPluginManager.h>
#include <util/dispatch/ConnectionsHandler.h>
#include <client/I_ConnectionProblems.h>
#include <client/I_Callback.h>
#include <client/xmlBlasterClient.h>
#include <util/Log.h>
#include <string>
#include <vector>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::client::protocol;

namespace org { namespace xmlBlaster { namespace client {

/**
 * <p>
 * The interface I_CallbackRaw/I_Callback/I_CallbackExtenden are enforced by AbstractCallbackExtended
 * is for the InvocationRecorder to playback locally queued messages and for the protocol drivers.
 * </p>
 */

class Dll_Export XmlBlasterAccess : public I_Callback
{
private:
   string ME;
   /** The cluster node id (name) to which we want to connect, needed for nicer logging, can be null */
   string serverNodeId_;
   ConnectQos connectQos_;
   /** The return from connect() */
   ConnectReturnQos connectReturnQos_;
   /** The dispatcher framework **/
   DeliveryManager* deliveryManager_;
   /** The callback server */
   I_CallbackServer* cbServer_;
   /** The connection server for this address */
   ConnectionsHandler* connection_;

   /** Used to callback the clients default update() method (as given on connect()) */
   I_Callback* updateClient_;
   
   /** used to temporarly store the failsafe notification address (if any). Once initFailsafe is called, this
    * pointer is set to NULL again. This way connection_.initFailsafe will be invoked even if the user has
    * called XmlBlasterAccess::initFailsafe before the connection_ member has been created.
    */
   I_ConnectionProblems* connectionProblems_;
   Global& global_;
   Log&    log_;
   string  instanceName_;
   
public:
   /**
    * Create an xmlBlaster accessor. 
    * @param glob Your environment handle or null to use the default Global.instance()
    * @param instanceName is the name to give to this instance of xmlBlasterAccess. It is used to map the
    * connections to a particular instance of XmlBlasterAccess (there will be one connection set 
    * such instance name. This way you can use the same connections for several instances of xmlBlasterAccess
    * provided they all have the same name. This name is also used to identify instances on logging and when
    * throwing exceptions.
    */
   XmlBlasterAccess(Global& global);

   virtual ~XmlBlasterAccess();

   /**
    * Login to xmlBlaster
    * @param qos Your configuration desire
    * @param client If not null callback messages will be routed to client.update()
    */
   ConnectReturnQos connect(const ConnectQos& qos, I_Callback *clientAddr);

   /**
    * Extracts address data from ConnectQos (or adds default if missing)
    * and instantiate a callback server as specified in ConnectQos
    */
   void createDefaultCbServer();

   /**
    * Create a new instance of the desired protocol driver like CORBA or RMI driver using the plugin loader. 
    * @param type  E.g. "IOR" or "RMI", if null we use the same protocol as our client access (corba is default).
    * @param version The version of the driver, e.g. "1.0"
    */
   I_CallbackServer* initCbServer(const string& loginName, const string& type, const string& version);

   /**
    * Initializes the little client helper framework for authentication.
    * <p />
    * The first goal is a proper loginQoS xml string for authentication.
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
   void initSecuritySettings(const string& secMechanism, const string& secVersion);

   /**
    * Logout from the server. 
    * <p>
    * Depending on your arguments, the callback server is removed as well, releasing all CORBA/RMI/XmlRpc threads.
    * Note that this kills the server ping thread as well (if in fail save mode)
    * </p>
    * @param qos The disconnect quality of service
    * @param flush Flushed pending publishOneway() messages if any
    * @param shutdown shutdown lowlevel connection as well (e.g. CORBA connection)
    * @param shutdownCb shutdown callback server as well (if any was established)
    * @return <code>true</code> successfully logged out<br />
    *         <code>false</code> failure on logout
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">interface.disconnect requirement</a>
    */
   bool disconnect(const DisconnectQos& qos, bool flush=true, bool shutdown=true, bool shutdownCb=true);

   /**
    * Create a descriptive ME, for logging only
    * @return e.g. "/node/heron/client/joe/3"
    */
   string getId();

   /**
    * The public session ID of this login session. 
    */
   string getSessionName();

   /**
    * Access the login name.
    * @return your login name or null if you are not logged in
    */
   string getLoginName();

   /**
    * Allows to set the node name for nicer logging.
    */
   void setServerNodeId(const string& nodeId);

   /**
    * The cluster node id (name) to which we want to connect.
    * <p />
    * Needed only for nicer logging when running in a cluster.<br />
    * Is configurable with "-server.node.id golan"
    * @return e.g. "golan", defaults to "xmlBlaster"
    */
   string getServerNodeId() const;

   /**
    * Put the given message entry into the queue
    */
   // MsgQueueEntry queueMessage(const MsgQueueEntry& entry);

   /**
    * Put the given message entry into the queue
    */
   // vector<MsgQueueEntry*> queueMessage(const vector<MsgQueueEntry*>& entries);

   // SubscribeReturnQos
//   string subscribe(const string& xmlKey, const string& qos);
   SubscribeReturnQos subscribe(const SubscribeKey& key, const SubscribeQos& qos);

//   vector<MessageUnit> get(const string&  xmlKey, const string& qos);
   vector<MessageUnit> get(const GetKey& key, const GetQos& qos);

   // UnSubscribeReturnQos[]
//   vector<string> unSubscribe(const string&  xmlKey, const string&  qos);
   vector<UnSubscribeReturnQos> unSubscribe(const UnSubscribeKey& key, const UnSubscribeQos& qos);

   // PublishReturnQos
//   string publish(const MessageUnit& msgUnit);
   PublishReturnQos publish(const MessageUnit& msgUnit);

   void publishOneway(const vector<MessageUnit>& msgUnitArr);

//   vector<string> publishArr(const vector<MessageUnit>& msgUnitArr);
   vector<PublishReturnQos> publishArr(vector<MessageUnit> msgUnitArr);

   // EraseReturnQos[]
//   vector<string> erase(const string& xmlKey, const string& qos);
   vector<EraseReturnQos> erase(const EraseKey& key, const EraseQos& qos);


   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message.
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   string update(const string &sessionId, UpdateKey &updateKey, void *content, long contentSize, UpdateQos &updateQos);

   /**
    * Command line usage.
    */
   static void usage();

   /**
    * used to initialize the failsafe behaviour of the client.
    * If connectionProblems is not NULL, then the passed object will be notified for connection lost
    * and reconnected events.
    */
    void initFailsafe(I_ConnectionProblems* connectionProblems=NULL);

    string ping();

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
