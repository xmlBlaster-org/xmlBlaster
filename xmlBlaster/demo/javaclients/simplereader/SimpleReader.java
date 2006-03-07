/*------------------------------------------------------------------------------
Name:      SimpleReader.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    Wolfgang Kleinertz
------------------------------------------------------------------------------*/
package javaclients.simplereader;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.ConnectQos;



public class SimpleReader implements I_Callback  {
   private static final String ME = "SimpleReader";

   private static final String USR_LOGIN  = ME;
   private static final String USR_PASSWD = "secret";

   private              I_XmlBlasterAccess xmlBlaster = null;
   private              Global glob = null;


   public SimpleReader(I_XmlBlasterAccess _xmlBlaster, String _key) throws Exception{
      this.xmlBlaster = _xmlBlaster;
      glob = Global.instance();

      // --- entweder ---
      ConnectQos qos = new ConnectQos(glob, USR_LOGIN, USR_PASSWD);
      xmlBlaster.connect(qos, this);

      subscribe(_key);
   }

   public static void main(String[] args) {
      try {
         if (args.length!=2) {
            printUsage();
            System.exit(0);
         }
         if (!args[0].equals("-key")) {
            printUsage();
            System.exit(0);
         }

         I_XmlBlasterAccess xmlBlaster = new XmlBlasterAccess(args);
         new SimpleReader(xmlBlaster, args[1]);

         while (true) {
            try {
               Thread.sleep(10);
            }
            catch(Exception e) {
               log_error( ME, e.toString(), "");
               e.printStackTrace();
            }
         }
      }
      catch(Exception ex) {
         log_error( ME, ex.toString(), "");
         ex.printStackTrace();
      }

   }

   private static void printUsage() {
      System.out.println("java javaclients.SimpleReader -key <key> ...");
   }

   public String update(String loginName, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException
   {
      System.out.println("Key: "+updateKey.toXml()+" >>> Content: "+new String(content)+" >>> ---");
      return ("Key: "+updateKey.toXml()+" >>> Content: "+new String(content)+" >>> ---");
   }

   private void subscribe(String _key) {
      try {
         SubscribeKey key = new SubscribeKey(glob, _key, "XPATH");
         SubscribeQos qos = new SubscribeQos(glob);
         xmlBlaster.subscribe(key.toXml(), qos.toXml());
      }
      catch( Exception ex ) {
         System.err.println("error-error-error-error >>>"+ex.toString());
      }
   }

   public static void log_error(String ME, String text1, String text2) {
      System.err.println("ME:" + ME + "text:" + text1 +  text2);
   }


} // -- class

// --file
