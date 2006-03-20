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
 * it then connects to xmlBlaster. 
 * <p />
 * You need to add this plugin to xmlBlasterPlugins.xml, for example:
 * <pre>
 *  &lt;plugin id='HelloWorldNative' className='javaclients.HelloWorldNative'>
 *     &lt;action do='LOAD' onStartupRunlevel='3' sequence='0' onFail='resource.configuration.pluginFailed'/>
 *     &lt;action do='STOP' onShutdownRunlevel='6' sequence='4'/>
 *  &lt;/plugin>
 * </pre>
 * As a protocol driver to talk to xmlBlaster it has configured "LOCAL", this
 * plugin works only if client and server is in the same virtual machine (JVM).
 * Other protocols like CORBA or SOCKET would work as well but carry the overhead
 * of sending the message over TCP/IP.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.local.html" target="others">native protocol requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.runlevel.html" target="others">run level requirement</a>
 */
public class HelloWorldNative implements I_Plugin
{
   private Global glob;

   private final void doLogin() {
      try {
         System.err.println("HelloWorldNative: Connecting with protocol 'LOCAL' to xmlBlaster\n");
         I_XmlBlasterAccess con = new XmlBlasterAccess(glob);

         ConnectQos qos = new ConnectQos(this.glob); /* Client side object */
         qos.setPtpAllowed(false);
         qos.setUserId("A-NATIVE-CLIENT-PLUGIN");
         qos.getSessionQos().setSessionTimeout(0L);
         con.connect(qos, null);    // Login to xmlBlaster as "A-NATIVE-CLIENT-PLUGIN"
         //Here we could publish or subscribe etc., see HelloWorld3.java how to do it
         //con.disconnect(null);
      }
      catch (Exception e) {
         System.err.println("HelloWorldNative: We have a problem: " + e.toString());
      }
   }

   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = glob.getClone(glob.getNativeConnectArgs()); // Sets  "-protocol LOCAL" etc.
      this.glob.addObjectEntry("ServerNodeScope", glob.getObjectEntry("ServerNodeScope"));
      System.out.println("\nHelloWorldNative: init(): The plugin is loaded");
      doLogin();
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

   /** To start as a plugin */
   public HelloWorldNative() {}

   /** To start as a separate client: java javaclients.HelloWorldNative */
   public HelloWorldNative(String args[]) {
      this.glob = new Global(args);
      doLogin();
   }

   public static void main(String args[]) {
      new HelloWorldNative(args);
   }
}

