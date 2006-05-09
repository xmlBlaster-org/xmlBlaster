package org.xmlBlaster.contrib.ant;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Hashtable;
import java.util.Properties;

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
 * The usage is simple, here are the steps to follow.<br />
 * Edit your <code>build.xml</code> file and register the xmlBlaster task:
 * <p>
 * <pre>
 *&lt;project name="xmlBlaster" default="publish" basedir=".">
 *
 *  &lt;taskdef name="xmlBlasterScript"
 *          classname="org.xmlBlaster.contrib.ant.XmlBlasterTask"
 *          classpath="lib/xmlBlaster.jar"/>
 *
 *  ...
 *
 *&lt;/project>
 * </pre>
 * The only tricky part is to choose the classpath setting, it
 * must include at least the xmlBlaster client library and this class itself.
 * <p>
 * <p>
 * 
 * Example task using files:
 * <pre>
 *&lt;target name="usingFiles">
 *   &lt;xmlBlasterScript
 *       scriptFile="myXblScript.xml"
 *       responseFile="methodReturn.xml"
 *       updateFile="asyncResponses.xml"/>
 *&lt;/target>
 * </pre>
 * You have to provide the <code>myXblScript.xml</code> file content, its format is defined
 * in the requirement <code>client.script</code>.<br />
 * The other two files are generated during execution.
 * <p/>
 * Example task with embedded script and output to console.<br />
 * We set a property to run without database on client side,
 * then we connect and subscribe to topic <code>Hello</code>.
 * After you press a key we publish such a message and receive it asynchronously:
 * <pre>
 *  &lt;target name="publish">
 *    &lt;xmlBlasterScript>
 *      &lt;!-- This is the script executed -->
 *      &lt;![CDATA[
 *        &lt;xmlBlaster>
 *          &lt;property name="queue/connection/defaultPlugin">RAM,1.0&lt;/property>
 *          &lt;connect/>
 *          &lt;subscribe>&lt;key oid="Hello"/>&lt;qos/>&lt;/subscribe>
 *          &lt;input message="Subscribed to 'hello', press a key to publish"/>
 *          &lt;publish>&lt;key oid="Hello">&lt;/key>&lt;content>Hallo from ANT>&lt;/content>&lt;/publish>
 *          &lt;wait delay="2000" />
 *          &lt;erase>&lt;key oid="Hello"/>&lt;qos/>&lt;/erase>
 *          &lt;wait delay="500" />
 *          &lt;disconnect />
 *        &lt;/xmlBlaster>
 *      ]]&gt 
 *   &lt;/xmlBlasterScript>
 * &lt;/target>
 * </pre>
 * <p>
 * In the following example we set verbosity to 3 (max), use the
 * given xmlBlaster.properties,
 * configure the JDK 1.4 logging for the xmlBlaster client library
 * and inherit all properties from ant to xmlBlaster-Global scope: 
 * <pre>
 *&lt;target name="usingFiles">
 *   &lt;xmlBlasterScript
 *       scriptFile="myXblScript.xml"
 *       responseFile="methodReturn.xml"
 *       updateFile="asyncResponses.xml"
 *       verbose="3"
 *       propertyFile="/tmp/xmlBlaster.properties"
 *       loggingFile="logging.properties"
 *       inheritAll="true"
 *       />
 *&lt;/target>
 * </pre>
 * <p>Note that the ant properties are weakest, followed by xmlBlaster.properties settings
 * and the &lt;property> tags in the script are strongest.
 * </p>
 * <h3>Classloader problem:</h3>
 * <p>Loading classes like the JDBC driver (hsqldb.jar)
 * or loading of customized JDK1.4 logging classes from logging.properties fails.<br />
 * The reason is that ant uses an own Classloader and the failing classes
 * to load seem to be loaded by the system Classloader (<code>ClassLoader.getSystemClassLoader().loadClass(word);</code>),
 * but this doesn't know our classes to load!<br />
 * The workaround is to set LOCALCLASSPATH before startig ant, for example:<br />
 * <code> export LOCALCLASSPATH=$CLASSPATH:$HOME/apache-ant-1.6.5/lib/ant.jar</code><br />
 * which resolves the issue (assuming that your CLASSPATH contains all needed classes already).
 * </p>
 * 
 * @author Marcel Ruff
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.script.html">http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.script.html</a>
 * @see <a href="http://ant.apache.org/manual/index.html">http://ant.apache.org/manual/index.html</a>
 */
public class XmlBlasterTask extends Task {

    private int verbose=1;
    private Global glob;
    private String xmlBlasterScript;
    private String scriptFile;
    private String responseFile;
    private String updateFile;
    private String propertyFile;
    private String loggingFile;
    private Reader reader;
    private boolean inheritAll;
    private OutputStream responseStream;
    private OutputStream updateStream;
    private XmlScriptClient interpreter;
    private boolean prepareForPublish;

    public void execute() throws BuildException {
       if (verbose > 1)  log("execute() called");
       
       String message = getProject().getProperty("ant.project.name");
       if (verbose > 0) log("Here is project '" + message + "' used in: " +  getLocation());
       
       this.glob = Global.instance();
       
       Hashtable antProperties = getProject().getProperties();
       Hashtable props = (inheritAll) ? antProperties : new Properties();
       
       if (this.propertyFile != null)
          props.put("propertyFile", this.propertyFile);

       if (this.loggingFile != null)
          props.put("java.util.logging.config.file", this.loggingFile);
       
       // Nested tags (antcall): <param name="param1" value="value"/>
       // (ant): <property name="param1" value="version 1.x"/>
       
       if (props.size() > 0)
          this.glob.init(props);
       
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
          
          if (this.responseFile == null)
             this.responseStream = System.out;
          else {
             this.responseStream = new FileOutputStream(this.responseFile);
          }
          
          if (this.updateFile == null)
             this.updateStream = this.responseStream;
          else {
             this.updateStream = new FileOutputStream(this.updateFile);
          }
          this.interpreter = new XmlScriptClient(this.glob, this.glob.getXmlBlasterAccess(), this.updateStream, this.responseStream, null);

          if (this.prepareForPublish) {
             this.interpreter.registerMsgUnitCb(new I_MsgUnitCb() {
                public boolean intercept(MsgUnit msgUnit) {
                   msgUnit.getQosData().clearRoutes();
                   return true;
                }
             });
          }

          if (verbose > 0)  System.out.println("scriptFile=" + this.scriptFile + " propertyFile=" + this.propertyFile + " loggingFile=" + this.loggingFile);
          if (verbose > 2)  System.out.println(this.glob.getProperty().toXml());
          
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

    /**
     * Set verbosity from 0 (silent) to 3 (chatterbox). 
     * @param level defaults to 1
     */
    public void setVerbose(int level) {
       this.verbose = level;
    }

    /**
     * Set the task body text, we expect a valid xmlBlaster xml script. 
     * @param text
     */
    public void addText(String text) {
       this.xmlBlasterScript = text;
       if (verbose > 0) log("Your script to execute: " + text);
    }

   /**
    * The file whereto dump the method invocation return values. 
    * @param responseFile The outFile to set, for example "/tmp/responses.xml"
    */
   public void setResponseFile(String responseFile) {
      if (responseFile == null || responseFile.trim().length() < 1)
         this.responseFile = null;
      else
         this.responseFile = responseFile;
   }

   /**
    * Pass the name of a file which contains XmlBlaster script markup. 
    * @param scriptFile The scriptFile to set.
    */
   public void setScriptFile(String scriptFile) {
      if (scriptFile == null || scriptFile.trim().length() < 1)
         this.scriptFile = null;
      else
         this.scriptFile = scriptFile;
   }

   /**
    * Where shall the update messages be dumped to. 
    * @param updateFile The updateFile to set, for example "/tmp/updates.xml"
    */
   public void setUpdateFile(String updateFile) {
      if (updateFile == null || updateFile.trim().length() < 1)
         this.updateFile = null;
      else
         this.updateFile = updateFile;
   }

   /**
    * Fake the sender address, this is useful to resend dead messages. 
    * @param prepareForPublish The prepareForPublish to set.
    */
   public void setPrepareForPublish(boolean prepareForPublish) {
      this.prepareForPublish = prepareForPublish;
   }

   /**
    * The <code>xmlBlaster.properties</code> to use. 
    * @param propertyFile The propertyFile to set, e.g. "/tmp/xmlBlaster.properties"
    */
   public void setPropertyFile(String propertyFile) {
      if (propertyFile == null || propertyFile.trim().length() < 1)
         this.propertyFile = null;
      else
         this.propertyFile = propertyFile;
   }

   /**
    * The <code>logging.properties</code> to use. 
    * <p>
    * Set the JDK1.4 logging configuration for the xmlBlaster client library
    * @param loggingFile The loggingFile to set, e.g. "/tmp/logging.properties"
    */
   public void setLoggingFile(String loggingFile) {
      if (loggingFile == null || loggingFile.trim().length() < 1)
         this.loggingFile = null;
      else
         this.loggingFile = loggingFile;
   }

   /**
    * If set to <code>true</code> all properties from the ant build file
    * are inheretid to the Global scope as properties.
    * @param inheritAll The inheritAll to set, defaults to false
    */
   public void setInheritAll(boolean inheritAll) {
      this.inheritAll = inheritAll;
   }
}
