package org.xmlBlaster.test.classtest;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.DisconnectQosJsonFactory;
import org.xmlBlaster.util.qos.DisconnectQosData;
import org.xmlBlaster.util.qos.DisconnectQosSaxFactory;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.engine.qos.DisconnectQosServer;

import junit.framework.*;

/**
 * Test DisconnectQos.
 * <p />
 * All methods starting with 'test' and without arguments are invoked
 * automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading
 * org.xmlBlaster.test.classtest.DisconnectQosTest
 * 
 * @see org.xmlBlaster.client.qos.DisconnectQos
 * @see <a href=
 *      "http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html"
 *      target="others">the interface.disconnect requirement</a>
 */
public class DisconnectQosTest extends TestCase {

   protected Global glob;
   int counter = 0;
   private DisconnectQosSaxFactory factory;
   private DisconnectQosJsonFactory jsonFactory;

   public DisconnectQosTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();
      this.factory = new DisconnectQosSaxFactory(glob);
      this.jsonFactory = new DisconnectQosJsonFactory(glob);
   }

   public void testParse() {
      System.out.println("***DisconnectQosTest: testParse ...");

      try {
         DisconnectQosServer qos = new DisconnectQosServer(this.glob,
               """
                     <qos>
                        <deleteSubjectQueue>false</deleteSubjectQueue>
                        <clearSessions>true</clearSessions>
                        <clientProperty name='__asyncUnsubscribeWithoutSubscriptionIdAllowed' type='boolean'>true</clientProperty>
                     </qos>
                     """);
         // print and reparse
         qos = new DisconnectQosServer(glob, factory.readObject(qos.toXml()));
         // test
         assertEquals("deleteSubjectQueue failed", false, qos.deleteSubjectQueue());
         assertEquals("clearSessions failed", true, qos.clearSessions());
         // client property
         DisconnectQosData qosData = qos.getData();
         ClientProperty cp = qosData
               .getClientProperty(UnSubscribeQos.CP_ASYNC_UNSUBSCRIBE_WITHOUT_SUBSCRIPTIONID_ALLOWED);

         assertNotNull(cp);
         assertEquals(true, cp.getBooleanValue());
         String xml = qos.toXml();
         System.out.println("XML:\n" + xml);

         // parse Json
         String json = jsonFactory.writeObject(factory.readObject(xml), "", null);
         System.out.println("Json:\n" + json);
         qos = new DisconnectQosServer(glob, jsonFactory.readObject(json));
         assertEquals("deleteSubjectQueue failed", false, qos.deleteSubjectQueue());
         assertEquals("clearSessions failed", true, qos.clearSessions());
         // client property
         qosData = qos.getData();
         cp = qosData.getClientProperty(UnSubscribeQos.CP_ASYNC_UNSUBSCRIBE_WITHOUT_SUBSCRIPTIONID_ALLOWED);
         // test default
         qos = new DisconnectQosServer(this.glob, "<qos/>");
         assertEquals("deleteSubjectQueue failed", true, qos.deleteSubjectQueue());
         assertEquals("clearSessions failed", false, qos.clearSessions());

      } catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }
      System.out.println("***DisconnectQosTest: testParse [SUCCESS]");
   }

   public void testDefault() {
      System.out.println("***DisconnectQosTest: testDefault ...");
      DisconnectQos qos = new DisconnectQos(this.glob);
      assertEquals("deleteSubjectQueue failed", true, qos.deleteSubjectQueue());
      assertEquals("clearSessions failed", false, qos.clearSessions());
      System.out.println("***DisconnectQosTest: testDefault [SUCCESS]");

   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.DisconnectQosTest
    * </pre>
    */
   public static void main(String args[])
   {
      DisconnectQosTest testSub = new DisconnectQosTest("DisconnectQosTest");
      testSub.setUp();
      testSub.testParse();
      testSub.testDefault();
      //testSub.tearDown();
   }
}
