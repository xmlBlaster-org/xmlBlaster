package org.xmlBlaster.test.classloader;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests.
 * <pre>
 * java -Djava.compiler= org.xmlBlaster.test.classloader.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.classloader.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster classloader tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.classloader.XmlBlasterClassloaderTest.class));
      return suite;
   }
}
