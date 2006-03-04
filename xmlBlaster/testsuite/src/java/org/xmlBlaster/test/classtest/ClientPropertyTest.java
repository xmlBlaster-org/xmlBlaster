package org.xmlBlaster.test.classtest;

import org.custommonkey.xmlunit.XMLTestCase;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.MsgQosSaxFactory;
import org.xmlBlaster.util.def.Constants;

/**
 * Test ClientProperty. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.ClientPropertyTest
 * @see org.xmlBlaster.util.qos.ClientProperty
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.clientProperty.html">The client.qos.clientProperty requirement</a>
 */
public class ClientPropertyTest extends XMLTestCase {
   protected Global glob;
   private static Logger log = Logger.getLogger(ClientPropertyTest.class.getName());
   int counter = 0;

   public ClientPropertyTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();

   }

   public void testClientProperty() throws Exception {
      ClientProperty clientProperty = new ClientProperty("StringKey", null, null);
      assertEquals("", "StringKey", clientProperty.getName());
      assertEquals("", null, clientProperty.getType());
      assertEquals("", null, clientProperty.getEncoding());
      assertEquals("", false, clientProperty.isBase64());
      assertEquals("", null, clientProperty.getValueRaw());
      assertEquals("", null, clientProperty.getStringValue());
      assertEquals("", null, clientProperty.getValueRaw());

      String xml = clientProperty.toXml();
      assertXpathExists("/clientProperty[@name='StringKey']", xml);
      System.out.println(xml);
   }

   public void testClientPropertyEncoding() throws Exception {
      {
         ClientProperty clientProperty = new ClientProperty("StringKey", "String", Constants.ENCODING_BASE64);
         assertEquals("", "StringKey", clientProperty.getName());
         assertEquals("", "String", clientProperty.getType());
         assertEquals("", Constants.ENCODING_BASE64, clientProperty.getEncoding());
         assertEquals("", true, clientProperty.isBase64());
         assertEquals("", null, clientProperty.getStringValue());
         assertEquals("", null, clientProperty.getValueRaw());

         String xml = clientProperty.toXml();
         assertXpathExists("/clientProperty[@name='StringKey']", xml);
         assertXpathExists("/clientProperty[@type='String']", xml);
         assertXpathExists("/clientProperty[@encoding='"+Constants.ENCODING_BASE64+"']", xml);
         System.out.println(xml);
         assertXMLEqual("comparing test xml to control xml",
                        "<clientProperty name='StringKey' type='String' encoding='base64'/>",
                        xml);

         clientProperty.setValue("BlaBlaBla");
         xml = clientProperty.toXml();
         assertEquals("Base64?", "QmxhQmxhQmxh", clientProperty.getValueRaw());
         assertEquals("", "BlaBlaBla", clientProperty.getStringValue());
         System.out.println(xml);
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
   }

   public void testClientPropertyCtorEncoding() throws Exception {
      {
         String value = "Bla<<";
         ClientProperty clientProperty = new ClientProperty("StringKey",
                                          ClientProperty.getPropertyType(value), null, value);
         assertEquals("", "StringKey", clientProperty.getName());
         assertEquals("", null, clientProperty.getType()); // defaults to String
         assertEquals("", Constants.ENCODING_BASE64, clientProperty.getEncoding());
         assertEquals("", true, clientProperty.isBase64());
         assertEquals("", value, clientProperty.getStringValue());
         //assertEquals("", null, clientProperty.getValueRaw());

         String xml = clientProperty.toXml();
         assertXpathExists("/clientProperty[@name='StringKey']", xml);
         assertXpathExists("/clientProperty[@encoding='"+Constants.ENCODING_BASE64+"']", xml);
         assertXMLEqual("comparing test xml to control xml",
                        "<clientProperty name='StringKey' encoding='base64'>QmxhPDw=</clientProperty>",
                        xml);
         System.out.println(xml);
      }
   }

   public void testClientPropertyAutoEncoding() throws Exception {
      ClientProperty clientProperty = new ClientProperty("StringKey", "", "");
      assertEquals("", "StringKey", clientProperty.getName());
      assertEquals("", "", clientProperty.getType());
      assertEquals("", "", clientProperty.getEncoding());
      assertEquals("", null, clientProperty.getStringValue());
      assertEquals("", null, clientProperty.getValueRaw());

      clientProperty.setValue("Bla<BlaBla");
      assertEquals("", Constants.ENCODING_BASE64, clientProperty.getEncoding());
      String xml = clientProperty.toXml();
      System.out.println(xml);
      assertEquals("Base64?", "QmxhPEJsYUJsYQ==", clientProperty.getValueRaw());
      assertEquals("", "Bla<BlaBla", clientProperty.getStringValue());
      System.out.println(xml);

      clientProperty.setValue("Bla]]>BlaBla");
      assertEquals("", Constants.ENCODING_BASE64, clientProperty.getEncoding());
      xml = clientProperty.toXml();
      //assertEquals("Base64?", "QmxhPD5CbGFCbGE=", clientProperty.getValueRaw());
      assertEquals("", "Bla]]>BlaBla", clientProperty.getStringValue());
      System.out.println(xml);
   }

   public void testClientPropertyTypes() throws Exception {
      {
         ClientProperty clientProperty = new ClientProperty("key", "int", null);
         assertEquals("", "key", clientProperty.getName());
         assertEquals("", "int", clientProperty.getType());
         assertEquals("", null, clientProperty.getEncoding());
         assertEquals("", null, clientProperty.getValueRaw());
         assertEquals("", null, clientProperty.getStringValue());
         assertEquals("", null, clientProperty.getObjectValue());

         clientProperty.setValue("9988");
         String xml = clientProperty.toXml();
         assertEquals("", "9988", clientProperty.getValueRaw());
         assertXMLEqual("comparing test xml to control xml",
                        "<clientProperty name='key' type='int'>9988</clientProperty>",
                        xml);
         assertEquals("", 9988, clientProperty.getIntValue());
         assertTrue("Expecting Integer", clientProperty.getObjectValue() instanceof Integer);
         System.out.println(xml);
      }
      {
         ClientProperty clientProperty = new ClientProperty("key", Constants.TYPE_BOOLEAN, null);
         assertEquals("", "key", clientProperty.getName());
         assertEquals("", "boolean", clientProperty.getType());
         assertEquals("", null, clientProperty.getEncoding());
         assertEquals("", null, clientProperty.getValueRaw());
         assertEquals("", null, clientProperty.getStringValue());
         assertEquals("", null, clientProperty.getObjectValue());

         clientProperty.setValue("true");
         String xml = clientProperty.toXml();
         assertEquals("", "true", clientProperty.getValueRaw());
         assertXMLEqual("comparing test xml to control xml",
                        "<clientProperty name='key' type='boolean'>true</clientProperty>",
                        xml);
         assertEquals("", true, clientProperty.getBooleanValue());
         assertTrue("", clientProperty.getObjectValue() instanceof Boolean);
         System.out.println(xml);
      }
      {
         ClientProperty clientProperty = new ClientProperty("key", Constants.TYPE_DOUBLE, null);
         assertEquals("", "key", clientProperty.getName());
         assertEquals("", "double", clientProperty.getType());
         assertEquals("", null, clientProperty.getEncoding());
         assertEquals("", null, clientProperty.getValueRaw());
         assertEquals("", null, clientProperty.getStringValue());
         assertEquals("", null, clientProperty.getObjectValue());

         clientProperty.setValue("12.78");
         String xml = clientProperty.toXml();
         assertEquals("", "12.78", clientProperty.getValueRaw());
         assertXMLEqual("comparing test xml to control xml",
                        "<clientProperty name='key' type='double'>12.78</clientProperty>",
                        xml);
         assertTrue("", 12.78 == clientProperty.getDoubleValue());
         assertTrue("", clientProperty.getObjectValue() instanceof Double);
         System.out.println(xml);
      }
      {
         ClientProperty clientProperty = new ClientProperty("key", Constants.TYPE_FLOAT, null);
         assertEquals("", "key", clientProperty.getName());
         assertEquals("", "float", clientProperty.getType());
         assertEquals("", null, clientProperty.getEncoding());
         assertEquals("", false, clientProperty.isBase64());
         assertEquals("", null, clientProperty.getValueRaw());
         assertEquals("", null, clientProperty.getStringValue());
         assertEquals("", null, clientProperty.getObjectValue());

         clientProperty.setValue("12.54");
         String xml = clientProperty.toXml();
         assertEquals("", "12.54", clientProperty.getValueRaw());
         assertXMLEqual("comparing test xml to control xml",
                        "<clientProperty name='key' type='float'>12.54</clientProperty>",
                        xml);
         assertTrue("", (float)12.54 == clientProperty.getFloatValue());
         assertTrue("", clientProperty.getObjectValue() instanceof Float);
         System.out.println(xml);
      }
      {
         ClientProperty clientProperty = new ClientProperty("key", Constants.TYPE_BYTE, null);
         assertEquals("", "key", clientProperty.getName());
         assertEquals("", "byte", clientProperty.getType());
         assertEquals("", null, clientProperty.getEncoding());
         assertEquals("", null, clientProperty.getValueRaw());
         assertEquals("", null, clientProperty.getStringValue());
         assertEquals("", null, clientProperty.getObjectValue());

         clientProperty.setValue("6");
         String xml = clientProperty.toXml();
         assertEquals("", "6", clientProperty.getValueRaw());
         assertXMLEqual("comparing test xml to control xml",
                        "<clientProperty name='key' type='byte'>6</clientProperty>",
                        xml);
         assertTrue("", (byte)6 == clientProperty.getByteValue());
         assertTrue("", clientProperty.getObjectValue() instanceof Byte);
         System.out.println(xml);
      }
      {
         ClientProperty clientProperty = new ClientProperty("key", Constants.TYPE_LONG, null);
         assertEquals("", "key", clientProperty.getName());
         assertEquals("", "long", clientProperty.getType());
         assertEquals("", null, clientProperty.getEncoding());
         assertEquals("", null, clientProperty.getValueRaw());
         assertEquals("", null, clientProperty.getStringValue());
         assertEquals("", null, clientProperty.getObjectValue());

         clientProperty.setValue("888888");
         String xml = clientProperty.toXml();
         assertEquals("", "888888", clientProperty.getValueRaw());
         assertXMLEqual("comparing test xml to control xml",
                        "<clientProperty name='key' type='long'>888888</clientProperty>",
                        xml);
         assertTrue("", 888888 == clientProperty.getLongValue());
         assertTrue("", clientProperty.getObjectValue() instanceof Long);
         System.out.println(xml);
      }
      {
         ClientProperty clientProperty = new ClientProperty("key", Constants.TYPE_SHORT, null);
         assertEquals("", "key", clientProperty.getName());
         assertEquals("", "short", clientProperty.getType());
         assertEquals("", null, clientProperty.getEncoding());
         assertEquals("", null, clientProperty.getValueRaw());
         assertEquals("", null, clientProperty.getStringValue());
         assertEquals("", null, clientProperty.getObjectValue());

         clientProperty.setValue("12");
         String xml = clientProperty.toXml();
         assertEquals("", "12", clientProperty.getValueRaw());
         assertXMLEqual("comparing test xml to control xml",
                        "<clientProperty name='key' type='short'>12</clientProperty>",
                        xml);
         assertTrue("", 12 == clientProperty.getShortValue());
         assertTrue("", clientProperty.getObjectValue() instanceof Short);
         System.out.println(xml);
      }
      {
         ClientProperty clientProperty = new ClientProperty("key", null, null);
         assertEquals("", "key", clientProperty.getName());
         assertEquals("", null, clientProperty.getType());
         assertEquals("", null, clientProperty.getEncoding());
         assertEquals("", null, clientProperty.getValueRaw());
         assertEquals("", null, clientProperty.getStringValue());
         assertEquals("", null, clientProperty.getObjectValue());

         byte[] bb = new byte[6];
         bb[0] = 0;
         bb[1] = 'A';
         bb[2] = 0;
         bb[3] = 99;
         bb[4] = 0;
         bb[5] = 0;
         clientProperty.setValue(bb);
         assertEquals("", "byte[]", clientProperty.getType());
         assertEquals("", Constants.ENCODING_BASE64, clientProperty.getEncoding());
         assertEquals("", true, clientProperty.isBase64());
         String xml = clientProperty.toXml();
         byte[] newVal = clientProperty.getBlobValue();
         for (int i=0; i<bb.length; i++) 
            assertTrue("Index #"+i, bb[i] == newVal[i]);
         assertXpathExists("/clientProperty[@name='key']", xml);
         assertXpathExists("/clientProperty[@type='"+Constants.TYPE_BLOB+"']", xml);
         assertXpathExists("/clientProperty[@encoding='"+Constants.ENCODING_BASE64+"']", xml);
         assertEquals("", "AEEAYwAA", clientProperty.getValueRaw());
         assertXMLEqual("comparing test xml to control xml",
                        "<clientProperty name='key' type='byte[]' encoding='base64'>AEEAYwAA</clientProperty>",
                        xml);
         assertTrue("", clientProperty.getObjectValue() instanceof byte[]);
         newVal = (byte[])clientProperty.getObjectValue();
         for (int i=0; i<bb.length; i++) 
            assertTrue("Index #"+i, bb[i] == newVal[i]);
         System.out.println(xml);
      }
   }

   public void testClientPropertyParsing() throws Exception {
      
      String xml =  "<qos>\n" +
         "  <isPublish/>\n" + 
         "  <clientProperty name='StringKey' type=''><![CDATA[Bla<BlaBla]]></clientProperty>\n" + 
         "</qos>";      
      
      MsgQosSaxFactory parser = new MsgQosSaxFactory(this.glob);
      MsgQosData data = parser.readObject(xml);
      ClientProperty prop = data.getClientProperty("StringKey");
      System.out.println(prop.toXml());
      assertEquals("", true, prop.isBase64());
      
   }

   public void testClientPropertyEnclosedXmlTree() throws Exception {
      
      String xml =  "<qos>\n" +
         "  <isPublish/>\n" + 
         "  <clientProperty name='StringKey' type=''><BlaBla attr1='val1' attr2=' val2 '> Something </BlaBla></clientProperty>\n" + 
         "</qos>";      
      
      MsgQosSaxFactory parser = new MsgQosSaxFactory(this.glob);
      MsgQosData data = parser.readObject(xml);
      ClientProperty prop = data.getClientProperty("StringKey");
      System.out.println(prop.toXml());
      // assertEquals("", true, prop.isBase64());
      
      
      String val = "<BlaBla attr1='val1' attr2=' val2 '> Something </BlaBla>";
      prop = new ClientProperty("StringKey", null, Constants.ENCODING_FORCE_PLAIN, val);
      System.out.println(prop.toXml());

      xml =  "<qos>\n" +
      "  <isPublish/>\n" + 
      "  <clientProperty name='StringKey' type='' encoding='forcePlain'><qos attr1='val1' attr2=' val2 '> Something </qos></clientProperty>\n" + 
      "</qos>";      
      
      parser = new MsgQosSaxFactory(this.glob);
      data = parser.readObject(xml);
      prop = data.getClientProperty("StringKey");
      System.out.println(prop.toXml());
      
      
      
      xml =  "<qos>\n" +
      "  <isPublish/>\n" + 
      "  <clientProperty name='StringKey' type='' encoding='forcePlain'><clientProperty name='aaa' type='' encoding=''>Something</clientProperty></clientProperty>\n" + 
      "</qos>";      
      
      parser = new MsgQosSaxFactory(this.glob);
      data = parser.readObject(xml);
      prop = data.getClientProperty("StringKey");
      System.out.println(prop.toXml());
      
      
      
      
      System.out.println("END");
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
         testSub.testClientPropertyEnclosedXmlTree();
         testSub.testClientProperty();
         testSub.testClientPropertyEncoding();
         testSub.testClientPropertyCtorEncoding();
         testSub.testClientPropertyTypes();
         testSub.testClientPropertyAutoEncoding();
         testSub.testClientPropertyParsing();
         testSub.tearDown();
      }
      catch(Throwable e) {
         e.printStackTrace();
         fail(e.toString());
      }
   }
}
