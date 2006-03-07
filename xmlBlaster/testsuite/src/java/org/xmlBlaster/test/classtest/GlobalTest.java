package org.xmlBlaster.test.classtest;

import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.Global;

import junit.framework.*;

/**
 * Test util Global. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * java -Dproperty.verbose=1 -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.GlobalTest
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * @see org.xmlBlaster.util.Global
 */
public class GlobalTest extends TestCase {
   protected Global glob;

   public GlobalTest(String name) {
      super(name);
   }

   protected void setUp() {
      System.out.println("***GlobalTest: setup ...");
      String[] args = new String[2];
      args[0] = "-test.xy";
      args[1] = "true";
      Global tmp = Global.instance();
      tmp.init(args);
      assertEquals("Argument not set", true, tmp.getProperty().get("test.xy", false));
   }

   public void testInstanceAccess() {
      System.out.println("***GlobalTest: testInstanceAccess ...");
      Global glob = Global.instance();
      assertEquals("Argument is lost", true, glob.getProperty().get("test.xy", false));
      System.out.println("***GlobalTest: testInstanceAccess [SUCCESS]");
   }

   public void testClone() {
      System.out.println("***GlobalTest: testClone ...");
      
      Global old = Global.instance();
      assertEquals("Argument not set", true, old.getProperty().get("test.xy", false));

      String[] args = new String[2];
      args[0] = "-test.clone";
      args[1] = "true";
      Global glob = old.getClone(args);

      assertEquals("Argument not set anymore", true, old.getProperty().get("test.xy", false));
      assertEquals("Second argument should not be in original instance", false, old.getProperty().get("test.clone", false));

      assertEquals("First argument is lost", true, glob.getProperty().get("test.xy", false));
      assertEquals("Second argument is lost", true, glob.getProperty().get("test.clone", false));
      
      System.out.println("***GlobalTest: testClone [SUCCESS]");
   }

   public void testPropertyFile() {
      System.out.println("***GlobalTest: testPropertyFile ...");
      
      try {
         FileLocator.writeFile(System.getProperty("user.home"), "_tmp.properties", "oo=aa\ncluster.node.id=bilbo".getBytes());
         String path = FileLocator.concatPath(System.getProperty("user.home"), "_tmp.properties");
         String[] args = { "-propertyFile", path };
         System.err.println("***GlobalTest: testPropertyFile -propertyFile " + path);
         Global.instance().init(args);
         assertEquals("Argument not set", path, Global.instance().getProperty().get("propertyFile", (String)null));
         assertEquals("Argument not set", "aa", Global.instance().getProperty().get("oo", (String)null));
         assertEquals("Argument not set", "bilbo", Global.instance().getProperty().get("cluster.node.id", (String)null));
         assertEquals("Invalid cluster node id", "bilbo", Global.instance().getId());

         org.xmlBlaster.engine.ServerScope eGlobal = new org.xmlBlaster.engine.ServerScope(Global.instance().getProperty().getProperties(), false);
         assertEquals("Argument not set after creating engine.Global", true, eGlobal.getProperty().get("test.xy", false));
         assertEquals("Argument not set after creating engine.Global", "aa", eGlobal.getProperty().get("oo", (String)null));
         assertEquals("Argument not set", "bilbo", eGlobal.getProperty().get("cluster.node.id", (String)null));

         Global bilboGlob = Global.instance().getClone(null);
         assertEquals("Argument not set", "bilbo", eGlobal.getProperty().get("cluster.node.id", (String)null));
         assertEquals("Invalid cluster node id", "bilbo", bilboGlob.getId());
      }
      catch(Exception e) {
         fail("property file check failed: " + e.toString());
      }
      
      System.out.println("***GlobalTest: testPropertyFile [SUCCESS]");
   }

   public void testUtilToEngine() {
      System.out.println("***GlobalTest: testUtilToEngine ...");
      
      assertEquals("Argument not set", true, Global.instance().getProperty().get("test.xy", false));
      org.xmlBlaster.engine.ServerScope eGlobal = new org.xmlBlaster.engine.ServerScope(Global.instance().getProperty().getProperties(), false);
      assertEquals("Argument not set after creating engine.Global", true, eGlobal.getProperty().get("test.xy", false));
      
      System.out.println("***GlobalTest: testUtilToEngine [SUCCESS]");
   }
}

