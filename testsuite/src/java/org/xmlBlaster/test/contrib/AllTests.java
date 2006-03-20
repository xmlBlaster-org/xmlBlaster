package org.xmlBlaster.test.contrib;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java -Ddb.password=secret -Djava.compiler= org.xmlBlaster.test.contrib.AllTests
 * java -Ddb.password=secret -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster contrib plugin tests");
      suite.addTest(org.xmlBlaster.test.contrib.db.AllTests.suite());
      suite.addTest(org.xmlBlaster.test.contrib.dbwatcher.AllTests.suite());
      suite.addTest(org.xmlBlaster.test.contrib.dbwriter.AllTests.suite());
      suite.addTest(org.xmlBlaster.test.contrib.replication.AllTests.suite());
      return suite;
   }
}
