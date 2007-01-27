/*------------------------------------------------------------------------------
Name:      TestMessages.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.jms;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

import javax.jms.JMSException;
import javax.jms.MessageNotWriteableException;

import java.util.logging.Logger;

import org.xmlBlaster.jms.MessageHelper;
import org.xmlBlaster.jms.XBBytesMessage;
import org.xmlBlaster.jms.XBDestination;
import org.xmlBlaster.jms.XBMessage;
import org.xmlBlaster.jms.XBTextMessage;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;

/**
 * Test Messages. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.TestJmsSubscribe
 * @see org.xmlBlaster.util.qos.ConnectQosData
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/jms.html" target="others">the jms requirement</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class TestMessages extends TestCase {
   protected Global glob;
   private static Logger log = Logger.getLogger(TestMessages.class.getName());
   int counter = 0, nmax;

   public TestMessages(String name) {
      super(name);
   }

   public void prepare(String[] args) {
      this.glob = new Global(args);
      // this.glob.init(args);
   }

   protected void setUp() {
      this.glob = Global.instance();

   }

   protected void tearDown() {
      
   }

   private void checkIfAllowed(XBMessage msg, String setter, String getter, Object val, boolean allowed) {
      try {
         String methodName = "get" + getter + "Property";
         Class[] argTypes = new Class[] {String.class };
         Object[] args = new Object[] { setter };
         Method method = XBMessage.class.getMethod(methodName, argTypes);
         try {
            log.info("checkIfAllowed: setter='" + setter + "', getter='" + getter + "' expected='" + allowed + "'");
            method.invoke(msg, args);
            assertTrue("the combination set" + setter + "Property / get" + getter + "Property should NOT be allowed", allowed);
         }
         catch (Exception ex) {
            if (ex instanceof JMSException) {
               if (allowed) ex.printStackTrace();
               assertTrue("the combination set" + setter + "Property / get" + getter + "Property should be allowed: " + ex.getMessage(), !allowed);
            }
         }
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("Something is probably wrong with this test", false);
      }
   }

   private void checkSetter(XBMessage msg, String name, Object obj) {
      try {
         Object obj1 = msg.getObjectProperty(name).getClass();
         assertEquals("Type of setter is incorrect ", obj, obj1);
      }
      catch (Exception ex) {
         assertTrue("exception should not occur when testing setter for '" + name + "' ", false);
      }
   }

   /**
    * Specified in the jms 1.1 spec Cap. 3.5.4
    * Tests the setting and getting of properties of all possible types
    * according to the specification for the javax.jms.Message
    * @see http://java.sun.com/j2ee/1.4/docs/api/javax.jms.Message.html
    */
   public void testPropertyValueConversion() {
      try {
         byte[] content = null;
         int type = XBMessage.TEXT;
         XBMessage msg = new XBMessage(null, content, type);

         String[] keys = new String[] {"Boolean", "Byte", "Short", "Int", "Long", "Float", "Double", "String"};
         Object[] values = new Object[] { new Boolean(false), new Byte((byte)1), new Short((short)2), new Integer(3), new Long(4), new Float(5.01), new Double(6.02), new String("7 String")  };
         
         msg.setBooleanProperty(keys[0], false);
         msg.setByteProperty(keys[1], (byte)1);
         msg.setShortProperty(keys[2], (short)2);
         msg.setIntProperty(keys[3], (int)3);
         msg.setLongProperty(keys[4], (long)4);
         msg.setFloatProperty(keys[5], (float)5.01);
         msg.setDoubleProperty(keys[6], (double)6.02);
         msg.setStringProperty(keys[7], "7 (String)");

         // prepare the result matrix (all others should  be false)
         boolean[][] allowed = new boolean[8][8];
         for (int j=0; j < 8; j++) {
            for (int i=0; i < 8; i++) allowed[j][i] = false;
         }
         
         for (int i=0; i < 8; i++) allowed[7][i] = true;
         for (int i=0; i < 8; i++) allowed[i][7] = true;
         allowed[0][0] = true;
         allowed[1][1] = true;
         allowed[1][2] = true;
         allowed[2][2] = true;
         allowed[1][3] = true;
         allowed[2][3] = true;
         allowed[3][3] = true;
         allowed[1][4] = true;
         allowed[2][4] = true;
         allowed[3][4] = true;
         allowed[4][4] = true;
         allowed[5][5] = true;
         allowed[5][6] = true;
         allowed[6][6] = true;

         // test first if all types are set correctly
         checkSetter(msg, "Boolean", Boolean.class);
         checkSetter(msg, "Byte", Byte.class);
         checkSetter(msg, "Short", Short.class);
         checkSetter(msg, "Int", Integer.class);
         checkSetter(msg, "Long", Long.class);
         checkSetter(msg, "Float", Float.class);
         checkSetter(msg, "Double", Double.class);
         checkSetter(msg, "String", String.class);

         for (int j=0; j < 8; j++) {
            for (int i=0; i < 8; i++) {
               checkIfAllowed(msg, keys[j], keys[i], values[j], allowed[j][i]);
            }
         }
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("exception occured ", false);
      }
   }

   public void testTextMessage() {
      try {
         { // 1. key, content and qos all null in constructor
            byte[] content = null;
            XBTextMessage msg = new XBTextMessage(null, content);

            String txt1 = "funny Things happen";
            msg.setText(txt1);
            String txt2 = msg.getText();
            assertEquals("normal text comparison", txt1, txt2);
         
            txt1 = null;
            msg.setText(txt1);
            txt2 = msg.getText();
            assertEquals("normal text comparison", txt1, txt2);
         }
         
         { // 2. content not null in constructor
            XBTextMessage msg = new XBTextMessage(null, "oh I am a text msg".getBytes());

            String txt1 = "funny Things happen";
            msg.setText(txt1);
            String txt2 = msg.getText();
            assertEquals("normal text comparison", txt1, txt2);
         
            txt1 = null;
            msg.setText(txt1);
            txt2 = msg.getText();
            assertEquals("normal text comparison", txt1, txt2);
         }
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("exception occured ", false);
      }
   }


   public void testBytesMessage() {
      try {
         {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[100];
            baos.write(buf);
            byte[] buf1 = baos.toByteArray();
            assertEquals("Wrong size of output buffer", buf.length, buf1.length);
         }
         { // 1. key, content and qos all null in constructor
            byte[] content = null;
            XBBytesMessage msg = new XBBytesMessage(null, content);
            
            content = new byte[256];
            for (int i=0; i < 256; i++) {
               content[i] = (byte)i;
            }
            
            msg.writeBytes(content, 0, content.length);
            XBDestination dest = new XBDestination("someTopic", null);
            msg.setJMSDestination(dest);
            msg.reset();
            
            byte[] content2 = new byte[256];
            msg.readBytes(content2);
            for (int i=0; i < content.length; i++)
               assertEquals("byte nr. '" + i + "' is wrong", content[i], content2[i]);
         }
         { // 1. key, content and qos all null in constructor
            byte[] content = null;
            XBBytesMessage msg = new XBBytesMessage(null, content);
            
            content = new byte[256];
            for (int i=0; i < 256; i++) {
               content[i] = (byte)i;
            }
            
            msg.writeBytes(content, 0, content.length);
            XBDestination dest = new XBDestination("someTopic", null);
            msg.setJMSDestination(dest);
            msg.reset();
            MsgUnit msgUnit = MessageHelper.convertToMessageUnit(new Global(), msg);
            
            byte[] content2 = msgUnit.getContent();
            assertEquals("Wrong length", content.length, content2.length);
            msg.readBytes(content2);
            for (int i=0; i < content.length; i++)
               assertEquals("byte nr. '" + i + "' is wrong", content[i], content2[i]);
         }
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("exception occured ", false);
      }
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.TestMessages
    * </pre>
    */
   public static void main(String args[])
   {
      TestMessages test = new TestMessages("TestMessages");
      test.prepare(args);

      test.setUp();
      test.testBytesMessage();
      test.tearDown();

      test.setUp();
      test.testPropertyValueConversion();
      test.tearDown();

      test.setUp();
      test.testTextMessage();
      test.tearDown();
   }
}
