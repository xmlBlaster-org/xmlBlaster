// xmlBlaster/demo/javaclients/HelloWorldNative.java
package javaclients;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.XmlBlasterAccess;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;


/**
 * This native client plugin is loaded by xmlBlaster on startup, 
 * it then connects to xmlBlaster and gets synchronous a message and disconnects. 
 * <p />
 * You need to add this plugin to xmlBlasterPlugins.xml, for example:
 * <pre>
 *  &lt;plugin id='HelloWorldNative' className='javaclients.HelloWorldNative'>
 *     &lt;attribute id='loginName'>jack&lt;/attribute>
 *     &lt;action do='LOAD' onStartupRunlevel='9' sequence='5' onFail='resource.configuration.pluginFailed'/>
 *     &lt;action do='STOP' onShutdownRunlevel='6' sequence='4'/>
 *  &lt;/plugin>
 * </pre>
 * As a protocol driver to talk to xmlBlaster it has configured "LOCAL", this
 * plugin works only if client and server is in the same virtual machine (JVM).
 * Other protocols like CORBA or SOCKET would work as well but carry the overhead
 * of sending the message over TCP/IP.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.runlevel.html" target="others">run level requirement</a>
 */
public class HelloWorldNative implements I_Plugin
{
   private Global glob;
   private static final String[] nativeConnectArgs = {
          "-protocol", "LOCAL",
          "-dispatch/connection/pingInterval", "0",
          "-dispatch/connection/burstMode/collectTime", "0",
          //"-queue/callback/defaultPlugin", "RAM,1.0",
          //"-queue/connection/defaultPlugin", "RAM,1.0",
          //"-queue/subject/defaultPlugin", "RAM,1.0",
          "-queue/defaultPlugin", "RAM,1.0"
          };

   private final void queryServerMemory() {
      try {
         System.err.println("HelloWorldNative: Connecting with protocol 'LOCAL' to xmlBlaster\n");
         I_XmlBlasterAccess con = new XmlBlasterAccess(glob);

         ConnectQos qos = new ConnectQos(this.glob); /* Client side object */
         qos.setUserId("A-native-client-plugin");
         con.connect(qos, null);    // Login to xmlBlaster as "A-native-client-plugin"

         MsgUnit[] msgs = con.get("<key oid='__cmd:?freeMem'/>", null);

         System.out.println("\nHelloWorldNative: xmlBlaster has currently " +
                new String(msgs[0].getContent()) + " bytes of free memory\n");

         con.disconnect(null);
      }
      catch (Exception e) {
         System.err.println("HelloWorldNative: We have a problem: " + e.toString());
      }
   }

   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = glob.getClone(nativeConnectArgs);
      this.glob.addObjectEntry("ServerNodeScope", glob.getObjectEntry("ServerNodeScope"));
      System.out.println("\nHelloWorldNative: init(): The plugin is loaded");
      queryServerMemory();
   }

   public String getType() {
      return "HelloWorldNative";
   }

   public String getVersion() {
      return "1.0";
   }

   public void shutdown() throws XmlBlasterException {
      System.err.println("\nHelloWorldNative: shutdown()\n");
   }

   public HelloWorldNative() {}

   public HelloWorldNative(String args[]) {
      this.glob = new Global(args);
      queryServerMemory();
   }

   public static void main(String args[]) {
      new HelloWorldNative(args);
   }
}

