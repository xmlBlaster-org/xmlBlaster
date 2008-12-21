package org.xmlBlaster.test.classtest.qos;

import java.util.Hashtable;
import java.util.logging.Logger;

import junit.framework.TestCase;

import org.xmlBlaster.util.Base64;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.address.Destination;

/**
 * Test MsgQosSaxFactory. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.qos.MsgQosTest
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html" target="others">the interface.publish requirement</a>
 */
public class MsgQosTest extends TestCase {
   private final String ME = "MsgQosTest";
   protected Global glob;
   private static Logger log = Logger.getLogger(MsgQosTest.class.getName());
   int counter = 0;

   public MsgQosTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();

   }

   public void testMethods() {
      System.out.println("***MsgQosTest: testMethods ...");
      
      MsgQosData qos = new MsgQosData(this.glob, MethodName.UPDATE);

      qos.setState("AA");
      assertEquals("state", "AA", qos.getState());

      ClientProperty cp = new ClientProperty("aKey", "byte[]", Constants.ENCODING_BASE64, "bla");
      qos.addClientProperty(cp);
      Hashtable jxPath = qos.toJXPath();
      String value = (String)jxPath.get("/qos/clientProperty[@name='aKey']/text()");
      String bla = Base64.encode("bla".getBytes());
      assertEquals("JXPATH", bla, value);
      String type = (String)jxPath.get("/qos/clientProperty[@name='aKey']/@type");
      assertEquals("JXPATH", "byte[]", type);
      String encoding = (String)jxPath.get("/qos/clientProperty[@name='aKey']/@encoding");
      assertEquals("JXPATH", Constants.ENCODING_BASE64, encoding);


      /*
      //qos.addRouteInfo(new RouteInfo(new NodeId("master"), 0, new Timestamp(9408630587L)));
      assertEquals("", true, qos.isSubscribable());
      assertEquals("", false, qos.isPtp());
      assertEquals("", false, qos.isVolatile());
      assertEquals("", false, qos.isAdministrative());
      assertEquals("", false, qos.isPersistent());
      assertEquals("", true, qos.isForceUpdate());
      assertEquals("", false, qos.isReadonly());
      assertEquals("", null, qos.getSender());
      assertEquals("", 0, qos.getRouteNodes().length);
      assertEquals("", PriorityEnum.NORM_PRIORITY, qos.getPriority());
      assertEquals("", false, qos.isFromPersistenceStore());
      assertTrue("", qos.getRcvTimestamp() == null);
      assertEquals("", null, qos.getDestinations());



      assertEquals("", "SOMETHING", qos.getStateInfo());
      assertEquals("", true, qos.isPtp());
      assertEquals("", true, qos.isAdministrative());
      assertEquals("", true, qos.isPersistent());
      assertEquals("", false, qos.isForceUpdate());
      assertEquals("", false, qos.isReadonly());
      assertEquals("", "Gesa", qos.getSender().getLoginName());

      assertEquals("", 0L, qos.getLifeTime()); // PtP message (because of Destination) is volatile
      assertEquals("", -1L, qos.getRemainingLifeStatic());

      assertEquals("", 3, qos.getRouteNodes().length);
      assertEquals("", 2, qos.getRouteNodes()[0].getStratum());
      assertEquals("", 0, qos.getRouteNodes()[2].getStratum());
      assertEquals("", 9408630500L, qos.getRouteNodes()[0].getTimestamp().getTimestamp());
      assertEquals("", true, qos.getRouteNodes()[0].getDirtyRead());
      assertEquals("", false, qos.getRouteNodes()[1].getDirtyRead());
      assertEquals("", false, qos.getRouteNodes()[2].getDirtyRead());

      assertEquals("", PriorityEnum.MIN_PRIORITY, qos.getPriority());
      assertEquals("", false, qos.isFromPersistenceStore());
      assertTrue("no receive timestamp expected", qos.getRcvTimestamp() == null);
      assertEquals("", true, qos.isSubscribable());
      assertEquals("", 2, qos.getDestinations().size());
      assertEquals("", true, ((Destination)qos.getDestinations().get(0)).forceQueuing());
      assertEquals("", true, ((Destination)qos.getDestinations().get(0)).isExactAddress());
      assertEquals("", false, ((Destination)qos.getDestinations().get(0)).isXPathQuery());
      // XPath is currently not supported
      //assertEquals("", false, ((Destination)qos.getDestinations().get(2)).isExactAddress());
      //assertEquals("", true, ((Destination)qos.getDestinations().get(2)).isXPathQuery());
      */

      System.out.println("***MsgQosTest: testMethods [SUCCESS]");
   }

   public void testDestination() {
      System.out.println("***MsgQosTest: testDestination ...");

      MsgQosData qos = new MsgQosData(this.glob, MethodName.PUBLISH);
      assertEquals("", null, qos.getDestinations());
      
      Destination destination = new Destination("a@b", Constants.EXACT);

      assertNull("", qos.getDestinations());
      assertEquals("", 0, qos.getNumDestinations());
      assertEquals("", 0, qos.getDestinationArr().length);

      qos.addDestination(destination);
      assertNotNull("", qos.getDestinations());
      assertEquals("", 1, qos.getDestinations().size());
      assertEquals("", 1, qos.getNumDestinations());
      assertEquals("", 1, qos.getDestinationArr().length);

      MsgQosData qos2 = (MsgQosData)qos.clone();
      assertNotNull("", qos2.getDestinations());
      assertEquals("", 1, qos2.getDestinations().size());
      assertEquals("", 1, qos2.getNumDestinations());
      assertEquals("", 1, qos2.getDestinationArr().length);

      String xml = qos.toXml();
      MsgQosData qos3 = (MsgQosData)qos.clone();


      //dest.setDestination(new SessionName(Global.instance(), "Johann"));

      System.out.println("***MsgQosTest: testDestination [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.qos.MsgQosTest
    * </pre>
    */
   public static void main(String args[])
   {
      MsgQosTest testSub = new MsgQosTest("MsgQosTest");
      testSub.setUp();
      //testSub.testMethods();
      testSub.testDestination();
      //testSub.tearDown();
   }
}
