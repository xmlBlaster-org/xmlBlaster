package org.xmlBlaster.test.classtest.qos;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java org.xmlBlaster.test.classtest.qos.AllTests
 * java -Djava.compiler= org.xmlBlaster.test.classtest.qos.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.classtest.qos.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster QoS tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.qos.MsgQosTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.qos.MsgQosFactoryTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.qos.AddressBaseTest.class));
      suite.addTest(StatusQosFactoryTest.suite());
      suite.addTest(QueryQosFactoryTest.suite());
      return suite;
   }
}
