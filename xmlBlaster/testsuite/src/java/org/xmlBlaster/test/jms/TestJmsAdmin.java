/*------------------------------------------------------------------------------
Name:      TestJmsAdmin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.jms;

import java.util.Hashtable;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.naming.NamingService;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.jms.XBConnectionFactory;
import org.xmlBlaster.jms.XBPropertyNames;
import org.xmlBlaster.jms.XBTopic;

/**
 * Test JmsAdmin. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.TestJmsAdmin
 * @see org.xmlBlaster.util.qos.ConnectQosData
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/jms.html" target="others">the jms requirement</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class TestJmsAdmin extends XMLTestCase {
   private final static String ME = "TestJmsAdmin";
   private final static String CONNECTION_FACTORY = "connectionFactory";
   private final static String TOPIC = "jmsAdmin";
   protected Global glob;
   protected LogChannel log;
   
   private String[] args;
   private NamingService namingService;
   private Hashtable env;
   private ConnectQos qos;
   
   
   public TestJmsAdmin(String name) {
      super(name);
      XMLUnit.setIgnoreWhitespace(true);
      try {
         this.namingService = new NamingService();
         this.namingService.start(); 
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("exception in constructor when starting naming service", false);
      }

      this.qos = new ConnectQos(this.glob);
      this.qos.addClientProperty("one", "1");
      this.qos.setPersistent(true);
      this.qos.setMaxSessions(100000);
      this.env = new Hashtable();
      this.env.put(XBPropertyNames.CONNECT_QOS, qos.toXml());
   
   }

   public void finalize() {
      this.namingService.stop(); 
   }

   public void prepare(String[] args) {
      //this.args = args;
      this.glob = new Global(args);
      this.glob.getLog("test");
   }

   protected void setUp() {
      this.glob = Global.instance();
      this.log = this.glob.getLog("test");
      adminJmsStart();
   }

   protected void tearDown() {
      try {
         InitialContext ctx = new InitialContext(this.env);
         ctx.unbind(CONNECTION_FACTORY);
         ctx.unbind(TOPIC);
      }
      catch (NamingException ex) {
         ex.printStackTrace();
         assertTrue("exception when unbinding", false);
      }
   }
   
   protected void adminJmsStart() {
      try {
         // System.setProperty("java.naming.factory.initial", "org.apache.naming.modules.memory.MemoryURLContextFactory");
         // System.setProperty("java.naming.factory.url.pkgs", "org.apache.naming.modules");
         InitialContext ctx = new InitialContext(this.env);
         ctx.bind(CONNECTION_FACTORY, new XBConnectionFactory(null, this.args, false));            
         ctx.bind(TOPIC, new XBTopic(TOPIC));
      }
      catch (NamingException ex) {
         ex.printStackTrace();
         assertTrue("exception occured in testJndi", false);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("exception when starting naming service", false);
      }
   }
   
   /**
    * Checks if the connectQos passed to the initial context also reaches the ConnectionFactrory correcty
    *
    */
   public void testConnectionFactory() {
      try {

         InitialContext ctx = new InitialContext(this.env);
         XBConnectionFactory factory = (XBConnectionFactory)ctx.lookup(CONNECTION_FACTORY);
         ConnectQos qos1 = factory.getConnectQos();

         if (this.log.TRACE) {
            this.log.plain("", "--------------------------------------");
            this.log.plain("", qos.toXml());
            this.log.plain("", "--------------------------------------");
            this.log.plain("", qos1.toXml());
            this.log.plain("", "--------------------------------------");
         }
         
         assertXMLEqual(qos.toXml(), qos1.toXml());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("naming exception", false);
      }
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.TestJmsAdmin
    * </pre>
    */
   public static void main(String args[])
   {
      TestJmsAdmin test = new TestJmsAdmin("TestJmsAdmin");
      test.prepare(args);
      
      test.setUp();
      test.testConnectionFactory();
      test.tearDown();
      
   }
}
