package org.xmlBlaster.test.contrib.dbwatcher;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java -Ddb.password=secret -Djava.compiler= org.xmlBlaster.test.contrib.dbwatcher.AllTests
 * java -Ddb.password=secret -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.dbwatcher.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster contrib.dbwatcher plugin tests");
      suite.addTest(new TestResultSetToXmlConverter());
      suite.addTest(new TestTimestamp());
      //suite.addTest(TestResultSetToXmlConverter.suite());
      //suite.addTest(TestTimestamp.suite());
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new TestResultSetToXmlConverter(null);
      new TestTimestamp(null);
   }
}
