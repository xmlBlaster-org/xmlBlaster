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
    * Login to xmlBlaster
    * <pre>
    *  ConnectQos qos = new ConnectQos(glob);
    *
    *  // Example how to configure fail safe settings
    *  Address addr = new Address(glob);
    *  addr.setDelay(2000L);
    *  addr.setRetries(-1);
    *  addr.setMaxMsg(2000);
    *  addr.setPingInterval(5000L);
    *  qos.addAddress(addr);
    *
    * </pre>
    * @param qos Your configuration desire
    * @param updateListener If not null a callback server will be created and 
    *        callback messages will be routed to your updateListener.update() method. 
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">interface.connect requirement</a>
    */
   ConnectReturnQos connect(ConnectQos qos, I_Callback updateListener) throws XmlBlasterException;
      
   /**
    * Create a new instance of the desired protocol driver like CORBA or RMI driver using the plugin loader. 
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
    * @see org.xmlBlaster.client.protocol.XmlBlasterConnection#disconnect(DisconnectQos, boolean, boolean, boolean)
    */
   boolean disconnect(DisconnectQos qos);

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
    * Access the callback server. 
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
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsafe.html">client.failsafe requirement</a>
    */
   SessionName getSessionName();

   /**
    * Allows to set the node name for nicer logging.
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
    * Implementing for every subscription a callback, you don't need to
    * dispatch updates when they are received in one central
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
    * default callback deliver channel.
    * <p />
    * NOTE: On logout we automatically unSubscribe() this subscription
    * if not done before.
    * @param cb      Your callback handling implementation
    * @return oid    A unique subscription Id<br>
    *                If you subscribed using a query, the subscription ID of this<br>
    *                query handling object (SubscriptionInfo.getUniqueKey()) is returned.<br>
    *                You should use this ID if you wish to unSubscribe()<br>
    *                If no match is found, an empty string "" is returned.
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
