package testsuite.org.xmlBlaster.snmp;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java testsuite.org.xmlBlaster.snmp.AllTests
 * java -Djava.compiler= testsuite.org.xmlBlaster.snmp.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading testsuite.org.xmlBlaster.snmp.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All snmp tests");
      suite.addTest(new TestSuite(testsuite.org.xmlBlaster.snmp.InsertTest.class));
      return suite;
   }
}
