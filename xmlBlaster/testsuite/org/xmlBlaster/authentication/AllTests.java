package authentication;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java authentication.AllTests
 * java -Djava.compiler= authentication.AllTests
 * java -Djava.compiler= junit.ui.TestRunner -noloading authentication.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster authentication tests");
      suite.addTest(new TestSuite(authentication.TestAuthenticationHtPassWd.class));
      suite.addTest(TestSession.suite());
      suite.addTest(TestSessionCb.suite());
      suite.addTest(TestLogin.suite());
      suite.addTest(TestLoginLogoutEvent.suite());
      suite.addTest(new TestSuite(authentication.TestLogout.class));
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new TestAuthenticationHtPassWd(null); 
   }
}

