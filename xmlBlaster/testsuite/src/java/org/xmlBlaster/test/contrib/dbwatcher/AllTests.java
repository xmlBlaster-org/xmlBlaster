package org.xmlBlaster.test.contrib.dbwatcher;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java ${LOG} -Ddb.url=${DB_URL} -Ddb.user=${DB_USER} -Ddb.password=${DB_PWD} -Dtest.sleepDelay=${TEST_SLEEP} ${APPL} ...
 * java -Ddb.password=secret -Djava.compiler= org.xmlBlaster.test.contrib.dbwatcher.AllTests
 * java -Ddb.password=secret -Ddb.url=jdbc:oracle:thin:@localhost:1521:xmlb -Ddb.user=xmlblaster -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.dbwatcher.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster contrib.dbwatcher plugin tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.contrib.dbwatcher.TestReplaceVariable.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.contrib.dbwatcher.TestResultSetToXmlConverter.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.contrib.dbwatcher.TestTimestamp.class));
      //suite.addTest(TestTimestamp.suite());
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new TestResultSetToXmlConverter(null);
      new TestTimestamp(null);
   }
}
