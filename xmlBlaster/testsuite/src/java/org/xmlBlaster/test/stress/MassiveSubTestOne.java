/*------------------------------------------------------------------------------
Name:      MassiveSubTestOne.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.stress;
import org.xmlBlaster.util.Global;
import junit.framework.*;

/**
 * Run massive with with only the one connection aproach read settings from env.
 * <p>Here is one possible setting:</p>
 * <pre>
numSubscribers=2500
maxSubPerCon=500
withEmbedded=false
noToPub=4
protocol=IOR
</pre>
 *
 *
 * @author Peter Antman
 * @version $Revision: 1.2 $ $Date$
 */

public class MassiveSubTestOne extends MassiveSubTest {
   

   public MassiveSubTestOne(Global glob, String testName, String loginName, boolean useOneConnection) {
      super(glob,testName,loginName,useOneConnection);
   }
   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      String loginName = "Tim";
      suite.addTest(new MassiveSubTestOne(new Global(), "testManyClients", loginName,true));
      
      return suite;
   }
   /**
    * An example of how to run it:
    java  -Xms18M -Xmx256M -classpath lib/junit.jar:lib/testsuite.jar:lib/xmlBlaster.jar -Dtrace=false org.xmlBlaster.test.stress.MassiveSubTestOne -numSubscribers 2500 -maxSubPerCon 500 -protocol LOCAL -withEmbedded true > log 2>&1
    * tail -f log | egrep 'Threads created|messages updated'
    */
   public static void main(String[] args) {
      Global glob = new Global(args);
      MassiveSubTestOne m = new MassiveSubTestOne(glob, "testManyClients", "testManyClients", true);
      m.setUp();
      m.testManyClients();
      m.tearDown();
   }
}
