package testsuite.org.xmlBlaster.jdbc;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java clustertest.AllTests
 * java -Djava.compiler= clustertest.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading clustertest.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster JDBC plugin tests");
      suite.addTest(TestJdbcAccess.suite());
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new TestJdbcAccess(null,null,null); 
   }
}
