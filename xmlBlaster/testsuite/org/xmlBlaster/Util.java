/*------------------------------------------------------------------------------
Name:      Util.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Some helper methods for test clients
Version:   $Id: Util.java,v 1.7 2002/05/03 10:37:49 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.jutils.init.Args;
import org.xmlBlaster.util.XmlBlasterProperty;


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
   public static String[] getOtherServerPorts(int serverPort)
   {
      String[] args = new String[8];
      args[0] = "-port";        // For all protocol we may use set an alternate server port
      args[1] = "" + serverPort;
      args[2] = "-socket.port";
      args[3] = "" + (serverPort-1);
      args[4] = "-rmi.registryPort";
      args[5] = "" + (serverPort-2);
      args[6] = "-xmlrpc.port";
      args[7] = "" + (serverPort-3);
      /*
         Vector vec = new Vector();
         vec.addElement("-port");
         vec.addElement(""+serverPort);
         return (String[])vec.toArray(new String[0]);
      */
      return args;
   }

   /**
    * Reset the server ports to default, that a client in this JVM finds the server
    */
   public static String[] getDefaultServerPorts()
   {
      String[] argsDefault = new String[8];
      argsDefault[0] = "-port";
      argsDefault[1] = "" + Global.XMLBLASTER_PORT;
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
         XmlBlasterProperty.addArgs2Props(getDefaultServerPorts());
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


