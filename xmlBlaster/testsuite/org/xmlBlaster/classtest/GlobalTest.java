package classtest;

import org.xmlBlaster.util.Global;

import junit.framework.*;

/**
 * Test util Global. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
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
      Global tmp = new Global(args);
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
}

