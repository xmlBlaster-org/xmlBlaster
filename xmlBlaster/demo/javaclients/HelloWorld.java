// xmlBlaster/demo/javaclients/HelloWorld.java

import org.xmlBlaster.util.Log;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This client connects to xmlBlaster and gets synchronous a message and disconnects. 
 * Invoke: java HelloWorld
 */
public class HelloWorld
{
   public HelloWorld(String[] args) {
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(args);

         con.connect(null, null);    // Login to xmlBlaster as "guest"

         MessageUnit[] msgs = con.get("<key oid='__sys__FreeMem'/>", null);

         Log.info("HelloWorld", "xmlBlaster has currently " + new String(msgs[0].getContent()) +
                      " bytes of free memory");

         con.disconnect(null);
      }
      catch (Exception e) {
         Log.panic("", e.toString());
      }
   }

   public static void main(String args[]) {
      new HelloWorld(args);
   }
}

