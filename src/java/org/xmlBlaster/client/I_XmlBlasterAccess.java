/*------------------------------------------------------------------------------
Name:      I_XmlBlasterAccess.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import java.io.InputStream;
import java.util.Map;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_ReplaceContent;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.dispatch.I_PostSendListener;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.util.MsgUnit;


/**
 * The Java client side access to xmlBlaster.
 * <br />
 * This interface hides a remote connection or a native connection to the server.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html">interface requirement</a>
 */
public interface I_XmlBlasterAccess extends I_XmlBlaster, I_ConnectionHandler
{
   /**
    * Register a listener to get events about connection status changes.
    * @param connectionListener null or your listener implementation on connection state changes (ALIVE | POLLING | DEAD)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsafe.html">client.failsafe requirement</a>
    */
   void registerConnectionListener(I_ConnectionStateListener connectionListener);

   /**
    * Register a listener to get notifications when a messages is successfully send from
    * the client side tail back queue.
    * Max one can be registered, any old one will be overwritten.
    * <p/>
    * A use case is that you want to get the ReturnQos when a message which was
    * queued on client side is finally sent to the server.
    * @param postSendListener The postSendListener to set, pass null to stop the listener
    * @return the old listener or null if no previous was registered
    */
   I_PostSendListener registerPostSendListener(I_PostSendListener postSendListener);

   /**
    * Setup the cache mode.
    * <p />
    * This installs a cache. When you call get(), a subscribe() is done
    * in the background that we always have a current value in our client side cache.
    * Further get() calls retrieve the value from the client cache.
    * <p />
    * Only the first call is used to setup the cache, following calls
    * are ignored silently (and return the original handle)
    *
    * @param size Size of the cache. This number specifies the count of subscriptions the cache
    *             can hold. It specifies NOT the number of messages.
    * @return The cache handle, usually of no interest
    * @see #getCached(GetKey, GetQos)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.cache.html">client.cache requirement</a>
    */
   public SynchronousCache createSynchronousCache(int size);

   /**
    * Use a specific error handler instead of the default one.
    * @param msgErrorHandler Your implementation of the error handler.
    * @see org.xmlBlaster.client.ClientErrorHandler
    */
   public void setClientErrorHandler(I_MsgErrorHandler msgErrorHandler);

   /**
    * Login to xmlBlaster.
    * <p>
    * Connecting with the default configuration (which checks xmlBlaster.properties and
    * your command line arguments):
    * </p>
    * <pre>
    *  import org.xmlBlaster.util.Global;
    *  ...
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
    * @return Can only be null if '-dispatch/connection/doSendConnect false' was set
    * @throws XmlBlasterException only if connection state is DEAD, typically thrown on wrong configurations.
    *            You must call connect again with different settings.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">interface.connect requirement</a>
    */
   ConnectReturnQos connect(ConnectQos qos, I_Callback updateListener) throws XmlBlasterException;

   /**
    * Create a new instance of the desired protocol driver like CORBA or RMI driver using the plugin loader.
    * <p>
    * Note that the returned instance is of your control only, we don't cache it in any way, this
    * method is only a helper hiding the plugin loading.
    * </p>
    * @param loginName A nice name for logging purposes
    * @param callbackAddress The callback address configuration, contains for example
    *        type like "IOR" or "RMI" and version of the driver, e.g. "1.0"
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.html">protocol requirement</a>
    */
   I_CallbackServer initCbServer(String loginName, CallbackAddress callbackAddress) throws XmlBlasterException;

   /**
    * Switch callback dispatcher on/off.
    * This is a convenience function (see ConnectQos). It will update the client side
    * ConnectQos as well so we don't loose the setting on reconnects after server maintenance.
    * @param activate true: XmlBlaster server delivers callback messages
    *        false: XmlBlaster server keeps messages for this client in the callback queue
    */
   void setCallbackDispatcherActive(boolean activate) throws XmlBlasterException;

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
   String sendAdministrativeCommand(String command) throws XmlBlasterException;

   /**
    * Access the client side security plugin.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/security.introduction.html">security.introduction requirement</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/security.development.serverPlugin.howto.html">security.development.serverPlugin.howto requirement</a>
    */
   I_ClientPlugin getSecurityPlugin();

   /**
    * Logout from the server.
    * <p />
    * Behavior on client side:<br />
    * Destroys pending tail back messages in the client queue
    * and destroys low level connection and callback server.
    * You can customize the behavior with disconnectQos.
    * <p />
    * Behavior on server side:<br />
    * The server side session resources are destroyed, pending messages are deleted.
    * <p />
    * NOTE: If you want to keep all resources on server side for this login session
    *       but want to halt your client,
    *       shutdown the callback server with <code>leaveServer(null)</code>
    *       and throw the xmlBlasterAccess instance away.
    *       This is often the case if the client disappears and at a later point wants
    *       to reconnect. On server side the queue for this session remains alive and
    *       collects messages.
    * <p />
    * If '-dispatch/connection/doSendConnect false' was set call disconnect() nevertheless
    * to cleanup client side resources.
    * @param disconnectQos Describe the desired behavior on disconnect
    * @return false if connect() wasn't called before or if you call disconnect() multiple times
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">interface.disconnect requirement</a>
    */
   boolean disconnect(DisconnectQos disconnectQos);

   /**
    * Leaves the connection to the server and cleans up the
    * client side resources without making a server side disconnect.
    * This way the client side persistent messages are kept in queue while
    * transient ones are lost. If you want to delete also the
    * persistent messages you have to do it manually.
    * <p>
    * As the login session on server side stays alive, all subscriptions stay valid
    * and callback messages are queued by the server.
    * If you connect at a later time the server sends us all queued messages.
    * </p>
    * <p>
    * Once you have called this method the I_XmlBlasterAccess
    * becomes invalid and any further invocation results in
    * an XmlBlasterException to be thrown.
    * </p>
    *
    * @param map The properties to pass while leaving server.
    *        Currently this argument has no effect. You can
    *        pass null as a parameter.
    */
   void leaveServer(Map map);

   /**
    * Has the connect() method successfully passed?
    * <p>
    * Note that this contains no information about the current connection state
    * of the protocol layer.
    * </p>
    * @return true If the connection() method was invoked without exception
    * @see I_ConnectionHandler#isAlive()
    * @see I_ConnectionHandler#isPolling()
    * @see I_ConnectionHandler#isDead()
    */
   boolean isConnected();

   /**
    * Send an event to xmlBlaster to refresh the login session life time.
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.login.session.html">session requirement</a>
    * @throws XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   void refreshSession() throws XmlBlasterException;

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
    * Access the callback server which is currently used in I_XmlBlasterAccess.
    * The callback server is not null if you have passes a I_Callback handle on connect().
    * @return null if no callback server is established
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.html">protocol requirement</a>
    * @see #connect(ConnectQos, I_Callback)
    */
   I_CallbackServer getCbServer();

   /**
    * A unique name for this client, for logging only
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

   String getStorageIdStr();

   /**
    * Allows to set a unique client side queue name (connection queue).
    * Useful only if you code connects to multiple servers with the same login name.
    * Use with very caution to be unique in complete database!
    * @param prefix For example "toserver1"+sessionName.getRelativeName()
    */
   void setStorageIdStr(String prefix);

   /**
    * Allows to set the node name for nicer logging.
    * Used for clustering.
    * @param nodeId For example "/xmlBlaster/node/heron"
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
    * Subscribe to messages.
    * <p>
    * The messages are delivered asynchronous with the update() method.
    * </p>
    * @param subscribeKey Which message topics to retrieve
    * @param subscribeQos Control the behavior and further filter messages with mime based filter plugins
    * @return Is never null
    * @see org.xmlBlaster.client.I_Callback#update(String, org.xmlBlaster.client.key.UpdateKey, byte[], org.xmlBlaster.client.qos.UpdateQos)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">interface.subscribe requirement</a>
    * @throws XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
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
    *         Is never null
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">interface.subscribe requirement</a>
    * @throws XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   SubscribeReturnQos subscribe(SubscribeKey subscribeKey, SubscribeQos subscribeQos, I_Callback cb) throws XmlBlasterException;

   /**
    * Subscribe to messages.
    * @param xmlKey Which message topics to retrieve
    * @param xmlQos Control the behavior and further filter messages with mime based filter plugins
    * @return is never null
    * @see I_XmlBlasterAccess#subscribe(SubscribeKey, SubscribeQos, I_Callback)
    * @throws XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   SubscribeReturnQos subscribe(String xmlKey, String xmlQos, I_Callback cb) throws XmlBlasterException;

   /**
    * Access synchronously messages. They are on first request subscribed
    * and cached on client side.
    * <p>
    * A typical use case is a servlet which receives many HTML requests and
    * usually the message has not changed. This way we avoid asking xmlBlaster
    * every time for the information but take it directly from the cache.
    * </p>
    * <p>
    * The cache is always up to date as it has subscribed on this topic
    * </p>
    * <p>
    * You need to call <i>createSynchronousCache()</i> before using <i>getCached()</i>.
    * </p>
    * <p>
    * NOTE: Passing two similar getKey but with different getQos filters is currently not supported.
    * </p>
    * <p>
    * NOTE: GetKey requests with EXACT oid are automatically removed from cache when
    *       the topic with this oid is erased. XPATH queries are removed from cache
    *       when the last topic oid which matched the XPATH disappears.
    * </p>
    * @param getKey Which message topics to retrieve
    * @param getQos Control the behavior and further filter messages with mime based filter plugins
    * @return An array of messages, the sequence is arbitrary, never null
    * @throws XmlBlasterException if <i>createSynchronousCache()</i> was not used to establish a cache first
    * @see #createSynchronousCache(int)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.cache.html">client.cache requirement</a>
    * @throws XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   public MsgUnit[] getCached(GetKey getKey, GetQos getQos) throws XmlBlasterException;

   //MsgUnit[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   /**
    * Get synchronous messages.
    * @param getKey Which message topics to retrieve
    * @param getQos Control the behavior and further filter messages with mime based filter plugins
    * @return never null
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html">interface.get requirement</a>
    * @throws XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   MsgUnit[] get(GetKey getKey, GetQos getQos) throws XmlBlasterException;

   // @param maxBytes The total size of all returned messages together must not exceed this value.
   //       This is not implemented yet since the size of the entries in the queue is not guaranteed
   //       to be related to the size of the MsgUnits
   //RELATING_CALLBACK = "callback";
   //RELATING_SUBJECT = "subject";
   //RELATING_HISTORY = "history";
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
   MsgUnit[] receive(String oid, int maxEntries, long timeout, boolean consumable) throws XmlBlasterException;


   //UnSubscribeReturnQos[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   /**
    * Cancel subscription.
    * @param unSubscribeKey Which messages to cancel
    * @param unSubscribeQos Control the behavior
    * @return The status of the unSubscribe request, is never null
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html">interface.unSubscribe requirement</a>
    * @throws XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   UnSubscribeReturnQos[] unSubscribe(UnSubscribeKey unSubscribeKey, UnSubscribeQos unSubscribeQos) throws XmlBlasterException;

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">interface.publish requirement</a>
    */
   PublishReturnQos publish(MsgUnit msgUnit) throws XmlBlasterException;
   //Rename publishArr() to publish
   //PublishReturnQos[] publish(org.xmlBlaster.util.MsgUnit[] msgUnitArr) throws XmlBlasterException;
   /**
    * Publish messages.
    * @param msgUnitArr The messages to send to the server
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">interface.publish requirement</a>
    * @throws XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
    */
   void publishOneway(org.xmlBlaster.util.MsgUnit [] msgUnitArr) throws XmlBlasterException;

   /**
    * Publishes one message in streaming manner, if the message content is too big to fit in one single chunk, the message
    * is split in several smaller messages (called chunks) and these are published. During the publishing of these messages,
    * the method blocks.
    * 
    * @param is The input stream from which to read the input data.
    * @param keyData The key for the message (same for all chunks)
    * @param qosData The qos for all messages (same for all chunks besides internal stuff added in this method)
    * @param maxBufSize The maximum content size of each chunk.
    * @param contentReplacer an optional content replacer (i.e. a modifier of the content) can be null.
    * @return an array containing the return qos. Currently an array of length 1 is returned containing the return qos for
    * the first message.
    * @throws XmlBlasterException
    */
   PublishReturnQos[] publishStream(InputStream is, MsgKeyData keyData, MsgQosData qosData, int maxBufSize, I_ReplaceContent contentReplacer) throws XmlBlasterException;

   /**
    * Implements the blocking request/reply pattern.
    * <p>
    * The msgUnit should contain a PublishQos which routes the request
    * to the desired client, for example sending it to client <code>joe</code>
    * and its login session <code>1</code> or sending it to a topic which was subscribed
    * by the destination client:
    * <pre>
    * import org.xmlBlaster.util.qos.address.Destination;
    * import org.xmlBlaster.client.qos.PublishQos;
    *  ...
    *  Global glob = ...;
    *  ...
    *  PublishQos pq = new PublishQos(glob);
    *  Destination dest = new Destination(glob, new SessionName(glob, "joe/1"));
    *  dest.forceQueuing(true);
    *  pq.addDestination(dest);
    *  </pre>
    *  <p>
    *  This receiver needs to send the response to the topic oid as passed with
    *  the client property "__jms:JMSReplyTo":
    *  <pre>
    *  String tempTopicOid = updateQos.getClientProperty(Constants.JMS_REPLY_TO, "");
    *  // Send reply back ...
    *  PublishKey pk = new PublishKey(glob, tempTopicOid);
    *  ...
    *  </pre>
    *
    *  <p>
    *  This approach is similar to the JMS approach for request/reply (TopicRequestor.java)
    *  but we have the choice to send the msgUnit directly to another client or to a topic (as JMS),
    *  and we can handle multiple replies for one request.
    *  <p>
    *  The feature is implemented on client side with a temporary response topic and a <code>receive()</code> call.
    *  The temporary response topic is erased after the response has arrived.
    *  <br />
    *  You can optionally add a clientProperty "__responseTopicIdPrefix", this topicId is
    *  used as a prefix for the temporary response topicId. the given prefix must be unique
    *  between clients. This is thread safe.
    *  A use case could be to simplify detecting the topic for an authorizer plugin.
    *  <br />
    *  You can optionally add a clientProperty "__responseTopicId" with a unique topicId
    *  to avoid the creation of temporary response topics, note that this feature
    *  is NOT thread safe, the client may only send one request() at a time.
    *  The response topic will live for one day after last usage.
    *  A reason to do so could be the better performance (avoid short living temporary response topics).
    *  <p>
    *  Please note the timeout limitation as described at
    *  {@link #receive(String, int, long, boolean)})
    *
    * @param msgUnit The request to send. The topicId may be any you wish for the receiver to recognize.
    *        If the receiver has not subsribed on this topicId you need to send it PtP (add the Destination client).
    * @param timeout The milliseconds to block, 0 is none blocking, -1 blocks forever
    * @param maxEntries The maximum number of entries to deliver or return with less after timeout
    * @return The response messages, typically one, never null, has 0 entries on timeout
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/demo/HelloWorld8.java.html">HelloWorld8.java</a>
    */
   MsgUnit[] request(MsgUnit msgUnit, long timeout, int maxEntries) throws XmlBlasterException;

   //EraseReturnQos[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   /**
    * @param eraseKey The topics to erase
    * @param eraseQos Control the erase behavior
    * @return The status of the erase request, is never null
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html">interface.erase requirement</a>
    * @throws XmlBlasterException like ErrorCode.USER_NOT_CONNECTED and others
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
