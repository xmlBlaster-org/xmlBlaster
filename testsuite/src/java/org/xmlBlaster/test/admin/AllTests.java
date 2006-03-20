/*------------------------------------------------------------------------------
Name:      AllTests.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.admin;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * This test client starts all known tests in directory xmlBlaster/testsuite/src/java/org/xmlBlaster/admin
 * these are remote tests between clients and a running xmlBlaster. 
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.AllTests
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.AllTests
 * </pre>
 */
public class AllTests
{
   public static Test suite() {
      TestSuite suite= new TestSuite("All xmlBlaster admin tests");
      suite.addTest(new TestSuite(TestAdminGet.class));
     return suite;
   }
}
