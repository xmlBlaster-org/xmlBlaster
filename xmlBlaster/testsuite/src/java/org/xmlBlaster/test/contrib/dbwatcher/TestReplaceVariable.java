/*------------------------------------------------------------------------------
Name:      TestReplaceVariable.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.dbwatcher;

import java.util.HashMap;
import java.util.Map;

import org.xmlBlaster.contrib.dbwatcher.DbWatcher;

import junit.framework.TestCase;

/**
 * @author Marcel Ruff
 */
public class TestReplaceVariable extends TestCase {

   /**
    * java org.xmlBlaster.test.contrib.dbwatcher.TestReplaceVariable
    */
   public static void main(String[] args) {
      try {
         TestReplaceVariable t = new TestReplaceVariable();
         t.testReplaceVariable();
      }
      catch (Throwable e) {
         System.err.println(e.toString());
      }
   }

   /*
   * @see TestCase#setUp()
   */
   protected void setUp() throws Exception {
      super.setUp();
   }

   /*
   * @see TestCase#tearDown()
   */
   protected void tearDown() throws Exception {
      super.tearDown();
   }
    
   public void testReplaceVariable() throws Exception {
      {
         String stmtTempl = "db.test_poll.event.${groupColValue}";
         String stmt = DbWatcher.replaceVariable(stmtTempl, "");
         System.out.println("Transformed to '" + stmt + "'");
         assertEquals("db.test_poll.event.", stmt);
      }
      {
         String stmtTempl = "select type_of_connection from nvl_${SUBNET_ID}li_gw_ent where blst_vers='${BUNDLE_VERS}' AND circuit='${CIRCUIT}' AND channel='${CHANNEL}'";
         String stmt = DbWatcher.replaceVariable(stmtTempl, "Bla");
         System.out.println("Transformed to '" + stmt + "'");
         assertEquals("select type_of_connection from nvl_Blali_gw_ent where blst_vers='Bla' AND circuit='Bla' AND channel='Bla'", stmt);
      }
      {
         String stmtTempl = "select type_of_connection from nvl_${SUBNET_ID}li_gw_ent where blst_vers='${BUNDLE_VERS}' AND circuit='${CIRCUIT}' AND channel='${CHANNEL}'";
         String stmt = DbWatcher.replaceVariable(stmtTempl, "");
         System.out.println("Transformed to '" + stmt + "'");
         assertEquals("select type_of_connection from nvl_li_gw_ent where blst_vers='' AND circuit='' AND channel=''", stmt);
      }
      {
         String stmtTempl = "select type_of_connection from nvl_${SUBNET_ID}li_gw_ent where blst_vers='${BUNDLE_VERS}' AND circuit='${CIRCUIT}' AND channel='${CHANNEL}'";
         String stmt = DbWatcher.replaceVariable(stmtTempl, null);
         System.out.println("Transformed to '" + stmt + "'");
         assertEquals(stmtTempl, stmt);
      }
   }

   /**
   * Constructor for TestReplaceVariable.
   * @param arg0
   */
   public TestReplaceVariable(String arg0) {
      super(arg0);
   }

   public TestReplaceVariable() {
   }
}
