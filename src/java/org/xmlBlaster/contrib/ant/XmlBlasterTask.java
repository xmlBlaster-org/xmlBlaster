package org.xmlBlaster.contrib.ant;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.xmlBlaster.client.script.I_MsgUnitCb;
import org.xmlBlaster.client.script.XmlScriptClient;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
/**
 * Access xmlBlaster from within ant. 
 * <p>
 * Example:
 * <pre><code>
&lt;project name="xmlBlaster" default="publish" basedir=".">

  &lt;taskdef name="xmlBlaster"
           classname="org.xmlBlaster.contrib.ant.XmlBlasterTask"
           classpath="build.tmp/classes:lib/xmlBlaster.jar"/>

  &lt;target name="publish">
    &lt;xmlBlaster>
        &lt;![CDATA[
         &lt;xmlBlaster>
           &lt;connect/>
           &lt;publish>&lt;key oid="1">&lt;airport />&lt;/key>&lt;content>Hallo from ANT>&lt;/content>&lt;/publish>
           &lt;subscribe>&lt;key queryType="XPATH">//airport&lt;/key>&lt;qos/>&lt;/subscribe>
           &lt;input message="Press key"/>
           &lt;publish>&lt;key oid="2">&lt;airport />&lt;/key>&lt;content>Hi again&lt;/content>&lt;/publish>
           &lt;wait delay="2000" />
           &lt;erase>&lt;key oid="1">&lt;/key>&lt;qos>&lt;force/>&lt;/qos>&lt;/erase>
           &lt;erase>&lt;key oid="2">&lt;/key>&lt;qos/>&lt;/erase>
           &lt;wait delay="500" />
           &lt;disconnect />
         &lt;/xmlBlaster>
        ]]&gt;
    &lt;/xmlBlaster>
  &lt;/target>
&lt;/project>
 * </code></pre>
 * @author Marcel Ruff
 * @see http://ant.apache.org/manual/index.html
 */
public class XmlBlasterTask extends Task {

    private int verbose;
    private Global glob;
    private String xmlBlasterScript;
    private String scriptFile;
    private String outFile;
    private String updateFile;
    private Reader reader;
    private OutputStream outStream;
    private OutputStream updStream;
    private XmlScriptClient interpreter;
    private boolean prepareForPublish;

    public void execute() throws BuildException {
       if (verbose > 0)  System.out.println("execute() called");
       
       String message = getProject().getProperty("ant.project.name");
       log("Here is project '" + message + "' used in: " +  getLocation());
       
       this.glob = Global.instance();
       
       try {
          if (this.scriptFile != null) {
             this.reader = new FileReader(this.scriptFile);
          }
          else if (this.xmlBlasterScript != null && this.xmlBlasterScript.trim().length() > 0) {
             this.reader = new StringReader(this.xmlBlasterScript);
          }
          else {
             throw new BuildException("Please provide a script");
          }
          
          if (this.outFile == null)
             this.outStream = System.out;
          else {
             this.outStream = new FileOutputStream(outFile);
          }
          
          if (this.updateFile == null)
             this.updStream = this.outStream;
          else {
             this.updStream = new FileOutputStream(updateFile);
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
          log("Scripting to xmlBlaster failed " + getLocation() + ": " + e.getMessage());
          throw new BuildException(e.getMessage());
       }
       catch (Throwable e) {
          log("Scripting to xmlBlaster failed " + getLocation() + ": " + e.toString());
          e.printStackTrace();
          throw new BuildException(e.toString());
       }
    }

    public void setVerbose(int level) {
       this.verbose = level;
    }

    /**
     * Set the task body text, we expect a valid xmlBlaster xml script. 
     * @param text
     */
    public void addText(String text) {
       this.xmlBlasterScript = text;
       log("Your script to execute: " + text);
    }

   /**
    * @param outFile The outFile to set.
    */
   public void setOutFile(String outFile) {
      if (outFile == null || outFile.trim().length() < 1)
         this.outFile = null;
      else
         this.outFile = outFile;
   }

   /**
    * @param scriptFile The scriptFile to set.
    */
   public void setScriptFile(String scriptFile) {
      if (scriptFile == null || scriptFile.trim().length() < 1)
         this.scriptFile = null;
      else
         this.scriptFile = scriptFile;
   }

   /**
    * @param updateFile The updateFile to set.
    */
   public void setUpdateFile(String updateFile) {
      if (updateFile == null || updateFile.trim().length() < 1)
         this.updateFile = null;
      else
         this.updateFile = updateFile;
   }

   /**
    * @param prepareForPublish The prepareForPublish to set.
    */
   public void setPrepareForPublish(boolean prepareForPublish) {
      this.prepareForPublish = prepareForPublish;
   }

   public static void main(String[] args) {
   }
}
