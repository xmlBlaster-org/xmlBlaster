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
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.FileLocatorTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.RunLevelTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.ContextNodeTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.SessionNameTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.SocketUrlTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.XmlBlasterAccessTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.TimestampTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.TimeoutTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.ClientPropertyTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.ConnectQosTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.DisconnectQosTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.TestPoolManager.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.CommandWrapperTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.XmlBlasterExceptionTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.MicroEditionTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.StringPairTokenizerTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.Sql92SelectorTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.PropertyTest.class));
      suite.addTest(org.xmlBlaster.test.classtest.key.AllTests.suite());
      suite.addTest(org.xmlBlaster.test.classtest.qos.AllTests.suite());
      suite.addTest(org.xmlBlaster.test.classtest.queue.AllTests.suite());
      suite.addTest(org.xmlBlaster.test.classtest.msgstore.AllTests.suite());
      return suite;
   }
}
