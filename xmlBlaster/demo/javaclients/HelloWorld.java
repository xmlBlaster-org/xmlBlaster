// xmlBlaster/demo/javaclients/HelloWorld.java
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.MsgUnit;


/**
 * This client connects to xmlBlaster and gets synchronous a message and disconnects. 
 * <p />
 * Invoke:
 * <pre>
 *   cd xmlBlaster                           // Change to xmlBlaster distribution directory
 *   java -jar lib/xmlBlaster.jar            // Start the server
 *   java -cp lib/xmlBlaster.jar HelloWorld  // Start this demo
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorld
{
   public HelloWorld(String[] args) {
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(args);

         con.connect(null, null);    // Login to xmlBlaster as "guest"

         MsgUnit[] msgs = con.get("<key oid='__cmd:?freeMem'/>", null);

         System.out.println("\nHelloWorld: xmlBlaster has currently " +
                new String(msgs[0].getContent()) + " bytes of free memory\n");

         con.disconnect(null);
      }
      catch (Exception e) {
         System.err.println("We have a problem: " + e.getMessage());
      }
   }

   public static void main(String args[]) {
      new HelloWorld(args);
   }
}

