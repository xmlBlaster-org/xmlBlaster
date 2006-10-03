// xmlBlaster/demo/javaclients/HelloWorldNative2.java
package javaclients;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;


/**
 * This native client plugin is loaded by xmlBlaster on startup, 
 * it then connects to xmlBlaster and subscribes to a topic and publishes a message. 
 * <p />
 * You need to register this plugin to xmlBlasterPlugins.xml, for example:
 * <pre>
 *  &lt;plugin id='HelloWorldNative2' className='javaclients.HelloWorldNative2'>
 *     &lt;attribute id='loginName'>nativeClient2&lt;/attribute>
 *     &lt;attribute id='topicName'>aNativeTopic2&lt;/attribute>
 *     &lt;action do='LOAD' onStartupRunlevel='9' sequence='6' onFail='resource.configuration.pluginFailed'/>
 *     &lt;action do='STOP' onShutdownRunlevel='6' sequence='5'/>
 *  &lt;/plugin>
 * </pre>
 * <p>
 * Note how the attributes <i>loginName</i> and <i>topicName</i> are passed to the plugin.
 * </p>
 * <p>
 * As a protocol driver to talk to xmlBlaster it has configured "LOCAL", this
 * plugin works only if client and server is in the same virtual machine (JVM).
 * Other protocols like CORBA or SOCKET would work as well but carry the overhead
 * of sending the message over TCP/IP.
 * </p>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.runlevel.html" target="others">run level requirement</a>
 */
public class HelloWorldNative2 implements I_Plugin
{
   private Global glob;
   private PluginInfo pluginInfo;
   private static Logger log = Logger.getLogger(HelloWorldNative2.class.getName());
   private String loginName;
   private String topicName;
   private I_XmlBlasterAccess con;

   private final void pubsub() {
      try {
         log.info("Connecting with protocol 'LOCAL' to xmlBlaster");
         con = new XmlBlasterAccess(glob);

         ConnectQos qos = new ConnectQos(this.glob); /* Client side object */
         qos.setUserId(this.loginName);
         qos.getSessionQos().setSessionTimeout(0L);
         con.connect(qos, new I_Callback() {

            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               if (log.isLoggable(Level.FINEST)) log.finest("UpdateKey.toString()=" + updateKey.toString() +
                                          "UpdateQos.toString()=" + updateQos.toString());
               if (updateKey.isInternal()) {
                  log.severe("Receiving unexpected asynchronous internal message '" + updateKey.getOid() +
                                "' in default handler");
                  return "";
               }
               if (updateQos.isErased()) {
                  log.info("Message '" + updateKey.getOid() + "' is erased");
                  return "";
               }
               if (updateKey.getOid().equals(topicName))
                  log.info("Receiving asynchronous message '" + updateKey.getOid() +
                               "' state=" + updateQos.getState() + " in default handler");
               else
                  log.severe("Receiving unexpected asynchronous message '" + updateKey.getOid() +
                                   "' in default handler");
               return "";
            }

         });

         SubscribeKey sk = new SubscribeKey(glob, this.topicName);
         SubscribeQos sq = new SubscribeQos(glob);
         sq.setWantInitialUpdate(false);
         SubscribeReturnQos sr1 = con.subscribe(sk, sq);
         log.info("Subscribed '" + sr1.getState() + "'");

         PublishKey pk = new PublishKey(glob, this.topicName, "text/plain", "1.0");
         PublishQos pq = new PublishQos(glob);
         MsgUnit msgUnit = new MsgUnit(pk, "Hi", pq);
         PublishReturnQos retQos = con.publish(msgUnit);
         log.info("Published message '" + retQos.getKeyOid() + "'");

      }
      catch (Exception e) {
         log.severe("We have a problem: " + e.toString());
      }
   }

   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.pluginInfo = pluginInfo;
      this.glob = glob.getClone(glob.getNativeConnectArgs()); // Sets  "-protocol LOCAL" etc.

      this.glob.addObjectEntry("ServerNodeScope", glob.getObjectEntry("ServerNodeScope"));
      this.loginName = glob.get("loginName", "NO_LOGIN_NAME_CONFIGURED", null, pluginInfo);
      this.topicName = glob.get("topicName", "NO_TOPIC_NAME_CONFIGURED", null, pluginInfo);

      log.info("init(): The plugin is loaded, doing a publish and subscribe\n\n");
      pubsub();
   }

   public String getType() {
      return (this.pluginInfo==null)?"HelloWorldNative2":this.pluginInfo.getType();
   }

   public String getVersion() {
      return (this.pluginInfo==null)?"1.0":this.pluginInfo.getVersion();
   }

   public void shutdown() {
      // cleans up our subscribe as well
      if (con != null) con.disconnect(null);
      log.info("shutdown()\n");
   }

   public void finalize() {
      log.info("Garbage collected");
   }

   /** To start as a plugin */
   public HelloWorldNative2() {}

   /** To start as a standalone client: java javaclients.HelloWorldNative2 */
   public HelloWorldNative2(String args[]) {
      this.glob = new Global(args);

      this.loginName = "A-native-client";
      this.topicName = "A-native-message";
      pubsub();

      shutdown();
   }

   public static void main(String args[]) {
      new HelloWorldNative2(args);
   }
}

