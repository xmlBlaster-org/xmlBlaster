/*------------------------------------------------------------------------------
Name:      StopXmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Stop xmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.test;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.Global;

import junit.framework.*;

/**
 * This client connects to xmlBlaster and stops it with a command message.
 * <p />
 * Invoke: java org.xmlBlaster.test.StopXmlBlaster
 */
public class StopXmlBlaster extends TestCase
{
   Global glob;

   public StopXmlBlaster(String name) { // For Junit invoke
      super(name);
      this.glob = Global.instance();
   }

   public StopXmlBlaster(String[] args, String name) { // Used by our main
      super(name);
      this.glob = new Global(args);
      testStop();
   }

   /** Stop xmlBlaster server (invoked by junit automatically as name starts with 'test') */
   public void testStop() {
      try {
         I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(glob, "joe", "secret");
         con.connect(qos, null);

         con.publish(new MsgUnit("<key oid='__cmd:?exit=0'/>", "".getBytes(), "<qos/>"));

         con.disconnect(null);

         // xmlBlaster shuts down 2 sec later + time to process shutdown
         try { Thread.sleep(4000L); } catch( InterruptedException i) {}

         try {
            Global glob2 = this.glob.getClone(null);
            I_XmlBlasterAccess con2 = glob2.getXmlBlasterAccess();
            ConnectQos connectQos = new ConnectQos(glob2, "joe", "secret");
            con2.connect(connectQos, null);
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
      new StopXmlBlaster(args, "StopXmlBlaster");
   }
}
