package org.xmlBlaster.test.authentication;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java org.xmlBlaster.test.authentication.AllTests
 * java -Djava.compiler= org.xmlBlaster.test.authentication.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.authentication.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster authentication tests");
      suite.addTest(TestSession.suite());
      suite.addTest(TestSessionCb.suite());
      suite.addTest(TestLogin.suite());
      suite.addTest(TestLoginLogoutEvent.suite());
      suite.addTest(new TestSuite(org.xmlBlaster.test.authentication.TestAuthenticationHtPassWd.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.authentication.TestLogout.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.authentication.TestSessionReconnect.class));
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new TestAuthenticationHtPassWd(null); 
   }
}

