/*------------------------------------------------------------------------------
Name:      StopXmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Stop xmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.test;

import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;

/**
 * This client connects to xmlBlaster and stops it with a command message. 
 * <p />
 * Invoke: java org.xmlBlaster.test.StopXmlBlaster
 */
public class StopXmlBlaster extends TestCase
{
   String[] args = new String[0];

   public StopXmlBlaster() { // For Junit invoke
   }

   public StopXmlBlaster(String[] args) { // Used by our main
      this.args = args;
      testStop();
   }

   /** Stop xmlBlaster server (invoked by junit automatically as name starts with 'test') */
   public void testStop() {
      try {
         XmlBlasterConnection con = new XmlBlasterConnection(args);

         ConnectQos qos = new ConnectQos(null, "joe", "secret");
         con.connect(qos, null);

         con.publish(new MessageUnit("<key oid='__cmd:?exit=0'/>", "".getBytes(), "<qos/>"));

         con.disconnect(null);

         // xmlBlaster shuts down 2 sec later + time to process shutdown
         try { Thread.currentThread().sleep(4000L); } catch( InterruptedException i) {}

         try {
            XmlBlasterConnection con2 = new XmlBlasterConnection(args);
            con2.connect(qos, null);
            fail("No connection expected");
         }
         catch(org.xmlBlaster.util.XmlBlasterException e) {
            System.err.println("Success, connection not possible any more");
         }
      }
      catch (Exception e) {
         System.err.println(e.toString());
         fail(e.toString());
      }
   }

   public static void main(String args[]) {
      new StopXmlBlaster(args);
   }
}
