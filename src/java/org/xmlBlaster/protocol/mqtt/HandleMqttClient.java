/*------------------------------------------------------------------------------
Name:      HandleMqttClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.mqtt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttWireMessage;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.SubscriptionInfo;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.engine.qos.DisconnectQosServer;
import org.xmlBlaster.engine.qos.SubscribeQosServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.SessionQos;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;

/**
 * Holds one socket connection to a client and handles all requests from one
 * client with plain socket messaging.
 * <p />
 * <ol>
 * <li>We block on the socket input stream to read incoming messages in a
 * separate thread (see run() method)</li>
 * <li>We send update() and ping() back to the client</li>
 * </ol>
 *
 * @author Adrian Batzill
 */
public class HandleMqttClient implements Runnable, I_CallbackDriver {
   private String ME = "HandleMqttClient";
   private static Logger log = Logger.getLogger(HandleMqttClient.class.getName());
   private static final String KEY_MAX_QOS_LEVEL = "_mqttQosLevel";
   private static final String KEY_RETAIN_AS_PUBLISHED = "_mqttRetainAsPublished";
   private static final String KEY_DOMAIN = "_domain";
   
   private XbMqttDriver driver;
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate;
   private AddressServer addressServer;
   // private String cbKey = null; // Remember the key for the Global map
   /** Holds remote "host:port" for logging */
   protected String remoteSocketStr;
   /** The socket connection to/from one client */
   protected Socket sock;
   protected InputStream iStream;
   protected OutputStream oStream;
   protected MqttInputStream mqttIStream;
   protected MqttOutputStream mqttOStream;

   private ConnectQosServer connectQos;
   private ConnectReturnQosServer connectReturnQos;

   protected Properties pluginParams;
   protected String levelSeparator;
   private String levelSeparatorEscaped;

   protected boolean disconnectIsCalled = false;

   private boolean isShutdownCompletly = false;
   
   private boolean isPersistentSession; 

   private Thread socketHandlerThread;

   private boolean running;

   // private ServerScope serverGlob;
   private Global glob;

   private XbMqttPublish lastWill;

   private AtomicInteger nextMessageId = new AtomicInteger(0);

   /**
    * Creates an instance which serves exactly one client.
    */
   public HandleMqttClient(ServerScope serverScope, XbMqttDriver driver, Socket sock) throws IOException {
      // this.serverGlob = glob;
      this.glob = new Global(); // new global for client scope
      this.driver = driver;
      this.sock = sock;
      this.iStream = sock.getInputStream();
      this.oStream = sock.getOutputStream();
      this.authenticate = driver.getAuthenticate();
      this.addressServer = driver.getAddressServer();
      this.ME = driver.getType() + "-HandleClient";

      this.pluginParams = driver.getPluginConfig().getParameters();
      this.levelSeparator = pluginParams.getProperty("mqtt.topicOidLevelSeparator", "/");
      this.levelSeparatorEscaped = Pattern.quote(this.levelSeparator);

      this.remoteSocketStr = this.sock.getInetAddress().toString() + ":" + this.sock.getPort();
      this.sock.setSoTimeout(addressServer.getEnv("SoTimeout", 0).getValue()); // switch off

      int linger = addressServer.getEnv("SoLingerTimeout", 0).getValue();
      if (linger > 0)
         this.sock.setSoLinger(true, linger);

      // TODO: ??
      // this.callCoreInSeparateThread =
      // getAddressServer().getEnv("callCoreInSeparateThread",
      // callCoreInSeparateThread).getValue();

      this.socketHandlerThread = new Thread(this, "XmlBlaster." + this.driver.getType() + (this.driver.isSSL() ? ".SSL" : ""));
      int threadPrio = addressServer.getEnv("threadPrio", Thread.NORM_PRIORITY).getValue();
      try {
         this.socketHandlerThread.setPriority(threadPrio);
         if (log.isLoggable(Level.FINE))
            log.fine("-plugin/socket/threadPrio " + threadPrio);
      } catch (IllegalArgumentException e) {
         log.warning("Your -plugin/socket/threadPrio " + threadPrio + " is out of range, we continue with default setting " + Thread.NORM_PRIORITY);
      }
   }

   public void startThread() {
      running = true;
      this.socketHandlerThread.start();
   }

   public String getType() {
      return this.driver.getType();
   }

   public boolean isShutdownCompletly() {
      return this.isShutdownCompletly;
   }

   synchronized public boolean isShutdown() {
      return (this.running == false);
   }

   /**
    * Close connection for one specific client
    */
   public void shutdown() {
      if (lastWill != null && connectReturnQos != null) {
         try {
            this.handlePublish(lastWill, null);
         } catch (Throwable e) {
            log.warning("Failed to publish last will for " + this.connectReturnQos.getSecretSessionId() + ": " + e.getMessage());
         }
         lastWill = null;
      }

      if (!running)
         return;
      synchronized (this) {
         if (!running)
            return;
         if (log.isLoggable(Level.FINE))
            log.fine("Shutdown cb connection to " + connectReturnQos.getUserId() + " ...");
         // if (cbKey != null)
         // driver.getGlobal().removeNativeCallbackDriver(cbKey);

         running = false;

         driver.removeClient(this);
      }
      I_Authenticate auth = this.authenticate;
      if (auth != null && this.connectReturnQos != null) {
         // From the point of view of the incoming client connection we are dead
         // The callback dispatch framework may have another point of view (which is not
         // of interest here)

         auth.connectionState(this.connectReturnQos.getSecretSessionId(), ConnectionStateEnum.DEAD);
      }
      closeSocket();
      this.isShutdownCompletly = true;
   }

   public String getLoginName() {
      if (this.connectReturnQos == null)
         return "";
      return this.connectReturnQos.getUserId();
   }

   public String toString() {
      StringBuffer ret = new StringBuffer(256);
      ret.append(getType()).append("-");
      if (getLoginName() != null && getLoginName().length() > 0)
         ret.append("-").append(getLoginName());
      else
         ret.append("-").append(getSecretSessionId());
      ret.append("-").append(remoteSocketStr);
      return ret.toString();
   }

   private void closeSocket() {
      try {
         if (iStream != null) {
            iStream.close();
            /* iStream=null; */ }
      } catch (IOException e) {
         log.warning(e.toString());
      }
      try {
         if (oStream != null) {
            oStream.close();
            /* oStream=null; */ }
      } catch (IOException e) {
         log.warning(e.toString());
      }
      Socket sock = this.sock;
      try {
         if (sock != null) {
            this.sock = null;
            sock.close();
         }
      } catch (IOException e) {
         log.warning(e.toString());
      }
      if (log.isLoggable(Level.FINE))
         log.fine("Closed socket for '" + getLoginName() + "'.");
   }

   private List<UserProperty> clientPropsToUserProps(ClientProperty[] clientProps) {
      List<UserProperty> userProps = new ArrayList<>();
      for (ClientProperty cp : clientProps) {
         userProps.add(new UserProperty(cp.getName(), cp.getStringValue()));
      }
      return userProps;
   }
   
   private boolean isWildcardFilter(String filter) {
      return filter.contains("#") || filter.contains("+");
   }

   private String mqttSubscriptionToXmlBlaster(String filter) {
      if (!isWildcardFilter(filter))
         return filter.replace("/", levelSeparator);
      
      StringTokenizer tokenizer = new StringTokenizer(filter, "+#/", true);
      StringBuilder xbFilter = new StringBuilder();

      while (tokenizer.hasMoreTokens()) {
         String token = tokenizer.nextToken();
         if ("/".equals(token)) {
            xbFilter.append(levelSeparatorEscaped);
         } else if ("+".equals(token)) {
            xbFilter.append("[^" + levelSeparatorEscaped + "]*"); // match all characters except level separator -> one level
         } else if ("#".equals(token)) {
            // Always at the end, must also match its parent topic
            xbFilter.append(".*");
         } else {
            xbFilter.append(Pattern.quote(token)); // fixed part without wildcard
         }
      }

      filter = xbFilter.toString(); 
      // If we end with wildcard, we also need to match the parent topic...
      if (filter.endsWith(levelSeparatorEscaped + ".*")) {
         int suffix = levelSeparatorEscaped.length() + 2;
         filter = "(" + filter + "|" + filter.substring(0, filter.length() - suffix) + ")";
      }
      filter = filter.replace("\\E\\Q", ""); // simplify a bit: if literal-end and literal-start are directly together, make
                                             // it one

      filter = "//key[xb:matches(@oid,'" + filter + "')]";
      return filter;
   }

   private void handleConnect(XbMqttConnect message, MqttOutputStream outputStream) throws MqttException, IOException {
      try {
         if (this.authenticate == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "authenticate object is null");
         }
         String login = message.getUserName();
         String password = new String(message.getPassword());
         if (login == null || password == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.USER_SECURITY_AUTHENTICATION, "No login/password given");
         }

         ConnectQosData conQosData = new ConnectQosData(glob);

         // SecurityQos
         I_SecurityQos securityQos = conQosData.getSecurityQos();
         securityQos.setUserId(login);
         securityQos.setCredential(password);

         // SessionQos
         SessionQos sessionQos = conQosData.getSessionQos();
         
         // MQTT3: if cleansession=0 -> persistent session with no timeout
         // MQTT5: if cleansstart -> discard maybe persistent session; if expiryInterval is 0 -> nonpersistent session (default), UINT_MAX -> never expire
         Long expiry = message.getProperties().getSessionExpiryInterval();
         this.isPersistentSession = false;
         long sessionTimeout = 0L;
         boolean resetSession = message.isCleanStart();
         if (mqttIStream.getMqttVersion() >= 5) {
            if (expiry == null || expiry == 0) {
               isPersistentSession = false;
            } else {
               sessionTimeout = (expiry == 0xFFFFFFFFl) ? 0 : expiry * 1000;
               isPersistentSession = true;
            }
         } else {
            isPersistentSession = !resetSession;
            sessionQos.setSessionTimeout(0L);
         }
         
         sessionQos.setSessionTimeout(sessionTimeout);
         sessionQos.clearSessions(resetSession); // TODO: should not clear all, only our sessionId?
         sessionQos.setMaxSessions(10);
         conQosData.setPersistent(isPersistentSession);
         conQosData.getSessionCbQueueProperty().setType("CACHE");
         conQosData.getSessionCbQueueProperty().setVersion("1.0");

         String clientId = message.getClientId();
         
         // If clientId is numeric, we use it, otherwise we use the hash code in a persistent connection
         try {
            clientId = "" + Long.parseLong(clientId);
         } catch (Throwable e) {
            clientId = "" + Integer.toUnsignedLong(clientId.hashCode());
         }
         
         String sessId = login;
         if (isPersistentSession) {
            sessId += "/" + clientId;
         }
         conQosData.setSessionName(new SessionName(glob, sessId));
         
         //SessionInfo oldSessInfo = authenticate.getGlobal().getRequestBroker().getAuthenticate(null).getSessionInfo(conQosData.getSessionName());
         //if (oldSessInfo != null)
         //   conQosData.getSessionQos().setSecretSessionId(oldSessInfo.getSecretSessionId());

         
         // ClientProps
         for (UserProperty prop : message.getProperties().getUserProperties()) {
            conQosData.addClientProperty(prop.getKey(), prop.getValue());
         }
         
         // ConnectQos
         this.connectQos = new ConnectQosServer(glob, conQosData);
         connectQos.setAddressServer(addressServer);
         addressServer.setRemoteAddress(remoteSocketStr);

         // Callback address
         CallbackAddress cbAddr = new CallbackAddress(glob, "MQTT", login);
         cbAddr.setCallbackDriver(this);
         if (isPersistentSession)
            cbAddr.setRetries(-1);
         connectQos.getSessionCbQueueProperty().setCallbackAddress(cbAddr);


         this.connectReturnQos = authenticate.connect(connectQos);

         // TODO: should we check permission of will topic first?
         if (message.getWillDestination() != null && message.getWillDestination().length() > 0) {
            this.lastWill = new XbMqttPublish(message.getWillDestination(), message.getWillMessage(), message.getWillProperties());
         }

         ME = connectReturnQos.getSessionName().getRelativeName();

         MqttProperties returnProps = new MqttProperties();
         returnProps.getUserProperties().addAll(clientPropsToUserProps(connectReturnQos.getData().getClientPropertyArr()));
         returnProps.setMaximumQoS(2);
         if (clientId == null || clientId.length() == 0)
            returnProps.setAssignedClientIdentifier(connectReturnQos.getSecretSessionId());

         log.info("Accepted mqtt version " + message.getMqttVersion() + " connection " + connectReturnQos.getSecretSessionId() + ": " + message.toLogString());
         
         XbMqttConnAck connack = new XbMqttConnAck(connectReturnQos.isReconnected(), 0, returnProps);
         outputStream.write(connack);
      } catch (XmlBlasterException e) {
         log.warning(ME + " Connect failed: " + e.toString());
         int retCode = 0x80;
         if (ErrorCode.getCategory(e.getErrorCode()) == ErrorCode.USER_SECURITY)
            retCode = 0x87; // "Not authorized"
         XbMqttConnAck connack = new XbMqttConnAck(false, retCode, null);
         outputStream.write(connack);
      }
   }

   private void handleSubscribe(XbMqttSubscribe message, MqttOutputStream outStream) throws XmlBlasterException, MqttException, IOException {
      I_XmlBlaster xb = authenticate.getXmlBlaster();

      int[] returnCodes = new int[message.getSubscriptions().length];
      int count = 0;

      // To allow MQTT filtering, even if we don't have / in our topic Oids
      // E.g. converts company.poiAttribConfigList.something.count
      // to company/poiAttribConfigList/something/count

      for (MqttSubscription subscription : message.getSubscriptions()) {
         String filter = mqttSubscriptionToXmlBlaster(subscription.getTopic());
         
         String filterType = Constants.EXACT;
         if (isWildcardFilter(subscription.getTopic())) {
            filterType = Constants.XPATH;
         }

         try {
            SubscribeQos qos = new SubscribeQos(glob);
            SubscribeKey key = new SubscribeKey(glob, filter, filterType);

            qos.setPersistent(this.isPersistentSession);
            qos.setWantUpdateOneway(subscription.getQos() == 0);
            //qos.setMultiSubscribe(true);
            qos.setWantNotify(false);
            qos.setWantInitialUpdate(subscription.getRetainHandling() < 2);
            qos.setWantLocal(!subscription.isNoLocal());
            // TODO: make subscription survive reconnect with same session id?

            for (UserProperty prop : message.getProperties().getUserProperties()) {
               if (KEY_DOMAIN.equals(prop.getKey()))
                  key.setDomain(prop.getValue());
               else
                  qos.getData().addClientProperty(prop.getKey(), prop.getValue());
            }
            
            qos.addClientProperty(KEY_MAX_QOS_LEVEL, subscription.getQos());
            qos.addClientProperty(KEY_RETAIN_AS_PUBLISHED, subscription.isRetainAsPublished());
            
            xb.subscribe(null, connectReturnQos.getSecretSessionId(), key.toXml(), qos.toXml());
            
            int grantedQos = subscription.getQos() >= 1 ? 1 : 0;
            returnCodes[count++] = grantedQos;
         } catch (Throwable e) {
            log.warning("MQTT subscribe failed for filter " + subscription.getTopic() + " -> " + filter + ": " + e.getMessage());
            returnCodes[count++] = 128;
         }
      }
      XbMqttSubAck subAck = new XbMqttSubAck(returnCodes, null);
      subAck.setMessageId(message.getMessageId());
      outStream.write(subAck);
   }

   /**
    * @param publish
    * @param outStream null when publishing last will
    * @throws XmlBlasterException
    * @throws MqttException
    * @throws IOException
    */
   private void handlePublish(XbMqttPublish publish, MqttOutputStream outStream) throws XmlBlasterException, MqttException, IOException {
      I_XmlBlaster xb = authenticate.getXmlBlaster();

      String topic = publish.getTopicName();
      topic = topic.replace("/", this.levelSeparator);

      PublishKey pubKey = new PublishKey(glob, topic);

      MqttMessage message = publish.getMessage();

      byte[] content = message.getPayload();

      PublishQos qos = new PublishQos(glob);

      for (UserProperty prop : publish.getProperties().getUserProperties()) {
         qos.addClientProperty(prop.getKey(), prop.getValue());
      }

      if (message.getProperties().getContentType() != null)
         pubKey.setContentMime(message.getProperties().getContentType());

      qos.setPersistent(message.isRetained());
      qos.setVolatile(!message.isRetained());
      if (publish.getProperties().getMessageExpiryInterval() != null)
         qos.setLifeTime(publish.getProperties().getMessageExpiryInterval() * 1000);


      MsgUnit msgUnit = new MsgUnit(pubKey, content, qos);

      if (message.getQos() == 0) {
         xb.publishOneway(addressServer, connectReturnQos.getSecretSessionId(), new MsgUnitRaw[] { msgUnit.getMsgUnitRaw() });
      } else if (message.getQos() == 1 || message.getQos() == 2) {
         int retCode = 0;
         MqttProperties props = new MqttProperties();
         try {
            String result = xb.publish(addressServer, connectReturnQos.getSecretSessionId(), msgUnit.getMsgUnitRaw());
            if (outStream != null && result != null) {
               PublishReturnQos retQos = new PublishReturnQos(glob, result);
               for (ClientProperty prop : retQos.getData().getClientPropertyArr())
                  props.getUserProperties().add(new UserProperty(prop.getName(), prop.getStringValue()));
            }
         } catch (XmlBlasterException ex) {
            if (ErrorCode.getCategory(ex.getErrorCode()) == ErrorCode.USER_SECURITY)
               retCode = 0x87; // "Not authorized"
            else
               retCode = 0x80; // "Unspecified error"
         }

         if (outStream != null) {
            if (message.getQos() == 1) { 
               outStream.write(new XbMqttPubAck(retCode, publish.getMessageId(), props)); // TODO: better error codes
            } else {
               // TODO: incomplete QoS level 2 - we just pretend
               mqttIStream.addResponseListener((msg) -> {
                  if (msg instanceof XbMqttPubRel && msg.getMessageId() == publish.getMessageId()) {
                     outStream.write(new XbMqttPubComp(0, publish.getMessageId(), null));
                     return true;
                  }
                  return false;
               });
               outStream.write(new XbMqttPubRec(0, publish.getMessageId(), props));
            }
         }
      } else {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION, "MQTT QoS level " + message.getQos() + " not implemented");
      }
   }

   private void handleUnsubscribe(XbMqttUnsubscribe unsubscribe, MqttOutputStream outStream) throws XmlBlasterException, MqttException, IOException {
      I_XmlBlaster xb = authenticate.getXmlBlaster();

      String[] topics = unsubscribe.getTopics();
      int[] result = new int[topics.length];
      for (int i = 0; i < topics.length; i++) {
         String topic = topics[i];

         String filter = mqttSubscriptionToXmlBlaster(topic);
         String filterType = Constants.EXACT;
         if (isWildcardFilter(topic))
            filterType = Constants.XPATH;
         
         try {
            UnSubscribeQos qos = new UnSubscribeQos(glob);
            UnSubscribeKey key = new UnSubscribeKey(glob, filter, filterType);

            for (UserProperty prop : unsubscribe.getProperties().getUserProperties()) {
               qos.addClientProperty(prop.getKey(), prop.getValue());
            }

            xb.unSubscribe(addressServer, connectReturnQos.getSecretSessionId(), key.toXml(), qos.toXml());
            result[i] = 0;
         } catch (Throwable e) {
            log.warning("Unsubscribe failed for topic filter " + topic + " -> " + filter);
            result[i] = 128;
         }
      }
      XbMqttUnsubAck unsubAck = new XbMqttUnsubAck(result, null);
      unsubAck.setMessageId(unsubscribe.getMessageId());
      mqttOStream.write(unsubAck);
   }

   private void handlePingReq(XbMqttPingReq req, MqttOutputStream outStream) throws IOException, MqttException, XmlBlasterException {
      this.authenticate.getXmlBlaster().refreshSession(addressServer);
      outStream.write(new XbMqttPingResp());
   }

   private void handleDisconnect(XbMqttDisconnect disconnect, MqttOutputStream outStream) {
      if (disconnect.getReturnCode() == 0)
         this.lastWill = null;
      Long expiry = disconnect.getProperties().getSessionExpiryInterval();
      Authenticate auth = authenticate.getGlobal().getRequestBroker().getAuthenticate(null);
      SessionInfo sInfo = auth.getSessionInfo(connectReturnQos.getSessionName());
      
      boolean expireNow = !this.connectQos.getData().isPersistent();
      
      if (expiry != null) {
         if (expiry == 0) {
            expireNow = true;
         } else {
            sInfo.setSessionTimeout((expiry == 0xFFFFFFFFl) ? 0 : expiry * 1000);
            expireNow = false;
         }
      }

      // MQTT: session stays active on disconnect, except if expiry is 0 -> don't send a disconnectQos, just quit
      if (expireNow) {
         DisconnectQosServer qos = new DisconnectQosServer(glob);
         try {
            authenticate.disconnect(addressServer, connectReturnQos.getSecretSessionId(), qos.toString());
         } catch (Throwable e) {
            log.severe("Unexpected: XmlBlaster disconnect returned error " + e.getMessage());
         }
      }
      shutdown();
   }

   public void handleMessage(MqttWireMessage message, MqttOutputStream outStream) {
      try {
         switch (message.getType()) {
         case MqttWireMessage.MESSAGE_TYPE_CONNECT:
            handleConnect((XbMqttConnect) message, outStream);
            break;
         case MqttWireMessage.MESSAGE_TYPE_SUBSCRIBE:
            handleSubscribe((XbMqttSubscribe) message, outStream);
            break;
         case MqttWireMessage.MESSAGE_TYPE_PUBLISH:
            handlePublish((XbMqttPublish) message, outStream);
            break;
         case MqttWireMessage.MESSAGE_TYPE_UNSUBSCRIBE:
            handleUnsubscribe((XbMqttUnsubscribe) message, outStream);
            break;
         case MqttWireMessage.MESSAGE_TYPE_PINGREQ:
            handlePingReq((XbMqttPingReq) message, outStream);
            break;
         case MqttWireMessage.MESSAGE_TYPE_DISCONNECT:
            handleDisconnect((XbMqttDisconnect) message, outStream);
            break;
         }

      } catch (Throwable e) {
         e.printStackTrace();
         log.severe("Lost connection to client: " + e.toString());
         shutdown();
      }
   }

   /**
    * Serve a client, we block until a message arrives ...
    */
   public void run() {
      if (log.isLoggable(Level.FINER))
         log.finer("Handling client request ...");
      try {
         if (log.isLoggable(Level.FINE)) {
            Socket socket = this.sock;
            if (socket != null)
               log.fine("Client accepted, coming from host=" + socket.getInetAddress().toString() + " port=" + socket.getPort());
         }
         this.mqttIStream = new MqttInputStream(iStream);
         this.mqttOStream = null;
         while (running) {
            try {
               MqttWireMessage msg = mqttIStream.readMessage();
               if (msg != null) {
                  // we can only create the output stream once we know the protocol version...
                  if (mqttOStream == null)
                     mqttOStream = new MqttOutputStream(mqttIStream.getMqttVersion(), oStream);
                  handleMessage(msg, mqttOStream);
               }
            } catch (Throwable e) {
               if (e.toString().indexOf("closed") != -1 || (e instanceof java.net.SocketException && e.toString().indexOf("Connection reset") != -1)) {
                  if (log.isLoggable(Level.FINE))
                     log.fine(toString() + ": TCP socket is shutdown: " + e.toString());
               } else if (e.toString().indexOf("EOF") != -1) {
                  if (this.disconnectIsCalled)
                     if (log.isLoggable(Level.FINE))
                        log.fine(toString() + ": Lost TCP connection after sending disconnect(): " + e.toString());
                     else
                        log.warning(toString() + ": Lost TCP connection: " + e.toString());
               } else {
                  log.warning(toString() + ": Error parsing TCP data from '" + remoteSocketStr + "', check if client and server have identical compression or SSL settings: " + e.toString());
               }
               if (e instanceof OutOfMemoryError || e instanceof IllegalArgumentException) {
                  e.printStackTrace();
               }
               if (e.getCause() != null && (e.getCause() instanceof OutOfMemoryError || e.getCause() instanceof IllegalArgumentException)) {
                  e.printStackTrace();
               }
//               I_Authenticate auth = this.authenticate;
//               if (auth != null) {
//                  // From the point of view of the incoming client connection we are dead
//                  // The callback dispatch framework may have another point of view (which is not of interest here)
//                  auth.connectionState(this.secretSessionId, ConnectionStateEnum.DEAD);
//               }
               break;
            }
         }
      } finally {
         shutdown(); // to potentially publish last will
         if (log.isLoggable(Level.FINE))
            log.fine("Deleted thread for '" + getLoginName() + "'.");
      }
   }

   /**
    * @return Returns the secretSessionId.
    */
   public String getSecretSessionId() {
      if (this.connectReturnQos == null)
         return null;
      return this.connectReturnQos.getSecretSessionId();
   }

   public Socket getSocket() {
      return this.sock;
   }

   /***********************************
    * // I_CallbackDriver implementation
    ***********************************/

   @Override
   public void init(Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
   }

   @Override
   public String getName() {
      return Global.getStrippedString(this.ME);
   }

   @Override
   public void init(Global glob, CallbackAddress callbackAddress) throws XmlBlasterException {
   }

   @Override
   public String getProtocolId() {
      return driver.getProtocolId();
   }

   @Override
   public String getRawAddress() {
      return driver.getRawAddress();
   }

   @Override
   public String[] sendUpdate(final MsgUnitRaw[] msgArrRaw) throws XmlBlasterException {
      MsgUnit[] msgArr = new MsgUnit[msgArrRaw.length];
      for (int i = 0; i < msgArrRaw.length; i++)
         msgArr[i] = new MsgUnit(glob, msgArrRaw[i], MethodName.UPDATE);
      
      final CountDownLatch allAckLatch = new CountDownLatch(msgArr.length);
      final String[] results = new String[msgArr.length];
      
      for (int i = 0; i < msgArr.length; i++) {
         final int msgId = this.nextMessageId.getAndIncrement();
         final int msgArrIdx = i;
         
         mqttIStream.addResponseListener((msg) -> {
            if (msg instanceof XbMqttPubAck && msg.getMessageId() == msgId) {
               StatusQosData qosData = new StatusQosData(glob, MethodName.UPDATE);
               qosData.setState(Constants.STATE_OK);
               results[msgArrIdx] = new PublishReturnQos(glob, qosData).toXml();
               allAckLatch.countDown();
               return true;
            }
            return false;
         });

         try {
            if (msgArr[i].getQosData().isErased()) {
               // erase.. ack directly
               allAckLatch.countDown();
               StatusQosData response = new StatusQosData(glob, MethodName.ERASE);
               response.setState(Constants.STATE_OK);
               results[i] = new EraseReturnQos(glob, response).toXml();
               continue;
            }
               
            sendMsgUnit(msgArr[i], 1, msgId);
         } catch (Throwable e) {
            log.warning("Failed to deliver message ID " + msgId + " " + msgArr[i].getKey() + ": " + e.getMessage());
            allAckLatch.countDown(); // won't receive ack for this one
            StatusQosData status = new StatusQosData(glob, MethodName.UPDATE);
            status.setException(e);
            status.setKeyOid(msgArr[i].getKeyOid());
            results[i] = new PublishReturnQos(glob, status).toXml();
         }
      }

      // Wait for all acks..
      try {
         allAckLatch.await(30, TimeUnit.SECONDS); // TODO: timeout handling? No ACK in time?
      } catch (InterruptedException e) {
      }
      // Handle missing acks
      for (int i = 0; i < results.length; i++) {
         if (results[i] == null) {
            StatusQosData status = new StatusQosData(glob, MethodName.UPDATE);
            status.setKeyOid(msgArr[i].getKeyOid());
            status.setState(Constants.STATE_TIMEOUT);
            results[i] = new PublishReturnQos(glob, status).toXml();
         }
      }

      return results;
   }

   @Override
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      for (MsgUnitRaw unitRaw : msgArr) {
         MsgUnit msgUnit = new MsgUnit(glob, unitRaw, MethodName.UPDATE_ONEWAY);
         if (msgUnit.getQosData().isErased())
            continue;
         sendMsgUnit(msgUnit, 0, 0);
      }
   }

   private void sendMsgUnit(MsgUnit unit, int qos, int messageId) throws XmlBlasterException {
      try {
         MsgQosData qosData = (MsgQosData) unit.getQosData();
         String subscriptionId = qosData.getSubscriptionId();
         SubscriptionInfo subInfo = authenticate.getGlobal().getRequestBroker().getClientSubscriptions().getSubscription(subscriptionId);
         
         // "Retain as published" handling
         // https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901104
         boolean retainFlag = false;
         if (subInfo != null) {
            SubscribeQosServer subQos = subInfo.getSubscribeQosServer();
            if (subQos != null && subQos.getData().getClientProperty(KEY_RETAIN_AS_PUBLISHED, false)) {
               if (!qosData.isVolatile())
                  retainFlag = true;
            }
         }
         
         String topicName = unit.getKeyOid().replace(this.levelSeparator, "/");

         MqttProperties props = new MqttProperties();
         List<UserProperty> userProps = new ArrayList<>();
         for (ClientProperty prop : qosData.getClientPropertyArr())
            userProps.add(new UserProperty(prop.getName(), prop.getStringValue()));
         props.setUserProperties(userProps);

         if (unit.getContentMime() != null)
            props.setContentType(unit.getContentMime());

         MqttMessage message = new MqttMessage(unit.getContent());
         message.setQos(qos);
         message.setRetained(retainFlag);
         message.setDuplicate(qosData.getRedeliver() > 0);
         
         // ReplyTo handling... bounce id? ...
         String replyTo = qosData.getClientProperty(Constants.JMS_REPLY_TO, (String) null);
         if (replyTo != null) {
            message.getProperties().setResponseTopic(replyTo.replace(this.levelSeparator, "/"));
         }
         
         XbMqttPublish publish = new XbMqttPublish(topicName, message, props);
         publish.setMessageId(messageId);
         
         this.mqttOStream.write(publish);
      } catch (Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION, ME, "Failed to send MQTT message: " + e.getMessage(), e);
      }
   }

   @Override
   public boolean isAlive() {
      return this.running;
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String ping(String qos) throws XmlBlasterException {
      // TODO: ?? Mqtt only allows pings from client to server
      StatusQosData status = new StatusQosData(glob, MethodName.PING);
      if (running) {
         status.setState(Constants.STATE_OK);
      } else {
         status.setState(Constants.STATE_TIMEOUT);
      }
      return status.toXml();
   }

   @Override
   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      return null;
   }
}
