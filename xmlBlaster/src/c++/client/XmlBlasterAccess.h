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
#include <util/queue/MsgQueueEntry.h>
#include <util/dispatch/DeliveryManager.h>
#include <client/protocol/I_CallbackServer.h>
#include <client/protocol/CbServerPluginManager.h>
#include <client/protocol/I_XmlBlasterConnection.h>
#include <client/I_Callback.h>

#include <util/Log.h>
#include <util/Global.h>
#include <string>
#include <vector>

using org::xmlBlaster::util::MessageUnit;
using org::xmlBlaster::util::dispatch::DeliveryManager;

namespace org { namespace xmlBlaster { namespace client {

/**
 * <p>
 * The interface I_CallbackRaw/I_Callback/I_CallbackExtenden are enforced by AbstractCallbackExtended
 * is for the InvocationRecorder to playback locally queued messages and for the protocol drivers.
 * </p>
 */

using org::xmlBlaster::util::qos::ConnectQos;
using org::xmlBlaster::util::qos::ConnectReturnQos;
using namespace org::xmlBlaster::client::protocol;
using org::xmlBlaster::util::Log;
using org::xmlBlaster::util::MessageUnit;

class XmlBlasterAccess : public I_Callback
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
   I_XmlBlasterConnection* connection_;

   /** Used to callback the clients default update() method (as given on connect()) */
   I_Callback* updateClient_;

   Global& global_;
   Log&    log_;

public:
   /**
    * Create an xmlBlaster accessor. 
    * @param glob Your environment handle or null to use the default Global.instance()
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
    * <p />
    * Flushes pending publishOneway messages if any and destroys low level connection and callback server.
    * @see org.xmlBlaster.client.protocol.XmlBlasterConnection#disconnect(DisconnectQos, boolean, boolean, boolean)
    */
   bool disconnect(const string& qos);

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
   bool disconnect(const string& qos, bool flush, bool shutdown, bool shutdownCb);

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
   MsgQueueEntry queueMessage(const MsgQueueEntry& entry);

   /**
    * Put the given message entry into the queue
    */
   vector<MsgQueueEntry*> queueMessage(const vector<MsgQueueEntry*>& entries);

   // SubscribeReturnQos
   string subscribe(const string& xmlKey, const string& qos);

   vector<MessageUnit> get(const string&  xmlKey, const string& qos);

   // UnSubscribeReturnQos[]
   vector<string> unSubscribe(const string&  xmlKey, const string&  qos);

   // PublishReturnQos
   string publish(const MessageUnit& msgUnit);

   void publishOneway(const vector<MessageUnit>& msgUnitArr);

   vector<string> publishArr(const vector<MessageUnit>& msgUnitArr);

   // EraseReturnQos[]
   vector<string> erase(const string& xmlKey, const string& qos);

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message.
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   string update(const string &sessionId, UpdateKey &updateKey, void *content, long contentSize, UpdateQos &updateQos);

   /**
    * Command line usage.
    */
   void usage();
};

}}} // namespaces

#endif
