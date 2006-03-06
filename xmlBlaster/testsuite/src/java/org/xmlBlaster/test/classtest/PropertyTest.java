package org.xmlBlaster.test.classtest;

import java.util.Map;

import org.custommonkey.xmlunit.XMLTestCase;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.context.ContextNode;

/**
 * Test ClientProperty. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.PropertyTest
 * @see org.xmlBlaster.util.qos.ClientProperty
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.clientProperty.html">The client.qos.clientProperty requirement</a>
 */
public class PropertyTest extends XMLTestCase {
   protected Global glob;
   int counter = 0;

   public PropertyTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();

   }

   public void testPropertyStartingWith() throws Exception {
      
      String[] args = new String[] { 
           "-logging", "FINE",
           "-/node/heron/logging", "FINE",
           "-logging/org.xmlBlaster.engine.level", "FINE",
           "-/node/heron/logging/org.xmlBlaster.engine.level", "FINE"
      };
      
      Global glob = new Global(args);

      ContextNode ctx = ContextNode.valueOf("/node/heron");
      Map props = glob.getProperty().getPropertiesForContextNode(ctx, "logging", "__default");
      
      assertEquals("Number of entries found", 3, props.size());
   }


   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.PropertyTest
    * </pre>
    */
   public static void main(String args[])
   {
      try {
         PropertyTest testSub = new PropertyTest("PropertyTest");
         testSub.setUp();
         testSub.testPropertyStartingWith();
         testSub.tearDown();
      }
      catch(Throwable e) {
         e.printStackTrace();
         fail(e.toString());
      }
   }
}
