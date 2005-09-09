package org.xmlBlaster.test.contrib.replication;

import junit.framework.Test;
import junit.framework.TestSuite;


/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java -Ddb.password=secret -Djava.compiler= org.xmlBlaster.test.contrib.replication.AllTests
 * java -Ddb.password=secret -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.replication.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster contrib.replication plugin tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.contrib.replication.TestDbBasics.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.contrib.replication.TestDbSpecific.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.contrib.replication.TestReplication.class));
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new TestDbSpecific(null);
   }
}
