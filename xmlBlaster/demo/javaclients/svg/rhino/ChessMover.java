/*------------------------------------------------------------------------------
Name:      ChessMover.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The demo class which moves around the chess peaces
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients.svg.rhino;

import org.xmlBlaster.util.XmlBlasterException;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.MsgUnit;
import java.util.Random;


/* This is a simple demo client which moves around the chess pieces used in
   chessRhino.svg. It has been tested with batik 1.5 but it should even work
   with Mozilla.
*/
public class ChessMover
{
   private static final String ME = "ChessMover";
   protected I_XmlBlasterAccess connection = null;
   private Random random = null;
   private long sleepTime = 0L;

   public ChessMover (String[] args) throws XmlBlasterException
   {
      Global glob = new Global(args);
      sleepTime = Long.parseLong(args[1]);
      random = new Random(100L);
      this.connection = glob.getXmlBlasterAccess();
      ConnectQos connectQos = new ConnectQos(glob, "chessMover", "secret");
      this.connection.connect(connectQos, null);
   }


   public void publish () throws XmlBlasterException
   {
      int id = random.nextInt(32);
      int x = random.nextInt(450);
      int y = random.nextInt(450);

      String key = "<?xml version='1.0'?><key oid='" + id + "'><chess>some chess name</chess></key>";
      String qos = "<qos></qos>";
      String transform = "translate(" + x + "," + y + ")";
      String content = "<chess><id>" + id + "A</id><transform>" + transform + "</transform></chess>";
      MsgUnit msg = new MsgUnit(key, content.getBytes(), qos);
      this.connection.publish(msg);
      try {
         Thread.sleep(this.sleepTime);
      }
      catch (Exception ex) {}
   }


   public static void main (String[] args) {
      try {

         ChessMover mover = new ChessMover(args);
         while (true) {
            mover.publish();
         }
      }
      catch (Exception ex) {
         System.err.println(ex.toString());

            System.err.println("usage: java javaclients.svg.rhino.ChessMover -interval updateInterval");
            System.err.println("where updateInterval is the time in ms between each move");
            System.exit(1);

      }
   }

}


