/*------------------------------------------------------------------------------
Name:      Util.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Some helper methods for test clients
Version:   $Id: Util.java,v 1.9 2002/05/17 09:54:49 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.jutils.init.Args;
import org.xmlBlaster.engine.helper.Constants;

import java.util.Vector;


/**
 * Some helper methods for test clients
 */
public class Util
{
   private final static String ME = "Util";

   /**
    * If you want to start a second xmlBlaster instances
    * set environment that the ports don't conflict
    */
   public static String[] getOtherServerPorts(int serverPort) {
      Vector vec = getOtherServerPortVec(serverPort);
      return (String[])vec.toArray(new String[0]);
   }

   /**
    * If you want to start a second xmlBlaster instances
    * set environment that the ports don't conflict
    */
   public static Vector getOtherServerPortVec(int serverPort) {
      // For all protocol we may use set an alternate server port
      Vector vec = new Vector();
      vec.addElement("-port");
      vec.addElement(""+serverPort);
      vec.addElement("-socket.port");
      vec.addElement(""+(serverPort-1));
      vec.addElement("-rmi.registryPort");
      vec.addElement(""+(serverPort-2));
      vec.addElement("-xmlrpc.port");
      vec.addElement(""+(serverPort-3));
      return vec;
   }

   /**
    * Reset the server ports to default, that a client in this JVM finds the server
    */
   public static String[] getDefaultServerPorts()
   {
      String[] argsDefault = new String[8];
      argsDefault[0] = "-port";
      argsDefault[1] = "" + Constants.XMLBLASTER_PORT;
      argsDefault[2] = "-socket.port";
      argsDefault[3] = "" + org.xmlBlaster.protocol.socket.SocketDriver.DEFAULT_SERVER_PORT;
      argsDefault[4] = "-rmi.registryPort";
      argsDefault[5] = "" + org.xmlBlaster.protocol.rmi.RmiDriver.DEFAULT_REGISTRY_PORT;
      argsDefault[6] = "-xmlrpc.port";
      argsDefault[7] = "" + org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver.DEFAULT_HTTP_PORT;
      return argsDefault;
   }

   public static void resetPorts()
   {
      try {
         Global.instance().getProperty().addArgs2Props(getDefaultServerPorts());
      } catch(org.jutils.JUtilsException e) {
         Log.error(ME, e.toString());
      }
   }

   /**
    * Stop execution for some given milliseconds
    * @param millis amount of milliseconds to wait
    */
   public static void delay(long millis)
   {
      try {
          Thread.currentThread().sleep(millis);
      }
      catch( InterruptedException i)
      {}
   }


   /**
    * Stop execution until a key is hit
    * @param text This text is shown on command line
    */
   public static void ask(String text)
   {
      Log.plain(ME, "################### " + text + ": Hit a key to continue ###################");
      try {
         System.in.read();
      } catch (java.io.IOException e) {}
   }


}


