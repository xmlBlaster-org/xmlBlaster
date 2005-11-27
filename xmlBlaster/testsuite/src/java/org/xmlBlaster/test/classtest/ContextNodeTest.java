package org.xmlBlaster.test.classtest;

import org.xmlBlaster.util.Global;
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

   public void testMerge() {
      System.out.println("***ContextNodeTest: testMerge ...");
      {
         ContextNode root = ContextNode.valueOf("/node/heron/client/joe");
         ContextNode other = ContextNode.valueOf("/node/xyz/client/joe/session/1");
         ContextNode leaf = root.mergeChildTree(other);
         System.out.println("Orig=" + root.getAbsoluteName() + " merge=" + other.getAbsoluteName() + " result=" + leaf.getAbsoluteName());
         assertEquals("merge", "/xmlBlaster/node/heron/client/joe/session/1", leaf.getAbsoluteName());
      }
      {
         ContextNode root = ContextNode.valueOf("/node/heron/client/joe/session/1");
         ContextNode other = ContextNode.valueOf("/node/xyz/client/joe/session/1");
         ContextNode leaf = root.mergeChildTree(other);
         System.out.println("Orig=" + root.getAbsoluteName() + " merge=" + other.getAbsoluteName() + " result=" + leaf.getAbsoluteName());
         assertEquals("merge", "/xmlBlaster/node/heron/client/joe/session/1", leaf.getAbsoluteName());
      }
      {
         ContextNode root = ContextNode.valueOf("/node/heron/client/joe/session/1");
         ContextNode other = ContextNode.valueOf("/node/xyz/service/Pop3Driver");
         ContextNode leaf = root.mergeChildTree(other);
         System.out.println("Orig=" + root.getAbsoluteName() + " merge=" + other.getAbsoluteName() + " result=" + leaf.getAbsoluteName());
         assertEquals("merge", "/xmlBlaster/node/heron/client/joe/session/1/service/Pop3Driver", leaf.getAbsoluteName());
      }
   }

   public void testParse() {
      System.out.println("***ContextNodeTest: testParse ...");
      try {
         String[] urls = { "/xmlBlaster/node/heron", "/node/heron" };
         for (int i=0; i<urls.length; i++) {
            String url = urls[i];
            System.out.println("Testing syntax parsing: " + url);
            ContextNode contextNode = ContextNode.valueOf(url);
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
         String[] urls = { "/xmlBlaster/node/heron/client/joe/session/1", "/node/heron" };
         for (int i=0; i<urls.length; i++) {
            String url = urls[i];
            System.out.println("Testing syntax parsing: " + url);
            ContextNode contextNode = ContextNode.valueOf(url);
            ContextNode cluster = contextNode.getParent(ContextNode.CLUSTER_MARKER_TAG);
            assertEquals("", "heron", cluster.getInstanceName());
         }
      }
      catch (IllegalArgumentException e) {
         fail("testParse failed: " + e.toString());
      }
         
      try {
         String[] urls = { "org.xmlBlaster:nodeClass=node,node=heron",
                           "org.xmlBlaster:nodeClass=node,node=heron,connectionClass=connection,connection=jack,queueClass=queue,queue=connection-99" };
         for (int i=0; i<urls.length; i++) {
            String url = urls[i];
            System.out.println("Testing JMX syntax parsing: " + url);
            ContextNode contextNode = ContextNode.valueOf(url);
            ContextNode cluster = contextNode.getParent(ContextNode.CLUSTER_MARKER_TAG);
            assertEquals("", "heron", cluster.getInstanceName());
         }
      }
      catch (IllegalArgumentException e) {
         fail("testParse failed: " + e.toString());
      }

      try {
         String url = "org.xmlBlaster:nodeClass=node,node=\"avalon\",connectionClass=connection,connection=\"jack\",queueClass=queue,queue=\"connection-99\"";
         ContextNode newParentNode = ContextNode.valueOf("org.xmlBlaster:nodeClass=node,node=heron");
         System.out.println("Testing JMX syntax parsing: " + url);
         ContextNode contextNode = ContextNode.valueOf(url);
         contextNode.changeParentName(newParentNode);
         String newString = contextNode.getAbsoluteName(ContextNode.SCHEMA_JMX);
         assertEquals("", "org.xmlBlaster:nodeClass=node,node=\"heron\",connectionClass=connection,connection=\"jack\",queueClass=queue,queue=\"connection-99\"",
                          newString);
      }
      catch (IllegalArgumentException e) {
         fail("testParse failed: " + e.toString());
      }

      try {
         ContextNode contextNode = ContextNode.valueOf(null);
         assertTrue("Expected null", contextNode==ContextNode.ROOT_NODE);
      }
      catch (IllegalArgumentException e) {
         fail(e.toString());
      }
         
      try {
         ContextNode contextNode = ContextNode.valueOf("");
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
      testSub.testMerge();
      //testSub.tearDown();
   }
}
