// xmlBlaster/demo/javaclients/ClientPlugin.java
package javaclients;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.XmlBlasterAccess;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;


/**
 * This client is loaded by xmlBlaster as a plugin on startup, it then connects
 * to xmlBlaster and gets synchronous a message and disconnects. 
 * <p />
 * You need to add this plugin to xmlBlasterPlugins.xml, for example:
 * <pre>
 *  &lt;plugin id='ClientPlugin' className='javaclients.ClientPlugin'>
 *     &lt;action do='LOAD' onStartupRunlevel='9'/>
 *     &lt;action do='STOP' onShutdownRunlevel='6'/>
 *  &lt;/plugin>
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.runlevel.html" target="others">run level requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.runlevel.howto.html" target="others">run level howto requirement</a>
 */
public class ClientPlugin implements I_Plugin
{
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo)
                                               throws XmlBlasterException {
      doSomething();
   }

   public String getType() {
      return "ClientPlugin";
   }

   public String getVersion() {
      return "1.0";
   }

   public void shutdown() throws XmlBlasterException {
   }

   /**
    * We login to xmlBlaster and check the free memory
    */
   private final void doSomething() {
      try {
         I_XmlBlasterAccess con = new XmlBlasterAccess(new String[0]);

         con.connect(null, null);    // Login to xmlBlaster

         MsgUnit[] msgs = con.get("<key oid='__cmd:?freeMem'/>", null);

         System.out.println("\n###ClientPlugin###: xmlBlaster has currently " +
                new String(msgs[0].getContent()) + " bytes of free memory\n");

         con.disconnect(null);
      }
      catch (Exception e) {
         System.err.println("ClientPlugin: We have a problem: " + e.toString());
      }
   }
}

