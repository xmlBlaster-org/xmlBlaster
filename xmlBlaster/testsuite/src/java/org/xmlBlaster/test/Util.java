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
    * @return A cloned Global which is configured with different serverPort
    */
   public static Global getOtherServerPorts(Global orig, int serverPort) {
      return orig.getClone(getOtherServerPorts(serverPort));
   }

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
      vec.addElement("-bootstrapPort");
      vec.addElement(""+serverPort);
      vec.addElement("-plugin/socket/port");
      vec.addElement(""+(serverPort-1));
      vec.addElement("-plugin/rmi/registryPort");
      vec.addElement(""+(serverPort-2));
      vec.addElement("-plugin/xmlrpc/port");
      vec.addElement(""+(serverPort-3));
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
         "-bootstrapPort",
         "" + Constants.XMLBLASTER_PORT,
         "-dispatch/connection/plugin/socket/port",
         "" + org.xmlBlaster.protocol.socket.ExecutorBase.DEFAULT_SERVER_PORT,
         "-dispatch/connection/plugin/rmi/registryPort",
         "" + org.xmlBlaster.protocol.rmi.RmiDriver.DEFAULT_REGISTRY_PORT,
         "-dispatch/connection/plugin/xmlrpc/port",
         "" + org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver.DEFAULT_HTTP_PORT,
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


   /**
    * Do some garbage collect attempts
    */
   public static void gc(int numGc) {
      for (int ii=0; ii<numGc; ii++) {
         System.gc();
         try { Thread.currentThread().sleep(100L); } catch( InterruptedException i) {}
      }
   }
}


