// xmlBlaster/demo/javaclients/HelloWorld.java
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.XmlBlasterAccess;
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
      }
      catch (Exception e) {
         System.err.println("HelloWorld: We have a problem: " + e.getMessage());
      }
   }

   public static void main(String args[]) {
      new HelloWorld(args);
   }
}

