package classtest;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java classtest.AllTests
 * java -Djava.compiler= classtest.AllTests
 * java -Djava.compiler= junit.ui.TestRunner -noloading classtest.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster class tests");
      suite.addTest(new TestSuite(classtest.GlobalTest.class));
      suite.addTest(new TestSuite(classtest.GlobalLogTest.class));
      suite.addTest(new TestSuite(classtest.XmlKeySaxTest.class));
      suite.addTest(new TestSuite(classtest.FileIOTest.class));
      return suite;
   }
}
