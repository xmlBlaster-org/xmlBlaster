package classtest;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.xml2java.PublishQos;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.Destination;

import junit.framework.*;

/**
 * Test PublishQos. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading classtest.PublishQosTest
 * @see org.xmlBlaster.client.PublishQos
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html" target="others">the interface.publish requirement</a>
 */
public class PublishQosTest extends TestCase {
   protected Global glob;
   int counter = 0;

   public PublishQosTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();
   }

   public void testParse() {
      System.out.println("***PublishQosTest: testParse ...");
      
      try {
         Timestamp timestamp = new Timestamp();
         String xml =
            "<qos>\n" +
            "   <destination queryType='EXACT' forceQueuing='true'>\n" +
            "      Tim\n" +
            "   </destination>\n" +
            "   <destination queryType='EXACT'>\n" +
            "      Ben\n" +
            "   </destination>\n" +
            "   <destination queryType='XPATH'>\n" +
            "      //[GROUP='Manager']\n" +
            "   </destination>\n" +
            "   <destination queryType='XPATH'>\n" +
            "      //ROLE/[@id='Developer']\n" +
            "   </destination>\n" +
            "   <sender>\n" +
            "      Gesa\n" +
            "   </sender>\n" +
            "   <priority>7</priority>\n" +
            "   <rcvTimestamp nanos='" + timestamp.getTimestamp() + "'/>\n" + // if from persistent store
            "   <expiration remainingLife='12000'/>\n" +            // if from persistent store
            "   <isVolatile>false</isVolatile>\n" +
            "   <isDurable/>\n" +
            "   <forceUpdate>false</forceUpdate>\n" +
            "   <readonly/>\n" +
            "   <route>\n" +
            "      <node id='bilbo' stratum='2' timestamp='9408630500' dirtyRead='true'/>\n" +
            "      <node id='frodo' stratum='1' timestamp='9408630538' dirtyRead='false'/>\n" +
            "      <node id='heron' stratum='0' timestamp='9408630564'/>\n" +
            "   </route>\n" +
            "</qos>\n";

         PublishQos qos = new PublishQos(glob, xml);

         assertEquals("", false, qos.isPubSubStyle());
         assertEquals("", true, qos.isPTP_Style());
         assertEquals("", false, qos.usesXPathQuery());
         assertEquals("", false, qos.isVolatile());
         assertEquals("", true, qos.isDurable());
         assertEquals("", false, qos.forceUpdate());
         assertEquals("", true, qos.readonly());
         assertEquals("", "Gesa", qos.getSender());

         assertEquals("", 3, qos.getRouteNodes().length);
         assertEquals("", 2, qos.getRouteNodes()[0].getStratum());
         assertEquals("", 0, qos.getRouteNodes()[2].getStratum());
         assertEquals("", 9408630500L, qos.getRouteNodes()[0].getTimestamp().getTimestamp());
         assertEquals("", true, qos.getRouteNodes()[0].getDirtyRead());
         assertEquals("", false, qos.getRouteNodes()[1].getDirtyRead());
         assertEquals("", false, qos.getRouteNodes()[2].getDirtyRead());

         assertEquals("", 7, qos.getPriority());
         assertEquals("", false, qos.isFromPersistenceStore());
         //assertEquals("", 1111, qos.getRemainingLife());
         assertTrue("", timestamp.getTimestamp() < qos.getRcvTimestamp().getTimestamp());
         assertEquals("", 4, qos.getDestinations().size());
         assertEquals("", true, ((Destination)qos.getDestinations().elementAt(0)).forceQueuing());
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***PublishQosTest: testParse [SUCCESS]");
   }

   public void testDefault() {
      System.out.println("***PublishQosTest: testDefault ...");
      
      try {
         Timestamp timestamp = new Timestamp();
         PublishQos qos = new PublishQos(glob, (String)null);
         //qos.addRouteInfo(new RouteInfo(new NodeId("master"), 0, new Timestamp(9408630587L)));
         assertEquals("", true, qos.isPubSubStyle());
         assertEquals("", false, qos.isPTP_Style());
         assertEquals("", false, qos.usesXPathQuery());
         assertEquals("", false, qos.isVolatile());
         assertEquals("", false, qos.isDurable());
         assertEquals("", true, qos.forceUpdate());
         assertEquals("", false, qos.readonly());
         assertEquals("", null, qos.getSender());
         assertEquals("", 0, qos.getRouteNodes().length);
         assertEquals("", 5, qos.getPriority());
         assertEquals("", false, qos.isFromPersistenceStore());
         assertTrue("", timestamp.getTimestamp() < qos.getRcvTimestamp().getTimestamp());
         assertTrue("", (qos.getRcvTimestamp().getTimestamp()-timestamp.getTimestamp()) < 2000000L); // nanos (2 sec)
         assertEquals("", null, qos.getDestinations());
      }
      catch (XmlBlasterException e) {
         fail("testDefault failed: " + e.toString());
      }

      System.out.println("***PublishQosTest: testDefault [SUCCESS]");
   }

   /**
    * <pre>
    *  java classtest.PublishQosTest
    * </pre>
    */
   public static void main(String args[])
   {
      PublishQosTest testSub = new PublishQosTest("PublishQosTest");
      testSub.setUp();
      testSub.testParse();
      testSub.testDefault();
      //testSub.tearDown();
   }
}
