package clustertest;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ServerThread;

import java.util.Vector;

import junit.framework.*;

/**
 * Test publishing a message from bilbo to heron. 
 * <p />
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html
 */
public class PublishTest extends TestCase {
   private String ME = "PublishTest";
   protected Global glob;
   public static int heronPort = 7600;
   public static int avalonPort = 7601;
   public static int golanPort = 7602;
   public static int frodoPort = 7603;
   public static int bilboPort = 7604;

   private ServerThread heronThread = null;
   private ServerThread avalonThread = null;
   private ServerThread golanThread = null;
   private ServerThread frodoThread = null;
   private ServerThread bilboThread = null;

   public PublishTest(String name) {
      super(name);
      this.glob = new Global();
   }

   private void startHeron() {
      Vector vec = new Vector();
      vec.addElement("-propertyFile");
      vec.addElement("heron.properties");
      String[] args = (String[])vec.toArray(new String[0]);

      glob.init(args);
      heronThread = ServerThread.startXmlBlaster(args);
      glob.getLog().info(ME, "'heron' is ready for testing on port " + heronPort);
   }

   protected void setUp() {
      startHeron();
   }

   public void testPublish() {
      System.out.println("***PublishTest: Publish a message to a cluster slave ...");
   }
}
