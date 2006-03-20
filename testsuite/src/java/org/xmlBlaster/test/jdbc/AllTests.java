package org.xmlBlaster.test.jdbc;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java -Djava.compiler= org.xmlBlaster.test.jdbc.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.jdbc.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite() {
      TestSuite suite= new TestSuite("All xmlBlaster JDBC plugin tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.jdbc.TestJdbcAccess.class));
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new TestJdbcAccess("TestJdbcAccess"); 
   }
}
