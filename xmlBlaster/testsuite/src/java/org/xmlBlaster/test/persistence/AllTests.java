package org.xmlBlaster.test.persistence;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java -Djava.compiler= org.xmlBlaster.test.persistence.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.persistence.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster persistence plugin tests");
      suite.addTest(TestPersistenceXMLDB.suite());
      suite.addTest(TestPersistence.suite());
      suite.addTest(TestPersistence2.suite());
      return suite;
   }
}
