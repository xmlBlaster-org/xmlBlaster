package org.xmlBlaster.test.classtest;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java org.xmlBlaster.test.classtest.AllTests
 * java -Djava.compiler= org.xmlBlaster.test.classtest.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.classtest.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster class tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.GlobalTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.GlobalLogTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.XmlKeySaxTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.FileIOTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.TimeoutTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.InvocationRecorderTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.PublishRetQosTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.EraseRetQosTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.SubscribeRetQosTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.PublishQosTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.ConnectQosTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.DisconnectQosTest.class));
      return suite;
   }
}
