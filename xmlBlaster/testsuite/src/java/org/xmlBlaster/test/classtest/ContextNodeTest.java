package org.xmlBlaster.test.classtest;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;

import junit.framework.*;

/**
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.ContextNodeTest
 * @see org.xmlBlaster.util.ContextNode
 */
public class ContextNodeTest extends TestCase {
   protected Global glob;
   int counter = 0;

   public ContextNodeTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();
   }

   public void testParse() {
      System.out.println("***ContextNodeTest: testParse ...");
      try {
         String[] urls = { "/xmlBlaster/node/heron", "/node/heron" };
         for (int i=0; i<urls.length; i++) {
            String url = urls[i];
            ContextNode contextNode = ContextNode.valueOf(glob, url);
            assertEquals("", "heron", contextNode.getInstanceName());
            assertEquals("", ContextNode.CLUSTER_MARKER_TAG, contextNode.getClassName());
            assertTrue("parent not null", contextNode.getParent() == null);
            assertEquals("", "/xmlBlaster/node/heron", contextNode.getAbsoluteName());
            assertEquals("", "node/heron", contextNode.getRelativeName());
         }
      }
      catch (IllegalArgumentException e) {
         fail("testParse failed: " + e.toString());
      }
         
         
      try {
         ContextNode contextNode = ContextNode.valueOf(glob, null);
         assertTrue("Expected null", contextNode==ContextNode.ROOT_NODE);
      }
      catch (IllegalArgumentException e) {
         fail(e.toString());
      }
         
      try {
         ContextNode contextNode = ContextNode.valueOf(glob, "");
         assertTrue("Expected null", contextNode==ContextNode.ROOT_NODE);
      }
      catch (IllegalArgumentException e) {
         fail(e.toString());
      }
         
      System.out.println("***ContextNodeTest: testParse [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.ContextNodeTest
    * </pre>
    */
   public static void main(String args[])
   {
      ContextNodeTest testSub = new ContextNodeTest("ContextNodeTest");
      testSub.setUp();
      testSub.testParse();
      //testSub.tearDown();
   }
}
