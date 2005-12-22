// xmlBlaster/demo/HelloWorld7.java
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
//import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;


/**
 * This is a dumb client, it does almost nothing.
 * It initializes the xmlBlaster client library
 * and the email callback server but does not connect to xmlBlaster. This is useful
 * when the client can't reach the xmlBlaster server because of a firewall or the like.
 * <br />
 * Another 'delegate' client, for example started as a native xmlBlaster plugin,
 * logs in with the same name and does for example all subscribes - as a delegate.
 * It then just disappears without disconnecting - thus leaving the session alive.
 * Arriving messages will then be routed to our dumb HelloWorld7.
 * <p />
 * Invoke:
 * <pre>
 * # 1. Start the server and setup an email MTA as described in the protocol.email requirement
 * ...
 *  
 * # 2. Start our dumb email client, it will wait for messages 
 * java HelloWorld7 -protocol email -session.name joe/1 -dispatch/connection/pingInterval 0
 *  
 * # 3.Start a delegate, hit enter to subscribe and than kill it with Ctrl-C (to not disconnect):
 * java javaclients.HelloWorldSubscribe -session.name joe/1 -persistentSubscribe true -protocol email
 * 
 * # 4. Start a publisher and watch the messages arriving at HelloWorld7
 * java javaclients.HelloWorldPublish -numPublish 10
 * </pre> 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.email.html" target="others">The protocol.email requirement</a>
 */
public class HelloWorld7 implements I_Callback
{
   private final Global glob;
   private final LogChannel log;

   public HelloWorld7(Global glob) {
      this.glob = glob;
      this.log = glob.getLog(null);
      
      I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess();
      
      try {
         ConnectQos qos = new ConnectQos(glob);

         // '-dispatch/connection/doSendConnect false' on command line would do the same
         qos.doSendConnect(false);
         
         // Initializes everything but does NOT send connect message
         con.connect(qos, this);

         log.info("HelloWorld7", "Waiting now for updates ...");
         char ret = 0;
         while (ret != 'q')
            ret = (char)Global.waitOnKeyboardHit("Enter 'q' to quit");
         
         con.disconnect(null); // Cleanup client library
      }
      catch (XmlBlasterException e) {
         log.error("HelloWorld7", e.getMessage());
      }
      catch (Throwable e) {
         log.error("HelloWorld7", e.toString());
         e.printStackTrace();
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos)
   {
      if (updateKey.isInternal()) {
         log.info("", "Received unexpected internal message '" +
              updateKey.getOid() + " from xmlBlaster");
         return "";
      }

      int myAge = updateQos.getClientProperty("myAge", 0);
      log.info("", "Received asynchronous message '" + updateKey.getOid() +
                   "' state=" + updateQos.getState() +
                   " content=" + new String(content) + 
                   " clientProperty myAge=" + myAge + " from xmlBlaster");

      return Constants.RET_OK;
      //UpdateReturnQos uq = new UpdateReturnQos(glob);
      //return uq.toXml();
   }

   /**
    * Try
    * <pre>
    *   java HelloWorld7 -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("Example: java HelloWorld7 -session.name Jeff\n");
         System.exit(1);
      }

      new HelloWorld7(glob);
   }
}
