package org.xmlBlaster.test.classtest;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.cluster.NodeId;

import junit.framework.*;

/**
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.SessionNameTest
 * @see org.xmlBlaster.util.SessionName
 */
public class SessionNameTest extends TestCase {
   protected Global glob;
   int counter = 0;

   public SessionNameTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();
   }

   public void testParse() {
      System.out.println("***SessionNameTest: testParse ...");
      try {
         SessionName sessionName = new SessionName(glob, "jack");
         //assertEquals("", "/node/unknown/client/jack", sessionName.getAbsoluteName());
         assertEquals("", "client/jack", sessionName.getAbsoluteName());
         assertEquals("", (String)null, sessionName.getNodeIdStr());
         assertEquals("", "client/jack", sessionName.getRelativeName());
         assertEquals("", "jack", sessionName.getLoginName());
         assertEquals("", 0L, sessionName.getPublicSessionId());
      }
      catch (IllegalArgumentException e) {
         fail("testParse failed: " + e.toString());
      }
         
      try {
         SessionName sessionName = new SessionName(glob, "client/jack");
         System.out.println("AbsoluteName=" + sessionName.getAbsoluteName() + " RelativeName=" + sessionName.getRelativeName());
         assertEquals("", "client/jack", sessionName.getAbsoluteName());
         //assertEquals("", "/node/unknown/client/jack", sessionName.getAbsoluteName());
         assertEquals("", (String)null, sessionName.getNodeIdStr());
         assertEquals("", "client/jack", sessionName.getRelativeName());
         assertEquals("", "jack", sessionName.getLoginName());
         assertEquals("", 0L, sessionName.getPublicSessionId());
      }
      catch (IllegalArgumentException e) {
         fail("testParse failed: " + e.toString());
      }
         
      try {
         SessionName sessionName = new SessionName(glob, "client/jack/99");
         System.out.println("AbsoluteName=" + sessionName.getAbsoluteName() + " RelativeName=" + sessionName.getRelativeName());
         assertEquals("", "client/jack/session/99", sessionName.getAbsoluteName());
         //assertEquals("", "/node/unknown/client/jack/99", sessionName.getAbsoluteName());
         assertEquals("", (String)null, sessionName.getNodeIdStr());
         assertEquals("", "client/jack/session/99", sessionName.getRelativeName());
         assertEquals("", "jack", sessionName.getLoginName());
         assertEquals("", 99L, sessionName.getPublicSessionId());
      }
      catch (IllegalArgumentException e) {
         fail("testParse failed: " + e.toString());
      }
         
      try {
         SessionName sessionName = new SessionName(glob, "/node/heron/client/jack/session/99");
         System.out.println("AbsoluteName=" + sessionName.getAbsoluteName() + " RelativeName=" + sessionName.getRelativeName());
         assertEquals("", "/node/heron/client/jack/session/99", sessionName.getAbsoluteName());
         assertEquals("", "heron", sessionName.getNodeId().getId());
         assertEquals("", "client/jack/session/99", sessionName.getRelativeName());
         assertEquals("", "jack", sessionName.getLoginName());
         assertEquals("", 99L, sessionName.getPublicSessionId());
      }
      catch (IllegalArgumentException e) {
         fail("testParse failed: " + e.toString());
      }
         
      try { // Test copy constructor ...
         SessionName tmp = new SessionName(glob, "client/jack");
         SessionName sessionName = new SessionName(glob, tmp, -4L);
         System.out.println("AbsoluteName=" + sessionName.getAbsoluteName() + " RelativeName=" + sessionName.getRelativeName());
         assertEquals("", "client/jack/session/-4", sessionName.getAbsoluteName());
         //assertEquals("", "/node/unknown/client/jack/-4", sessionName.getAbsoluteName());
         assertEquals("", (String)null, sessionName.getNodeIdStr());
         assertEquals("", "client/jack/session/-4", sessionName.getRelativeName());
         assertEquals("", "jack", sessionName.getLoginName());
         assertEquals("", -4L, sessionName.getPublicSessionId());
      }
      catch (IllegalArgumentException e) {
         fail("testParse failed: " + e.toString());
      }

      try { // Test given node ID ...
         SessionName tmp = new SessionName(glob, new NodeId("avalon"), "client/jack");
         SessionName sessionName = new SessionName(glob, tmp, -4);
         System.out.println("AbsoluteName=" + sessionName.getAbsoluteName() + " RelativeName=" + sessionName.getRelativeName());
         assertEquals("Expected /node/avalon/client/jack/session/-4 but was:" + sessionName.getAbsoluteName(), "/node/avalon/client/jack/session/-4", sessionName.getAbsoluteName());
         assertEquals("", "avalon", sessionName.getNodeId().getId());
         assertEquals("", "client/jack/session/-4", sessionName.getRelativeName());
         assertEquals("", "jack", sessionName.getLoginName());
         assertEquals("", -4L, sessionName.getPublicSessionId());
      }
      catch (IllegalArgumentException e) {
         fail("testParse failed: " + e.toString());
      }

      try { // Test given node ID ...
         SessionName tmp = new SessionName(glob, new NodeId("/node/avalon"), "client/jack");
         SessionName sessionName = new SessionName(glob, tmp, -4);
         System.out.println("AbsoluteName=" + sessionName.getAbsoluteName() + " RelativeName=" + sessionName.getRelativeName());
         assertEquals("", "/node/avalon/client/jack/session/-4", sessionName.getAbsoluteName());
         assertEquals("", "avalon", sessionName.getNodeId().getId());
         assertEquals("", "client/jack/session/-4", sessionName.getRelativeName());
         assertEquals("", "jack", sessionName.getLoginName());
         assertEquals("", -4L, sessionName.getPublicSessionId());
      }
      catch (IllegalArgumentException e) {
         fail("testParse failed: " + e.toString());
      }

      try { // Test given node ID ...
         SessionName sessionName = new SessionName(glob, new NodeId("/node/sauron/client/jack/99"), "/node/heron/client/jack/99");
         System.out.println("AbsoluteName=" + sessionName.getAbsoluteName() + " RelativeName=" + sessionName.getRelativeName());
         assertEquals("", "/node/sauron/client/jack/session/99", sessionName.getAbsoluteName());
         assertEquals("", "sauron", sessionName.getNodeId().getId());
         assertEquals("", "client/jack/session/99", sessionName.getRelativeName());
         assertEquals("", "jack", sessionName.getLoginName());
         assertEquals("", 99L, sessionName.getPublicSessionId());
      }
      catch (IllegalArgumentException e) {
         fail("testParse failed: " + e.toString());
      }
         
      try { // Test given node ID ...
         new SessionName(glob, new NodeId("/avalon"), "client/jack");
         fail("testParse failed, nodeId is invalid.");
      }
      catch (IllegalArgumentException e) {
         System.out.println("SUCCESS: " + e.toString());
      }

      try {
         new SessionName(glob, null);
         fail("testParse failed, null should throw an exception.");
      }
      catch (IllegalArgumentException e) {
         System.out.println("SUCCESS: " + e.toString());
      }
         
      try {
         new SessionName(glob, "");
         fail("testParse failed, \"\" should throw an exception.");
      }
      catch (IllegalArgumentException e) {
         System.out.println("SUCCESS: " + e.toString());
      }
         
      try {
         new SessionName(glob, "/");
         fail("testParse failed, \"/\" should throw an exception.");
      }
      catch (IllegalArgumentException e) {
         System.out.println("SUCCESS: " + e.toString());
      }
         
      try {
         new SessionName(glob, "//////");
         fail("testParse failed, \"//////\" should throw an exception.");
      }
      catch (IllegalArgumentException e) {
         System.out.println("SUCCESS: " + e.toString());
      }

      try {
         new SessionName(glob, "/node//joe/2");
         fail("testParse failed, \"/node//joe/2\" should throw an exception.");
      }
      catch (IllegalArgumentException e) {
         System.out.println("SUCCESS: " + e.toString());
      }
         
      try {
         new SessionName(glob, "/node//client/joe/2");
         fail("testParse failed, \"/node//client/joe/2\" should throw an exception.");
      }
      catch (IllegalArgumentException e) {
         System.out.println("SUCCESS: " + e.toString());
      }
         
      System.out.println("***SessionNameTest: testParse [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.SessionNameTest
    * </pre>
    */
   public static void main(String args[])
   {
      SessionNameTest testSub = new SessionNameTest("SessionNameTest");
      testSub.setUp();
      testSub.testParse();
      //testSub.tearDown();
   }
}
