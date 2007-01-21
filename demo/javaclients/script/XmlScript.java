/*------------------------------------------------------------------------------
Name:      XmlScript.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package javaclients.script;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.script.XmlScriptClient;
import org.xmlBlaster.client.script.I_MsgUnitCb;
import org.xmlBlaster.util.MsgUnit;


/**
 * This demo shows how an xml file (or stream) can perform invocations on an XmlBlaster
 * access. Everything you can do with xmlBlaster can be done invoked in a script.
 * <p>
 * Invocation examples:<br />
 * <pre>
 *    java -cp ../../../lib/xmlBlaster.jar javaclients.script.XmlScript
 *
 *    java javaclients.script.XmlScript -requestFile inFile.xml -responseFile outFile.xml -updateFile updFile.xml
 *
 *    java javaclients.script.XmlScript -help
 *
 *    java javaclients.script.XmlScript -prepareForPublish true -requestFile 2004-10-23_20_44_43_579.xml
 * </pre>
 * <p>
 * The setting <tt>-prepareForPublish true</tt> strips away routing informations if you want to publish
 * a dumped dead message given by -requestFile.</p>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.script.html">The client.script requirement</a>
 */
public class XmlScript {
   private final Global glob;
   private static Logger log = Logger.getLogger(XmlScript.class.getName());
   private XmlScriptClient interpreter;
   private Reader reader;
   private OutputStream outStream;
   private OutputStream updStream;
   private boolean prepareForPublish;
   
   public final class OutputStreamWithDelay extends OutputStream {
    
      private OutputStream out;
      private long msgDelay;
      private long bytesPerSecond;
      private double kappa;
      
      public OutputStreamWithDelay(OutputStream out, long msgDelay, long bytesPerSecond) {
         this.out = out;
         this.msgDelay = msgDelay;
         this.bytesPerSecond = bytesPerSecond;
         if (this.bytesPerSecond > 0L)
            this.kappa = 1000.0 / this.bytesPerSecond;
         
      }

      private final void waitForRate(int len) {
         if (bytesPerSecond < 0L)
            return;
         long val = (long)(kappa*len);
         if (val > 0L) {
            try {
               Thread.sleep(val);
            }
            catch (InterruptedException ex) {
               ex.printStackTrace();
            }
         }
      }
      
      public void write(byte[] b, int off, int len) throws IOException {
         waitForRate(len);
         this.out.write(b, off, len);
      }

      public void write(byte[] b) throws IOException {
         if (this.msgDelay > 0L) {
            try {
               Thread.sleep(this.msgDelay);
            }
            catch (InterruptedException ex) {
               ex.printStackTrace();
            }
         }
         this.write(b, 0, b.length);
      }

      public void write(int b) throws IOException {
         this.out.write(b);
      }
   }

   public XmlScript(Global glob, String inFile, String outFile, String updFile, long msgDelay, long rateBytesPerSecond) {
      this.glob = glob;
      this.prepareForPublish = glob.getProperty().get("prepareForPublish", this.prepareForPublish);

      try {
         if (inFile == null) this.reader = new InputStreamReader(System.in);
         else {
            this.reader = new FileReader(inFile);
         }
         if (outFile == null) this.outStream = System.out;
         else {
            this.outStream = new FileOutputStream(outFile);
         }
         if (updFile == null) this.updStream = this.outStream;
         else {
            this.updStream = new FileOutputStream(updFile);
         }
         if (msgDelay > 0L || rateBytesPerSecond > 0L) {
            this.updStream = new OutputStreamWithDelay(this.updStream, msgDelay, rateBytesPerSecond);
         }
         
         this.interpreter = new XmlScriptClient(this.glob, this.glob.getXmlBlasterAccess(), this.outStream, this.updStream, null);

         if (this.prepareForPublish) {
            this.interpreter.registerMsgUnitCb(new I_MsgUnitCb() {
               public boolean intercept(MsgUnit msgUnit) {
                  msgUnit.getQosData().clearRoutes();
                  return true;
               }
            });
         }
         this.interpreter.parse(this.reader);
      }
      catch (XmlBlasterException e) {
         log.severe("Scripting failed: " + e.getMessage());
      }
      catch (Throwable e) {
         log.severe("Scripting to xmlBlaster failed: " + e.toString());
         e.printStackTrace();
      }
   }

   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(glob.usage());
         System.out.println("Get help: java javaclients.script.XmlScript -help\n");
         System.out.println("Example: java javaclients.XmlScript -requestFile inFile.xml -responseFile outFile.xml -updateFile updFile.xml\n");
         System.out.println("  if you don't specify anything as '-requestFile', then the standard input stream is taken.\n");
         System.out.println("  if you don't specify anything as '-responseFile', then the standard output stream is taken.\n");
         System.out.println("  if you don't specify anything as '-updateFile', then the same stream as for the output stream is used.\n");
         System.out.println("  if you don't specify anything as '-msgDelay', it will not wait after each update, otherwise it will wait so many ms as specified.\n");
         System.out.println("  if you don't specify anything as '-bytesPerSecond', it will not wait after each read, otherwise it will try to get the rate specified.\n");
         System.out.println("  -prepareForPublish true  If you want to publish a dumped dead message given by -requestFile.\n");
         System.out.println("  if you don't specify anything as '-numRuns', it will execute the script one time, otherwise it will repeat execution the given times.\n");
         System.exit(1);
      }
      
      String inFile = glob.getProperty().get("requestFile", (String)null);
      String outFile = glob.getProperty().get("responseFile", (String)null);
      String updFile = glob.getProperty().get("updateFile", (String)null);
      int numRuns = glob.getProperty().get("numRuns", 1);
      long msgDelay = glob.getProperty().get("msgDelay", 0L);
      long bytesPerSecond = glob.getProperty().get("bytesPerSecond", 0L);
      
      if (numRuns < 0) numRuns = Integer.MAX_VALUE; // forever
      for (int i=0; i<numRuns; i++)
    	  new XmlScript(glob, inFile, outFile, updFile, msgDelay, bytesPerSecond);
   }
} // XmlScript

