/*------------------------------------------------------------------------------
Name:      XmlScript.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: XmlScript.java,v 1.3 2004/04/26 10:23:15 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients.script;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.script.XmlScriptInterpreter;


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
 * </pre>
 */
public class XmlScript {
   private static String ME = "XmlScript";
   private final Global glob;
   private final LogChannel log;
   private XmlScriptInterpreter interpreter;
   private Reader reader;
   private OutputStream outStream;
   private OutputStream updStream;

   public XmlScript(Global glob, String inFile, String outFile, String updFile) {
      this.glob = glob;
      this.log = glob.getLog("demo");
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
         this.interpreter = new XmlScriptInterpreter(this.glob, this.glob.getXmlBlasterAccess(), this.outStream, this.updStream, null);
         this.interpreter.parse(this.reader);
      }
      catch (Exception e) {
         log.error(ME, "Client failed: " + e.toString());
         // e.printStackTrace();
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
         System.exit(1);
      }
      
      String inFile = glob.getProperty().get("requestFile", (String)null);
      String outFile = glob.getProperty().get("responseFile", (String)null);
      String updFile = glob.getProperty().get("updateFile", (String)null);
      
      new XmlScript(glob, inFile, outFile, updFile);
   }
} // XmlScript

