package org.xmlBlaster.test.classtest;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.enum.Constants;

import junit.framework.*;
import org.custommonkey.xmlunit.*;

/**
 * Test ClientProperty. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.ClientPropertyTest
 * @see org.xmlBlaster.util.qos.ClientPropertyData
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html" target="others">the interface.connect requirement</a>
 */
public class ClientPropertyTest extends XMLTestCase {
   protected Global glob;
   protected LogChannel log;
   int counter = 0;

   public ClientPropertyTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();
      this.log = this.glob.getLog("test");
   }

   public void testClientProperty() throws Exception {
      ClientProperty clientProperty = new ClientProperty(this.glob, "StringKey", null, null);
      assertEquals("", "StringKey", clientProperty.getName());
      assertEquals("", null, clientProperty.getType());
      assertEquals("", null, clientProperty.getEncoding());
      assertEquals("", null, clientProperty.getValueRaw());
      assertEquals("", null, clientProperty.getStringValue());
      assertEquals("", null, clientProperty.getValueRaw());

      String xml = clientProperty.toXml();
      assertXpathExists("/clientProperty[@name='StringKey']", xml);
      System.out.println(xml);
   }

   public void testClientPropertyEncoding() throws Exception {
      ClientProperty clientProperty = new ClientProperty(this.glob, "StringKey", "String", Constants.ENCODING_BASE64);
      assertEquals("", "StringKey", clientProperty.getName());
      assertEquals("", "String", clientProperty.getType());
      assertEquals("", Constants.ENCODING_BASE64, clientProperty.getEncoding());
      assertEquals("", null, clientProperty.getStringValue());
      assertEquals("", null, clientProperty.getValueRaw());

      String xml = clientProperty.toXml();
      assertXpathExists("/clientProperty[@name='StringKey']", xml);
      assertXpathExists("/clientProperty[@type='String']", xml);
      assertXpathExists("/clientProperty[@encoding='"+Constants.ENCODING_BASE64+"']", xml);
      assertXMLEqual("comparing test xml to control xml",
                     "<clientProperty name='StringKey' type='String' encoding='base64'/>",
                     xml);

      clientProperty.setValue("BlaBlaBla");
      xml = clientProperty.toXml();
      assertEquals("Base64?", "QmxhQmxhQmxh", clientProperty.getValueRaw());
      assertEquals("", "BlaBlaBla", clientProperty.getStringValue());
      assertXMLEqual("comparing test xml to control xml",
                     "<clientProperty name='StringKey' type='String' encoding='base64'>QmxhQmxhQmxh</clientProperty>",
                     xml);
      try {
         assertEquals("", 99, clientProperty.getIntValue());
         fail("String to int not possible");
      }
      catch(java.lang.NumberFormatException e) {
         System.out.println("OK Expected exception NumberFormatException");
      }
      System.out.println(xml);
   }

   public void testClientPropertyTypes() throws Exception {
      ClientProperty clientProperty = new ClientProperty(this.glob, "intKey", "int", null);
      assertEquals("", "intKey", clientProperty.getName());
      assertEquals("", "int", clientProperty.getType());
      assertEquals("", null, clientProperty.getEncoding());
      assertEquals("", null, clientProperty.getValueRaw());
      assertEquals("", null, clientProperty.getStringValue());

      clientProperty.setValue("9988");
      String xml = clientProperty.toXml();
      assertEquals("", "9988", clientProperty.getValueRaw());
      assertXMLEqual("comparing test xml to control xml",
                     "<clientProperty name='intKey' type='int'>9988</clientProperty>",
                     xml);
      assertEquals("", 9988, clientProperty.getIntValue());
      System.out.println(xml);
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.ClientPropertyTest
    * </pre>
    */
   public static void main(String args[])
   {
      try {
         ClientPropertyTest testSub = new ClientPropertyTest("ClientPropertyTest");
         testSub.setUp();
         testSub.testClientProperty();
         testSub.testClientPropertyEncoding();
         testSub.testClientPropertyTypes();
         //testSub.tearDown();
      }
      catch(Throwable e) {
         fail(e.toString());
      }
   }
}
