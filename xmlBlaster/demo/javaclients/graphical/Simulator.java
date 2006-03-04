package javaclients.graphical;

import java.awt.Point;
import java.awt.Rectangle;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;


/**
 * This client connects to xmlBlaster and subscribes to a message.
 * <p />
 * We then publish the message and receive it asynchronous in the update() method.
 * <p />
 * Invoke: java Simulator
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class Simulator {

   private Point newPoint(Point oldPoint, Rectangle bound) {
      int x, y;
      if (oldPoint != null) {
         x = (int)Math.round(10.0 * Math.random()) + oldPoint.x - 5;
         y = (int)Math.round(10.0 * Math.random()) + oldPoint.y - 5;
      }
      else {
         x = (int)Math.round(bound.width * Math.random()) + bound.x;
         y = (int)Math.round(bound.height * Math.random()) + bound.y;
      }
      
      if (x > (bound.x+bound.width)) x = bound.x + bound.width;
      if (x < bound.x) x = bound.x;

      if (y > (bound.y+bound.height)) y = bound.y + bound.height;
      if (y < bound.y) y = bound.y;
      return new Point(x, y);
   }
   

   public Simulator(final Global glob) {
      String oidPrefix = glob.getProperty().get("oidPrefix", "ambulance");
      int nmax = glob.getProperty().get("nmax", 5);
      int sweeps = glob.getProperty().get("sweeps", 500);
      Point[] points = new Point[nmax];
      Rectangle bound = new Rectangle(0, 0, 1000, 500);
      for (int i=0; i < nmax; i++) points[i] = newPoint(null, bound);

      I_XmlBlasterAccess con = null;
      try {
         con = glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(glob);
         con.connect(qos, null);  // Login to xmlBlaster, register for updates

         for (int j=0; j < sweeps; j++) {
            for (int i=0; i < nmax; i++) {
               points[i] = newPoint(points[i], bound);
               String content = new String(points[i].x + ";" + points[i].y);
               con.publish(new MsgUnit(glob, "<key oid='" + oidPrefix + "." + (i+1) + "'><" + oidPrefix + "/></key>", content.getBytes(),
                                           "<qos/>"));
            }
            Thread.sleep(200L);
         }

         for (int i=0; i < nmax; i++) {
            con.erase("<key oid='" + oidPrefix + "." + (i+1) + "'/>", null);
         }
      }
      catch (Exception e) {
         System.err.println(e.getMessage());
      }
      finally {
         try {
            if (con != null) con.disconnect(null);
         }
         catch (Exception ex) {         
            System.err.println(ex.getMessage());
         }
      }
   }

   /**
    * Try
    * <pre>
    *   java Simulator -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();

      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.out.println("Example: java Simulator -session.name Jack");
         System.exit(1);
      }

      new Simulator(glob);
   }
}
