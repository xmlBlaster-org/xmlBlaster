package javaclients.graphical;

import java.awt.Point;
import java.awt.Rectangle;

import org.jutils.log.LogChannel;
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
      int x = (int)Math.round(10.0 * Math.random()) + oldPoint.x - 5;
      int y = (int)Math.round(10.0 * Math.random()) + oldPoint.y - 5;
      if (x > (bound.x+bound.width)) x = bound.x + bound.width;
      if (x < bound.x) x = bound.x;

      if (y > (bound.y+bound.height)) y = bound.y + bound.height;
      if (y < bound.y) y = bound.y;
      return new Point(x, y);
   }
   

   public Simulator(final Global glob) {

      int imax = 5;
      Point[] points = new Point[imax];
      for (int i=0; i < imax; i++) points[i] = new Point(100, 200);
      Rectangle bound = new Rectangle(0, 0, 1000, 1000);

      try {
         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(glob);
         con.connect(qos, null);  // Login to xmlBlaster, register for updates

         for (int j=0; j < 500; j++) {
            for (int i=0; i < imax; i++) {
               points[i] = newPoint(points[i], bound);
               String content = new String(points[i].x + ";" + points[i].y);
               con.publish(new MsgUnit(glob, "<key oid='ambulance." + (i+1) + "'/>", content.getBytes(),
                                           "<qos/>"));
            }
            Thread.sleep(200L);
         }

         for (int i=0; i < imax; i++) {
            con.erase("<key oid='ambulance." + (i+1) + "'/>", null);
         }
         con.disconnect(null);
      }
      catch (Exception e) {
         System.err.println(e.getMessage());
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
         glob.getLog(null).info("Simulator", "Example: java Simulator -session.name Jack\n");
         System.exit(1);
      }

      new Simulator(glob);
   }
}
