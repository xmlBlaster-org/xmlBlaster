/*------------------------------------------------------------------------------
Name:      I_XmlBlasterAccess.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.I_ConnectionHandler;
import org.xmlBlaster.client.I_ConnectionStateListener;


/**
 * The Java client side access to xmlBlaster. 
 * This interface hides a remote connection or a native connection. 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html">interface requirement</a>
 */
public interface I_XmlBlasterAccess extends I_XmlBlaster, I_ConnectionHandler
{
   /**
    * Initialize and configure fail safe connection, with this the client library
    * automatically polls for the xmlBlaster server. 
    * @param address The configuration of the client connection
    * @param connectionListener null or your listener implementation on connection state changes (ALIVE | POLLING | DEAD)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsafe.html">client.failsafe requirement</a>
    */
   void registerConnectionListener(I_ConnectionStateListener connectionListener);

   /**
    * Login to xmlBlaster. 
    * <p>
    * Connecting with the default configuration (which checks xmlBlaster.properties and
    * your command line arguments):
    * </p>
    * <pre>
    *  I_XmlBlasterAccess xmlBlasterAccess = glob.getXmlBlasterAccess();
    *  xmlBlasterAccess.connect(null, null);
    * </pre>
    * <p>
    * The default behavior is to poll automatically for the server if it is not found.
    * As we have not specified a listener for returned messages from the server there
    * is no callback server created. 
    * </p>
    * <p>
    * This example shows how to configure different behavior:
    * </p>
    * <pre>
    *  // Example how to configure fail safe settings
    *  ConnectQos connectQos = new ConnectQos(glob);
    *
    *  Address address = new Address(glob);
    *  address.setDelay(4000L);      // retry connecting every 4 sec
    *  address.setRetries(-1);       // -1 == forever
    *  address.setPingInterval(0L);  // switched off
    *  addr.setType("SOCKET");       // don't use CORBA protocol, but use SOCKET instead
    *
    *  connectQos.setAddress(address);
    *
    *  CallbackAddress cbAddress = new CallbackAddress(glob);
    *  cbAddress.setDelay(4000L);      // retry connecting every 4 sec
    *  cbAddress.setRetries(-1);       // -1 == forever
    *  cbAddress.setPingInterval(4000L); // ping every 4 seconds
    *  connectQos.addCallbackAddress(cbAddress);
    *  
    *  xmlBlasterAccess.connect(connectQos, new I_Callback() {
    *
    *     public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
    *                          UpdateQos updateQos) {
    *        if (updateKey.isInternal()) {
    *           return "";
    *        }
    *        if (updateQos.isErased()) {
    *           return "";
    *        }
    *        log.info(ME, "Receiving asynchronous message '" + updateKey.getOid() +
    *                     "' state=" + updateQos.getState() + " in default handler");
    *        return "";
    *     }
    *
    *  });  // Login to xmlBlaster, default handler for updates;
    * </pre>
    * @param qos Your configuration desire
    * @param updateListener If not null a callback server will be created and 
    *        callback messages will be routed to your updateListener.update() method. 
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">interface.connect requirement</a>
    */
   ConnectReturnQos connect(ConnectQos qos, I_Callback updateListener) throws XmlBlasterException;
      
   /**
    * Create a new instance of the desired protocol driver like CORBA or RMI driver using the plugin loader. 
    * <p>
    * Note that the returned instance is of your control only, we don't cache it in any way, this
    * method is only a helper hiding the plugin loading.
    * </p>
    * @param type  E.g. "IOR" or "RMI", if null we use the same protocol as our client access (corba is default).
    * @param version The version of the driver, e.g. "1.0"
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.html">protocol requirement</a>
    */
   I_CallbackServer initCbServer(String loginName, String type, String version) throws XmlBlasterException;

   /**
    * Access the client side security plugin. 
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/security.introduction.html">security.introduction requirement</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/security.development.serverPlugin.howto.html">security.development.serverPlugin.howto requirement</a>
    */
   I_ClientPlugin getSecurityPlugin();

   /**
    * Logout from the server.
    * <p />
    * Flushes pending publishOneway messages if any and destroys low level connection and callback server.
    * @see org.xmlBlaster.client.XmlBlasterAccess#disconnect(DisconnectQos, boolean, boolean, boolean)
    */
   boolean disconnect(DisconnectQos qos);

   /**
    * Note that this contains no information about the current connection state. 
    * @return true If the connection() method was invoked without exception
    * @see I_ConnectionHandler#isAlive()
    * @see I_ConnectionHandler#isPolling()
    * @see I_ConnectionHandler#isDead()
    */
   boolean isConnected();

   /**
    * Access the returned QoS of a connect() call. 
    * @return is null if connect() was not called before
    */
   ConnectReturnQos getConnectReturnQos();

   /**
    * Access the current ConnectQos.
    * @return is null if connect() was not called before
    */
   ConnectQos getConnectQos();

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
   boolean disconnect(DisconnectQos disconnectQos, boolean flush, boolean shutdown, boolean shutdownCb);

   /**
    * Access the callback server which is currently used in I_XmlBlasterAccess. 
    * @return null if no callback server is established
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.html">protocol requirement</a>
    */
   I_CallbackServer getCbServer();

   /**
    * Create a descriptive ME, for logging only
    * @return e.g. "/node/heron/client/joe/3"
    */
   String getId();

   /**
    * The public session ID of this login session. 
    * This is a convenience method only, the information is from ConnectReturnQos or if not available
    * from ConnectQos.
    * @return null if not known
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsafe.html">client.failsafe requirement</a>
    */
   SessionName getSessionName();

   /**
    * Allows to set the node name for nicer logging. 
    * Used for clustering.
    */
   void setServerNodeId(String nodeId);

   /**
    * The cluster node id (name) to which we want to connect.
    * <p />
    * Needed only for nicer logging when running in a cluster.<br />
    * Is configurable with "-server.node.id golan"
    * @return e.g. "golan", defaults to "xmlBlaster"
    */
   String getServerNodeId();

   //SubscribeReturnQos subscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">interface.subscribe requirement</a>
    */
   SubscribeReturnQos subscribe(SubscribeKey subscribeKey, SubscribeQos subscribeQos) throws XmlBlasterException;

   /**
    * This subscribe variant allows to specify a specialized callback
    * for updated messages.
    * <p />
    * This way you can implement for every subscription a specific callback,
    * so you don't need to dispatch updates when they are received in only one central
    * update method.
    * <p />
    * Example:<br />
    * <pre>
    *   XmlBlasterAccess con = ...   // login etc.
    *   ...
    *   SubscribeKey key = new SubscribeKey(glob, "//stock", "XPATH");
    *   SubscribeQos qos = new SubscribeQos(glob);
    *   try {
    *      con.subscribe(key, qos, new I_Callback() {
    *            public String update(String name, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
    *               System.out.println("Receiving message for '//stock' subscription ...");
    *               return "";
    *            }
    *         });
    *   } catch(XmlBlasterException e) {
    *      System.out.println(e.getMessage());
    *   }
    * </pre>
    * <p />
    * NOTE: You need to pass a callback handle on login as well (even if you
    * never use it). It allows to setup the callback server and is the
    * default callback deliver channel for PtP messages.
    * <p />
    * NOTE: On logout we automatically unSubscribe() this subscription
    * if not done before.
    * @param cb      Your callback handling implementation
    * @return SubscribeReturnQos with the unique subscriptionId<br>
    *                If you subscribed using a query, the subscription ID of this<br>
    *                query handling object (SubscriptionInfo.getUniqueKey()) is returned.<br>
    *                You should use this ID if you wish to unSubscribe()
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">interface.subscribe requirement</a>
    */
   SubscribeReturnQos subscribe(SubscribeKey subscribeKey, SubscribeQos subscribeQos, I_Callback cb) throws XmlBlasterException;

   /**
    * @see I_XmlBlasterAccess#subscribe(SubscribeKey, SubscribeQos, I_Callback)
    */
   SubscribeReturnQos subscribe(String xmlKey, String xmlQos, I_Callback cb) throws XmlBlasterException;

   //MsgUnit[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html">interface.get requirement</a>
    */
   MsgUnit[] get(GetKey getKey, GetQos getQos) throws XmlBlasterException;

   //UnSubscribeReturnQos[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html">interface.unSubscribe requirement</a>
    */
   UnSubscribeReturnQos[] unSubscribe(UnSubscribeKey unSubscribeKey, UnSubscribeQos unSubscribeQos) throws XmlBlasterException;

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">interface.publish requirement</a>
    */
   PublishReturnQos publish(MsgUnit msgUnit) throws XmlBlasterException;
   //Rename publishArr() to publish
   //PublishReturnQos[] publish(org.xmlBlaster.util.MsgUnit[] msgUnitArr) throws XmlBlasterException;
   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">interface.publish requirement</a>
    */
   void publishOneway(org.xmlBlaster.util.MsgUnit [] msgUnitArr) throws XmlBlasterException;

   //EraseReturnQos[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html">interface.erase requirement</a>
    */
   EraseReturnQos[] erase(EraseKey eraseKey, EraseQos eraseQos) throws XmlBlasterException;

   /**
    * Access the environment settings of this connection. 
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return The global handle (like a stack with local variables for this connection)
    */
   Global getGlobal();

   /**
    * Dump state of this client connection handler into an XML ASCII string.
    * @return internal state
    */
   String toXml();
}
