/*------------------------------------------------------------------------------
Name:      Util.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Some helper methods for test clients
------------------------------------------------------------------------------*/
package org.xmlBlaster.test;

import org.xmlBlaster.util.Global;
import org.jutils.init.Args;
import org.xmlBlaster.util.enum.Constants;

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
      vec.addElement("-client.port");
      vec.addElement(""+serverPort);
      vec.addElement("-admin.remoteconsole.port");  // -admin.remoteconsole.port 0 : switch off telnet
      vec.addElement(""+0);
      //vec.addElement(""+(serverPort-4));
      return vec;
   }

   /**
    * Reset the server ports to default, that a client in this JVM finds the server
    */
   public static String[] getDefaultServerPorts()
   {
      String[] argsDefault = {
         "-port",
         "" + Constants.XMLBLASTER_PORT,
         "-socket.port",
         "" + org.xmlBlaster.protocol.socket.SocketDriver.DEFAULT_SERVER_PORT,
         "-rmi.registryPort",
         "" + org.xmlBlaster.protocol.rmi.RmiDriver.DEFAULT_REGISTRY_PORT,
         "-xmlrpc.port",
         "" + org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver.DEFAULT_HTTP_PORT,
         "-client.port",
         "" + Constants.XMLBLASTER_PORT,
         "-admin.remoteconsole.port",
         "" + org.xmlBlaster.engine.admin.extern.TelnetGateway.TELNET_PORT
         };
      return argsDefault;
   }

   public static void resetPorts()
   {
      resetPorts(Global.instance());
   }

   public static void resetPorts(Global glob)
   {
      //try {
         glob.init(getDefaultServerPorts()); // Restes bootstrap address which this call doesn't: glob.getProperty().addArgs2Props(getDefaultServerPorts());
         glob.shutdownHttpServer();
      //} catch(org.jutils.JUtilsException e) {
      //   glob.getLog("test").error(ME, e.toString());
      //}
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
      System.out.println("################### " + text + ": Hit a key to continue ###################");
      try {
         System.in.read();
      } catch (java.io.IOException e) {}
   }


}


