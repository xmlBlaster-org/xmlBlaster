/*------------------------------------------------------------------------------
Name:      MassiveSubTestOne.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
Version:   $Id: MassiveSubTestOne.java,v 1.1 2002/09/27 12:23:51 antman Exp $
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
client.protocol=IOR
</pre>
 *
 *
 * @author Peter Antman
 * @version $Revision: 1.1 $ $Date: 2002/09/27 12:23:51 $
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
   
}
