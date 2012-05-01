package org.xmlBlaster.util.property;

//import java.applet.Applet;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;

import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;


/**
 * Handling properties for your project, e.g. your property file $HOME/cool.properties and command line properties.
 * <br />
 * <p />
 * <b>Variable replacement</b>
 * <p />
 * All environment variable (e.g. ${java.io.tmpdir}="/tmp", ${file.separator}="/") can be used
 * in the properties file and are replaced on occurrence.<br />
 * Further, you can use any of your own variables from the properties file or from the
 * command line with curly braces: ${...}.<br />
 * Example:<br />
 * A user may specify the MOMS_NAME and use it later as a variable as follows:<br />
 * <pre>   java -DMOMS_NAME=Joan ...</pre><br />
 * or in the properties file:<br />
 * <pre>   MOMS_NAME=Joan</pre>
 * or on the command line:<br />
 * <pre>   java org.MyApp  -MOMS_NAME Joan</pre><br />
 * and now use it like<br />
 * <pre>   MOMS_RECIPY=Strawberry cake from ${MONS_NAME} is the best.</pre>
 * which results to<br />
 * <pre>   MOMS_RECIPY=Strawberry cake from Joan is the best.</pre>
 * The maximum replacement (nesting) depth is 50.
 * <p />
 * A user may specify the PROJECT_HOME environment variable, the property file will be
 * searched there as well.
 * <p />
 * NOTE: These are often helpful variables
 * <ul>
 *    <li>${user.dir} =   The current directory </li>
 *    <li>${user.home} =  The users home directory</li>
 * </ul>
 * user.dir and user.home are without a path separator at the end
 * <p />
 * The property file settings are the weakest, they are overwritten
 * by system property settings and finally by command line arguments.
 * <p>
 * NOTE: If a variable like ${XYZ} is not resolved an exception is thrown,
 * you can use the markup $_{XYZ} to avoid the exception in which case the
 * $_{XYZ} remains as is.
 * </p>
 *
 * <br />
 * <p>
 * <b>Searching the property file</b>
 * <p />
 * This class searches the given property file in typical directories (see findPath() method).
 * It is searched for in the following order:
 * <ol>
 *    <li>Command line parameter '-propertyFile', e.g. "-propertyFile /tmp/xy.properties"</li>
 *    <li>Local directory user.dir</li>
 *    <li>In $PROJECT_HOME, e.g. "/opt/myproject"</li>
 *    <li>In user.home - $HOME</li>
 *    <li>In CLASSPATH - $CLASSPATH from your environment. You can stuff the property file into your project jar file</li>
 *    <li>In java.home/lib/ext directory, e.g. "/opt/jdk1.2.2/jre/lib/ext"<br />
 *        You may use this path for Servlets, demons etc.</li>
 *    <li>In java.home directory, e.g. "/opt/jdk1.2.2/jre/lib"<br />
 *        You may use this path for Servlets, demons etc.</li>
 *    <li>Fallback: "\jutils" on DOS-WIN$ or "/usr/local/jutils" on UNIX</li>
 * </ol>
 *
 * <br />
 * <p>
 * <b>Array support</b>
 * <br />
 * <br />
 * We look for keys containing [] brackets and collect them into a map
 * </p>
 * <pre>
 *  val[A]=AAA
 *  val[B]=BBB
 * </pre>
 *  Accessing the map with the array praefix (here it is "val")
 * <pre>
 *   Map map = get("val", (Map)null);
 * </pre>
 *   returns a Map containing keys { "A", "B" }
 *   and values { "AAA", "BBB" }
 * <br />
 * <br />
 * Two dimensional arrays are supported as well:
 * <pre>
 *   val[C][1]=cccc   -> map entry with key "C:1" and value "cccc"
 * </pre>
 *
 *
 * <p>
 * <b>Very simple array values</b>
 * <br />
 * <br />
 * Values of properties may have a separator, example:
 * </p>
 * <pre>
 *  names=Joe,Jack,Averell
 * </pre>
 *  Access the names split to an array:
 * <pre>
 *  String[] arr = prop.get("array", new String[0], ",");
 *  //arr[0] contains "Joe", arr[1] contains "Jack" and arr[2] contains "Averell"
 * </pre>
 *
 * <br />
 * <p>
 * <b>Listening on property changes</b>
 * <br />
 * <br />
 * You can listen on added properties, changed properties or on removed properties, the following code
 * snippet shows how to do it:
 * </p>
 * <pre>
 * I_PropertyChangeListener ll = prop.addPropertyChangeListener(theKey, new I_PropertyChangeListener() {
 *       public void propertyChanged(PropertyChangeEvent ev) {
 *          System.out.println("propertyChanged: " + ev.toString());
 *          // key = ev.getKey();
 *          // old = ev.getOldValue();
 *          // value = ev.getNewValue();
 *       }
 *    }
 *    );
 * </pre>
 * Note that setting a property with an equal value is not triggering an event.
 *
 * <p />
 * JDK 1.1 or higher only.
 * @author Marcel Ruff
 * @author Michael Winkler, doubleSlash Net-Business GmbH
 * @see testsuite.TestProperty
 */
public class Property implements Cloneable {
   private final static String ME = "Property";
   //private static Logger log = Logger.getLogger(Property.class.getName());

   private static String separator = null;

   /** The users home directory */
   private static String userHome = null;

   /** current directory, can it change for each instance or is it final in the JVM? */
   private String currentPath = null;

   /** user supplied path in PROJECT_HOME */
   private String projectHomePath = null;

   /** The java home ext directory, e.g. /opt/jdk1.2.2/jre/lib/ext */
   private static String javaHomeExt = null;

   /** The java home directory, e.g. /opt/jdk1.2.2/jre/lib */
   private static String javaHome = null;

   /** The supplied file name, e.g. cool.properties */
   private String propertyFileName = null;

   /** Holding all found properties */
   private Properties properties = null;
   private final Properties dummyProperties = new Properties();

   /** Holding all maps from array properties, e.g. prop[A] */
   private Map propMap = new TreeMap();

   /** Scan system environment variables? */
   private boolean scanSystemProperties = true;

   /** Replace occurrences of ${key} with the value of key */
   private boolean replaceVariables = true;

   /** Scan for properties with square brackets [] and handle them as array */
   private boolean supportArrays = true;

   /** Was the option '--help' or '-help' or '-h' or '-?' specified? */
   private boolean wantsHelp = false;

   /** Applet handle or null */
   private Object /*Applet*/ applet = null;

   /** Map containing as key a property key and as value a
    *  Set of listeners (I_PropertyChangeListener) which wants to be notified on changes
    */
   private HashMap changeListenerMap = new HashMap();

   public static final int MAX_NEST = 50;

   public static final int DEFAULT_VERBOSE=1;
   /**
    * Set the verbosity when loading properties (outputs with System.out).
    * <p />
    * 0=nothing, 1=info, 2=trace, configure with
    * <pre>
    * java -Dproperty.verbose 2
    *
    * java MyApp -property.verbose 2
    * </pre>
    */
   public int verbose = DEFAULT_VERBOSE;



   /**
   * Construct a property container from supplied property file and args array.
   * <p />
   * @param fileName The property file name, e.g. "project.properties"
   * @param scanSystemProperties Scan System.getProperties() as well, you can
   *        add variable to JVM like: java -DmyName=Joe ...
   * @param args A String array with properties, usually from command line, e.g.
   *        java myApp -logging FINE -name Joe
   * @param replaceVariables true: replace occurrences of ${key} with the value of key
   */
   public Property(String fileName, boolean scanSystemProperties, String[] args, boolean replaceVariables) throws XmlBlasterException {
      init(fileName, scanSystemProperties, args, replaceVariables, supportArrays);
   }


   /**
    * Returns the subset of properties found in the properties starting 
    * with the specified prefix. 
    * @param prefix The prefix to use. If null is passed, then all properties
    * are returned
    * 
    * @return the subset of properties found. The keys are stripped from their
    * prefix. The returned keys are returned in alphabetical order.
    */
   public  Map getPropertiesStartingWith(String prefix) {
      TreeMap map = new TreeMap();
      synchronized (this.properties) {
         Enumeration iter = this.properties.keys();
         while (iter.hasMoreElements()) {
            String key = ((String)iter.nextElement()).trim();
            if (prefix == null || key.startsWith(prefix)) {
               Object val = this.properties.get(key);
               if (prefix != null)
                  key = key.substring(prefix.length());
               map.put(key, val);
            }
         }
      }
      synchronized (this.propMap) {
         Iterator iter = this.propMap.entrySet().iterator();
         while (iter.hasNext()) {
            Map.Entry entry = ((Map.Entry)iter.next());
            String key = (String)entry.getKey();
            Object val = entry.getValue();
            if (prefix == null || key.startsWith(prefix)) {
               if (prefix != null)
                  key = key.substring(prefix.length());
               map.put(key, val);
            }
         }
      }

      return map;
   }
   
   /**
    * Returns a map containing all properties of the specified context.
    * 
    * /node/heron/logging, FINE,
    * /node/heron/logging/org.xmlBlaster.engine.level, FINE
    * 
    * Suppose you want to retrieve these values, then the parentCtx is /node/heron and
    * the shortKey is logging. If you want to retrieve both values you have to pass keyForDefault
    * which is not null (for example you will pass '__default'. Then you will get:
    * 
    * __default = FINE
    * org.xmlBlaster.engine.level = FINE
    * 
    * two entries. If you pass keyForDefault = null you will only get the second value
    * 
    * @param ctx
    * @param shortKey
    * @param keyForDefault, can be null in which case it will not return the default value.
    * @return
    */
   public Map getPropertiesForContextNode(ContextNode parentCtx, String shortKey, String keyForDefault) {
      Map general = getPropertiesStartingWith(shortKey + ContextNode.SEP);
      if (keyForDefault != null) {
         Object def = this.properties.getProperty(shortKey);
         if (def != null)
            general.put(keyForDefault, def);
      }
      if (parentCtx == null)
         return general;
      
      String completePropName = parentCtx.getAbsoluteName() + ContextNode.SEP + shortKey;
      Map specific1 = getPropertiesStartingWith(completePropName + ContextNode.SEP);
      Object tmp = this.properties.get(completePropName);
      if (tmp != null)
         specific1.put(completePropName, tmp);
      
      if (keyForDefault != null) {
         Object def = this.properties.getProperty(completePropName);
         if (def != null)
            specific1.put(keyForDefault, def);
      }
      
      String relativePropName = parentCtx.getRelativeName() + ContextNode.SEP + shortKey;
      Map specific2 = getPropertiesStartingWith(relativePropName + ContextNode.SEP);
      if (keyForDefault != null) {
         Object def = this.properties.getProperty(relativePropName);
         if (def != null)
            specific2.put(keyForDefault, def);
      }
      
      Map specific3 = getPropertiesStartingWith(ContextNode.SEP + relativePropName + ContextNode.SEP);
      if (keyForDefault != null) {
         Object def = this.properties.getProperty(ContextNode.SEP + relativePropName);
         if (def != null)
            specific3.put(keyForDefault, def);
      }
      
      general.putAll(specific1);
      general.putAll(specific2);
      general.putAll(specific3);
      return general;
   }

   /**
    * Construct a property container from supplied property file and enumeration.
    * <p />
    * Usually used by servlets (ServletConfig conf):<br />
    * <pre>
    *    Properties extraProps = new Properties();
    *    Enumeration e = conf.getInitParameterNames();
    *    while (e.hasMoreElements()) {
    *       String name = (String)e.nextElement();
    *       extraProps.put(name, conf.getInitParameter(name));
    *    }
    * </pre>
    *
    * @param fileName The property file name, e.g. "project.properties"
    * @param scanSystemProperties Scan System.getProperties() as well, you can
    *        add variable to JVM like: java -DmyName=Joe ...
    * @param args A String array with properties, usually from command line, e.g.
    *        java myApp -trace true -name Joe
    * @param replaceVariables true: replace occurrences of ${key} with the value of key
    */
   public Property(String fileName, 
                   boolean scanSystemProperties, 
                   java.util.Properties extraProps, 
                   boolean replaceVariables)
      throws XmlBlasterException
   {
      Vector buffer = new Vector();

      Enumeration e = extraProps.propertyNames();
      while (e.hasMoreElements()) {
         String name = (String)e.nextElement();
         buffer.add(new String("-" + name));
         buffer.add(extraProps.getProperty(name));
      }

      String[] arr = new String[buffer.size()];
      buffer.copyInto(arr);

      init(fileName, scanSystemProperties, arr, replaceVariables, supportArrays);
   }


   /**
   * Construct a property container from supplied property file and enumeration.
   * <p />
   * Usually used by servlets (ServletConfig conf):<br />
   * <pre>
   *    Properties extraProps = new Properties();
   *    Enumeration e = conf.getInitParameterNames();
   *    while (e.hasMoreElements()) {
   *       String name = (String)e.nextElement();
   *       extraProps.put(name, conf.getInitParameter(name));
   *    }
   * </pre>
   *
   * @param fileName The property file name, e.g. "project.properties"
   * @param scanSystemProperties Scan System.getProperties() as well, you can
   *        add variable to JVM like: java -DmyName=Joe ...
   * @param name of project which is to be extracted from user.home/projects.properties
   * @param replaceVariables true: replace occurrences of ${key} with the value of key
   */
   public Property (String fileName, boolean scanSystemProperties, String projectname, boolean replaceVariables) throws XmlBlasterException {
      init(fileName, scanSystemProperties, (String[])null, replaceVariables, supportArrays);

      FileInputStream fis = null;
      String projectFileName = null;
      try { // Read user supplied properties file
         if (userHome != null) {
            projectFileName = userHome + "/projects.properties";
            File file = new File (projectFileName);
            fis = new FileInputStream (file);
         }
         Properties myProps = new Properties();
         myProps.load (fis);
         if (!myProps.getProperty (projectname, "").equals("")) {
            this.projectHomePath = myProps.getProperty (projectname);
            if (verbose>=1) System.out.println("Property: found " + projectname + " in " + projectFileName);
         }
         else {
            throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".Error", "Unable to find " + projectname + " in " + projectFileName);
         }
         if (fis != null)
            fis.close();
      }
      catch (IOException e) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".Error", "Unable to find " + projectFileName + ": " + e);
      }
   }
   /**
   * Construct a property container from supplied property file and args array.
   * <p />
   * @param fileName The property file name, e.g. "project.properties"
   * @param scanSystemProperties Scan System.getProperties() as well, you can
   *        add variable to JVM like: java -DmyName=Joe ...
   * @param args A String array with properties, usually from command line, e.g.
   *        java myApp -trace true -name Joe
   * @param replaceVariables true: replace occurrences of ${key} with the value of key
   * @param output false: no info output to System.out is printed. Only successfull operation.
   */
   public Property(String fileName, boolean scanSystemProperties, String[] args, boolean replaceVariables, boolean output)
      throws XmlBlasterException {
      if (output)
         this.verbose = 2;
      init (fileName, scanSystemProperties, args, replaceVariables, supportArrays);
   }




   /**
   * Construct a property container from supplied property file (URL) and Applet.
   * <p />
   * NOTE: Applet HTML PARAM values may not contain variables ${...}, they are currently not replaced.
   *
   * @param propertyFileUrl applet.getCodeBase()+"xy.properties" e.g. "http://myHost.com:80/xy.properties"
   *
   * @param scanSystemProperties Scan System.getProperties() as well, you can
   *        add variable to JVM like: java -DmyName=Joe ...<br />
   *   NOTE: If you set this to true, you need a signed applet!
   *
   * @param applet Not yet supported. How to access all applet parameters???
   * @param replaceVariables true: replace occurrences of ${key} with the value of key
   */
   public Property(String propertyFileUrl, boolean scanSystemProperties, Object/*Applet*/ applet, boolean replaceVariables) throws XmlBlasterException {
      System.out.println("Property for Applet: propertyFileUrl=" + propertyFileUrl + ", scanSystemProperties=" + scanSystemProperties + ", replaceVariables=" + replaceVariables);
      this.scanSystemProperties = scanSystemProperties;
      this.replaceVariables = replaceVariables;
      this.applet = applet;

      // Variant downloading it with a jar file using HTML ARCHIVE tag?
      //  java.net.URL      url;
      //  url = this.getClass().getResource(propertyFileName);

      if (propertyFileUrl == null) {
        loadProps(null, null);
        if (properties == null)
         properties = dummyProperties;
        return;
      }

      URL url = null;
      FileInfo info = new FileInfo(propertyFileUrl);
      try {
        url = new URL(propertyFileUrl);
        this.propertyFileName = url.getFile();
        info.fullPath = this.propertyFileName;
        try {
          info.in = url.openStream();
        }
        catch (java.io.IOException e) {
         System.out.println("Property file " + url + " read error, continuing without it: " + e.toString());
         //throw new XmlBlasterException(ME + ".Error", "URL access: " + e.toString());
        }
      }
      catch (java.net.MalformedURLException e) {
        System.out.println("Property file " + propertyFileUrl + " is malformed, continuing without it: " + e.toString());
      }
      loadProps(info, null);
      if (properties == null)
        properties = dummyProperties;
   }

   /**
    * We do a deep copy for all properties and listeners
    */
   public Object clone() {
      try {
         Property p = (Property)super.clone();
         p.properties = (Properties)this.properties.clone();
         //p.dummyProperties = (Properties)this.dummyProperties.clone();
         p.propMap = this.propMap != null ? (TreeMap)((TreeMap)this.propMap).clone():null;
         p.changeListenerMap = (HashMap)changeListenerMap.clone();
         return p;
      }
      catch (CloneNotSupportedException e) {
         if (verbose>=1) System.err.println("Property clone failed: " + e.toString());
         throw new RuntimeException("org.xmlBlaster.util.property.Property clone failed: " + e.toString());
      }
   }


   /**
    * Internal Initialize.
    * @param argsProps The checked command line arguments
    */
   private void init(String fileName_, boolean scanSystemProperties, String[] args, boolean replaceVariables, boolean supportArrays)
      throws XmlBlasterException {

      this.propertyFileName = fileName_;

      if (System.getProperty("property.verbose") != null) {
         try { verbose = Integer.parseInt(System.getProperty("property.verbose").trim()); } catch(NumberFormatException e) { System.err.println("-property.verbose expects a number"); }
      }
      Properties argsProps = argsToProps(args);
      if (argsProps != null && argsProps.get("---help") != null) {
         wantsHelp = true;
      }

      if (verbose>=2) {
         System.out.println(
        "Property: filenName=" + fileName_
          + ", scanSystemProperties=" + scanSystemProperties
          + ", args=" + (argsProps == null ? "null" : "[" + argsProps.size() + "]")
          + ", replaceVariables=" + replaceVariables
          + ", supportArrays=" + supportArrays
          );
      }
      this.propertyFileName = fileName_;
      this.scanSystemProperties = scanSystemProperties;
      this.replaceVariables = replaceVariables;
      this.supportArrays = supportArrays;
      separator = System.getProperty("file.separator");
      userHome = System.getProperty("user.home");
      this.currentPath = System.getProperty("user.dir");
      this.projectHomePath = System.getProperty("PROJECT_HOME");
      javaHomeExt = System.getProperty("java.ext.dirs");
      javaHome = System.getProperty("java.home");

      loadProps(findGivenFile(propertyFileName), argsProps);

      if (properties == null)
        properties = dummyProperties;
   }

   /**
   * Get the internal handle.
   * @return The Properties handle.
   */
   public final java.util.Properties getProperties() {
      return properties;
   }

   public void setApplet(Object/* java.applet.Applet*/ applet)
   {
      this.applet = applet;
      java.applet.Applet ap = (java.applet.Applet)this.applet;
      if (ap.getParameter("--help")!=null || ap.getParameter("-?")!=null || ap.getParameter("-h")!=null || ap.getParameter("-help")!=null)
         wantsHelp = true;
   }

   private String get_(String key)
   {
      if (key == null) return null;
      if (applet != null) {
        java.applet.Applet ap = (java.applet.Applet)this.applet;
        String a = ap.getParameter(key);
        if (a != null) return a;
      }
      Object obj = properties.get(key);
      if (obj == null)
    	  return null;
      else if (obj instanceof String)
    	  return (String)obj;
      return ""+obj;
      //return (String)properties.get(key);
   }

   private Map getMap_(String key)
   {
      if (applet != null) {
      }
      if (propMap == null)
         return null;

      return (Map)propMap.get(key);
   }


   /**
     * Set or overwrite a property,
     * note that currently no variable replacement is implemented for the passed value.
     * @param key The key for this property
     * @param value The value for it
     * @return The value, ${...} variables are replaced
     */
   public final String set(String key, String value) throws XmlBlasterException
   {
      String oldValue = get_(key);

      value = replaceVariable(key, value);
      properties.setProperty(key, value);

      fireChangeEvent(key, oldValue, value);

      if (supportArrays == true) scanArray(key, value);
      return value;
   }



   /**
   * Try to find the given key.
   * @param key the key to look for
   * @param defaultVal the default value to return if key is not found
   * @return The String value for the given key
   */
   public final String get(String key, String defaultVal) {
      String str = get_(key);
      if (str == null)
        return defaultVal;
      return str;
   }



   /**
   * Try to find the given key.
   * <p />
   * Example:<br />
   * NameList=Josua,David,Ken,Abel<br />
   * Will return each name separately in the array.
   * @param key the key to look for
   * @param defaultVal The default value to return if key is not found
   * @param separator  The separator, typically ","
   * @return The String array for the given key, the elements are trimmed (no leading/following white spaces)
   */
   public final String[] get(String key, String[] defaultVal, String separator) {
      String str = get_(key);
      if (str == null)
        return defaultVal;
      StringTokenizer st = new StringTokenizer(str, separator);
      int num = st.countTokens();
      String[] arr = new String[num];
      int ii=0;
      while (st.hasMoreTokens()) {
        arr[ii++] = st.nextToken().trim();
      }
      return arr;
   }



   /**
   * Try to find the given key.
   * @param key the key to look for
   * @param defaultVal the default value to return if key is not found
   * @return The int value for the given key
   */
   public final int get(String key, int defaultVal) {
      String str = get_(key);
      if (str == null)
        return defaultVal;
      try {
        return Integer.parseInt(str);
      }
      catch (Exception e) {
        return defaultVal;
      }
   }



   /**
   * Try to find the given key.
   * @param key the key to look for
   * @param defaultVal the default value to return if key is not found
   * @return The long value for the given key
   */
   public final long get(String key, long defaultVal) {
      String str = get_(key);
      if (str == null)
        return defaultVal;
      try {
        return Long.parseLong(str);
      }
      catch (Exception e) {
        return defaultVal;
      }
   }


   /**
   * Try to find the given key.
   * @param key the key to look for
   * @param defaultVal the default value to return if key is not found
   * @return The double value for the given key
   */
   public final double get(String key, double defaultVal) {
      String str = get_(key);
      if (str == null)
        return defaultVal;
      try {
        return Double.parseDouble(str);
      }
      catch (Exception e) {
        return defaultVal;
      }
   }


   /**
   * Try to find the given key.
   * @param key the key to look for
   * @param defaultVal the default value to return if key is not found
   * @return The float value for the given key
   */
   public final float get(String key, float defaultVal) {
      String str = get_(key);
      if (str == null)
        return defaultVal;
      try {
        return Float.parseFloat(str);
      }
      catch (Exception e) {
        return defaultVal;
      }
   }



   /**
   * Try to find the given key.
   * <p />
   * See toBool() for a list of recognized strings.
   * @param key the key to look for
   * @param defaultVal the default value to return if key is not found
   * @return The boolean value for the given key
   */
   public final boolean get(String key, boolean defaultVal) {
      String str = get_(key);
      if (str == null)
        return defaultVal;
      try {
        return toBool(str);
      }
      catch (Exception e) {
        return defaultVal;
      }
   }


   /**
   * Try to find the given key.
   * <p />
   * Example:
   * <pre>
   *   Map map = get(key, (Map)null);
   *   if (map != null) {
   *      ...
   *   }
   * </pre>
   * @param key the key to look for
   * @param defaultVal the default Map to return if key is not found
   * @return The Map or null if not found
   */
   public final Map get(String key, Map defaultVal) {
      Map map = getMap_(key);
      if (map == null)
        return defaultVal;
      else
        return map;
   }



   /**
   * Try to find the given key.
   * <p />
   * @param key the parameter key to look for
   * @return true if the property exists
   */
   public final boolean propertyExists(String key) {
      String str = get_(key);
      if (str == null)
        return false;
      return true;
   }



   /**
   * Remove the given property.
   * <p />
   * This method does nothing if the key is not in the property hashtable.
   * @param key the key to remove
   * @return The removed value
   */
   public final String removeProperty(String key) {
      String oldValue = (String)properties.remove(key);
      fireChangeEvent(key, oldValue, (String)null);
      return oldValue;
   }



   /**
   * If set to true user wants you to display a usage text.
   * @return true if the option '--help' or '-help' or '-h' or '-?' was given.
   */
   public final boolean wantsHelp() {
      return wantsHelp;
   }



   /**
   * Use this method only the first time to initialize everything.
   * @param argsProps This key/value parameter array is added to the properties object (see addArgs2Props()).
   * @return the initialized Properties
   */
   public final Properties loadProps(FileInfo info, Properties argsProps) throws XmlBlasterException {
      if (properties != null) {
        if (verbose>=1) System.out.println("Property: reload loadProps()");
      }

      // set default
      properties = new Properties();
      try {
         // 1. Read user supplied properties file
         if (info != null) {
            InputStream inputStream = info.getInputStream();
            if (inputStream != null) {
               try {
                  properties.load(inputStream);
                  if (verbose>=2) System.out.println("Property: Loaded file " + propertyFileName);
               } finally {
                  info.closeInputStream();
               }
            }
            else {
               if (verbose>=1) System.out.println("Property: No property file given.");
            }
         } else {
            if (verbose>=0 && propertyFileName!=null) System.err.println("Property: Please copy " + propertyFileName + " to your home directory. We continue with default settings.");
         }

        // 2. Read system environment, e.g. java -Dname=joe
        if (scanSystemProperties == true) {
         Properties env = System.getProperties();
         for (Enumeration e = env.propertyNames(); e.hasMoreElements();) {
            String key = (String) e.nextElement();
            String value = System.getProperty(key);
            properties.put(key, value);
            fireChangeEvent(key, null, value);
         }
        }
        else {
           if (verbose>=1) System.out.println("Property: No system properties scanned.");
         }

        // 3. read user supplied String[], e.g. command line parameters
        if (argsProps != null) {
         addArgs2Props(properties, argsProps);
        }
        else {
           if (verbose>=1) System.out.println("Property: No args array given.");
        }

        // 4. Substitute dynamic variables, e.g. ${user.dir}
        if (replaceVariables == true)
         replaceVariables();

         // 5. Scan variables containing []
        if (supportArrays == true)
         scanArrays();
      }
      catch (IOException e) {
        throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".Error", "Unable to initialize " + propertyFileName + ": " + e);
      }
      return properties;
   }



   /**
     * Replace all dynamic variables, e.g. ${XY} with their values.
     */
   private final void replaceVariables() throws XmlBlasterException {
      if (replaceVariables == false)
        return;
      for (Enumeration e = properties.keys(); e.hasMoreElements();) {
        String key = (String) e.nextElement();
        String value = get_(key);
        //
        // ${PROJECT_HOME} is the actual directory
        // replace it with the home directory
        if (value.equals("${PROJECT_HOME}")) {
           properties.put(key, new File("").getAbsolutePath());
        }
        else {
           String replaced = replaceVariable(key, value);
           if (replaced != null && !replaced.equals(value)) {
              properties.put(key, replaced);
              fireChangeEvent(key, value, replaced);
           }
        }
      }
   }

   /**
     * We look for keys containing [] brackets and collect them into a map
     * <pre>
     *  val[A]=AAA
     *  val[B]=BBB
     *  ->
     *   get("val", (Map)null);
     *   Returns a Map containing keys { "A", "B" }
     *   and values { "AAA", "BBB" }
     * </pre>
     * Two dimensional arrays are supported as well:
     * <pre>
     *   val[C][1]=cccc   -> map entry with key "C:1" and value "cccc"
     * </pre>
     */
   private final void scanArrays() throws XmlBlasterException {
      if (supportArrays == false)
         return;
      for (Enumeration e = properties.keys(); e.hasMoreElements();) {
         String key = (String) e.nextElement();
         String value = get_(key);
         scanArray(key, value);
      }
   }

   /**
    * @see #scanArrays()
    */
   private final void scanArray(String key, String value) throws XmlBlasterException {

      int posOpen = key.indexOf("[");
      if (posOpen < 0)
         return;

      int posClose = key.indexOf("]", posOpen);
      if (posClose < 0)
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".scanArray", "Invalid array, missing closing ']' bracket for key='" + key + "'");
      if (posClose <= posOpen)
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".scanArray", "Invalid array, closing ']' bracket before '[' for key='" + key + "'");
      String prefix = key.substring(0, posOpen);

      Map map = getMap_(prefix);
      if (map == null) {
         map = new TreeMap();
         /*String oldValue = (String)*/propMap.put(prefix, map);
      }

      String arg = key.substring(posOpen+1, posClose);

      int posOpen2 = key.indexOf("[", posClose+1);
      if (posOpen2 >= 0) {
         int posClose2 = key.indexOf("]", posOpen2);
         if (posClose2 < 0)
            throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".scanArrays", "Invalid array, missing closing ']' braket for key='" + key + "'");
         arg += ":";
         arg += key.substring(posOpen2+1, posClose2);
      }

      arg = replaceVariable(key, arg);

      String oldValue = (String)map.put(arg, value);
      fireChangeEvent(arg, oldValue, value);
   }

   /**
    * Notifies registered listeners when a property has changed or is created or is deleted
    * If key or values are null, no event is fired
    */
   private void fireChangeEvent(String key, String oldValue, String newValue) {
      if ( key == null || (newValue != null && newValue.equals(oldValue)) || (newValue == null && oldValue == null) ) {
         return;
      }
      Object obj = changeListenerMap.get(key);
      if ( obj != null ) {
         Set listenerSet = (Set)obj;
         Iterator it = listenerSet.iterator();
         PropertyChangeEvent ev = new PropertyChangeEvent(key, oldValue, newValue);
         while (it.hasNext()) {
            I_PropertyChangeListener l = (I_PropertyChangeListener)it.next();
            l.propertyChanged(ev);
         }
      }
   }

   /**
     * Replace dynamic variables, e.g. ${XY} with their values.
     * <p />
     * The maximum replacement (nesting) depth is 50.
     * @param key For logging only
     * @value The value string which may contain zero to many ${...} variables
     * @return The new value where all ${} are replaced.
     */
   private final String replaceVariable(String key, String value) throws XmlBlasterException {
      value = replaceVariableWithException(key, value);
      return replaceVariableNoException(key, value);
   }

   /**
    * Replace dynamic variables, e.g. ${XY} with their values.
    * <p />
    * The maximum replacement (nesting) depth is 50.
    * @param key For logging only
    * @value The value string which may contain zero to many ${...} variables, if null we return null
    * @return The new value where all ${} are replaced.
    * @throws XmlBlasterException if a variable ${...} is not found of max nesting depth is reached or matching "}" is missing
    */
   public final String replaceVariableWithException(String key, String value) throws XmlBlasterException {
      if (value == null) return null;
      if (replaceVariables == false)
        return value;
      String origValue = value;
      for (int ii = 0;; ii++) {
         int from = value.indexOf("${");
         if (from != -1) {
            int to = value.indexOf("}", from);
            if (to == -1) {
               throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".InvalidVariable", "Invalid variable '" + value.substring(from) + "', expecting ${} syntax.");
            }
            String sub = value.substring(from, to + 1); // "${XY}"
            String subKey = sub.substring(2, sub.length() - 1); // "XY"
            String subValue = get_(subKey);
            if (subValue == null) {
               throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".UnknownVariable", "Unknown variable " + sub + "");
            }
            value = ReplaceVariable.replaceAll(value, sub, subValue);
         }
         else {
            if (ii > 0 && verbose>=2) {
               System.out.println("Property: Replacing '" + key + "=" + origValue + "' to '" + value + "'");
            }
            return value;
         }
         if (ii > MAX_NEST) {
            throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".MaxNest", "Maximum nested depth of " + MAX_NEST + " reached for variable '" + get_(key) + "'.");
         }
      }
   }

   /**
    * Replace dynamic variables, e.g. $_{XY} with their values.
    * <p />
    * The maximum replacement (nesting) depth is 50.
    * 
    * @param key
    *           For logging only
    * @value The value string which may contain zero to many $_{...} variables
    * @return The new value where all resolvable $_{} are replaced.
    * @throws XmlBlasterException
    *            if matching "}" is missing
    */
   public final String replaceVariableNoException(String key, String value) throws XmlBlasterException {
      if (replaceVariables == false)
        return value;
      String origValue = value;
      for (int ii = 0;; ii++) {
         int from = value.indexOf("$_{");
         if (from != -1) {
            int to = value.indexOf("}", from);
            if (to == -1) {
               throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".InvalidVariable", "Invalid variable '" + value.substring(from) + "', expecting ${} syntax.");
            }
            String sub = value.substring(from, to + 1); // "$_{XY}"
            String subKey = sub.substring(3, sub.length() - 1); // "XY"
            String subValue = get_(subKey);
            if (subValue == null) {
               if (verbose>=2) System.out.println("Property: Unknown variable " + sub + " is not replaced");
               return value;
            }
            value = org.xmlBlaster.util.ReplaceVariable.replaceAll(value, sub, subValue);
         }
         else {
            if (ii > 0 && verbose>=2) {
               System.out.println("Property: Replacing '" + key + "=" + origValue + "' to '" + value + "'");
            }
            return value;
         }
         if (ii > MAX_NEST) {
            if (verbose>=1) System.out.println("Property: Maximum nested depth of " + MAX_NEST + " reached for variable '" + get_(key) + "'.");
            return value;
         }
      }
   }

   /**
   * Parse a string to boolean.
   * <p />
   * @param token for example "false"
   * @return true for one of "true", "yes", "1", "ok"<br />
   *         false for "false", "0", "no"
   * @exception if none of the above strings
   */
   static public final boolean toBool(String token) throws XmlBlasterException {
      if (token == null)
        throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME, "Can't parse <null> to true or false");
      if (token.equalsIgnoreCase("true") || token.equalsIgnoreCase("1") || token.equalsIgnoreCase("ok") || token.equalsIgnoreCase("yes"))
        return true;
      if (token.equalsIgnoreCase("false") || token.equalsIgnoreCase("0") || token.equalsIgnoreCase("no"))
        return false;
      throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME, "Can't parse <" + token + "> to true or false");
   }


   /********************************************************************************************
    * Look for properties file.
    * <p />
    * The property file is searched in the specific order as described above
    *
    * @param      fileName
    *             e.g. "cool.properties".
    * @return     the path to file or null if not found
    *******************************************************************************************/
   public final FileInfo findPath(String fileName) {
      if (fileName == null)
        return null;

      File f = null;
      FileInfo info = new FileInfo(fileName);

      f = new File(currentPath, fileName);
      if (f.exists()) {
         if (verbose>=1) System.out.println("Property: Loading " + fileName + " from directory " + currentPath);
         info.path = currentPath;
         return info;
      }
      else {
         if (verbose>=2)
            System.out.println("Property: File '" + fileName + "' is not in current directory " + currentPath);
      }


      f = new File(fileName); // if given with full name
      if (f.exists()) {
         if (verbose>=1) System.out.println("Property: Loading " + fileName + " from directory " + currentPath);
         info.path = f.getName(); // The file name without path
         info.fullPath = fileName;
         return info;
      }
      else {
         if (verbose>=2)
            System.out.println("Property: File '" + fileName + "' is not in current directory " + currentPath);
      }


      if (projectHomePath == null) {
         if (verbose>=2) {
            System.out.println("Property: File '" + fileName + "' is not in PROJECT_HOME, 'java -DPROJECT_HOME=...' is not set ...");
         }
      }
      else {
         if (!projectHomePath.endsWith(separator))
            projectHomePath += separator;
         f = new File(projectHomePath, fileName);
         if(f.exists()) {
            if (verbose>=1) System.out.println("Property: Loading " + fileName + " from directory " + projectHomePath);
            info.path = projectHomePath;
            return info;
         }
         else {
            if (verbose>=2)
               System.out.println("Property: File '" + fileName + "' is not in directory PROJECT_HOME=" + projectHomePath);
         }
      }

      f = new File(userHome, fileName);
      if (f.exists()) {
        if (verbose>=1) System.out.println("Property: Loading " + fileName + " from directory " + userHome);
        info.path = userHome;
        return info;
      }
      else {
        if (verbose>=2)
           System.out.println("Property: File '" + fileName + "' is not in user.home directory " + userHome);
      }

      ClassLoader loader = Property.class.getClassLoader();
      if (loader != null) {
        java.net.URL url = loader.getResource(fileName);
        if (url != null ) {
           info.in = loader.getResourceAsStream(fileName);
           info.fullPath = url.getFile(); // path and filename
           if (verbose>=1) System.out.println("Property: Loading " + fileName + " from CLASSPATH " + info.getFullPath());
         return info;
        }
      }
      if (verbose>=2)
         System.out.println("Property: File '" + fileName + "' is not in CLASSPATH");

      f = new File(javaHomeExt, fileName);
      if (f.exists()) {
        if (verbose>=1) System.out.println("Property: Loading " + fileName + " from directory " + javaHomeExt);
        info.path = javaHomeExt;
        return info;
      }
      else {
         if (verbose>=2)
           System.out.println("Property: File '" + fileName + "' is not in java.home/lib/ext directory " + javaHomeExt);
      }


      f = new File(javaHome, fileName);
      if (f.exists()) {
        if (verbose>=1) System.out.println("Property: Loading " + fileName + " from directory " + javaHome);
        info.path = javaHome;
        return info;
      }
      else {
         if (verbose>=2) {
           System.out.println("Property: File '" + fileName + "' is not in java.home directory " + javaHome);
         }
      }
      String guess;
      if (separator.equals("/"))
        guess = "/usr/local/jutils/";
      else
        guess = "\\jutils\\";


      f = new File(guess, fileName);
      if(f.exists()) {
        if (verbose>=1) System.out.println("Property: Loading " + fileName + " from directory " + guess);
        info.path = guess;
        return info;
      }
      else {
        if(verbose>=2)
           System.out.println("Property: File '" + fileName + "' is not in directory " + guess);
      }

      return null;
   }


   /**
   * Find properties file.
   * @param args Only "-propertyFile /tmp/xy.properties" is evaluated (if given)
   * @return The property file name or null if not found of not given.
      */
   public final FileInfo findArgsFile(Properties argsProps) {

      String argsLocation = (String)argsProps.get("propertyFile");
      if (argsLocation != null) {
         FileInfo info = findPath(argsLocation);
         if(info != null) {
            propertyFileName = argsLocation;
         }
         else {
            if (verbose>=0) System.out.println("Property: ERROR: File '-propertyFile " + argsLocation + "' not found!");
            // TODO: throw Exception?
         }
         return info;
      }
      return null;
   }


   /**
   * Find properties file which was given with the constructor.
   * <p />
   * See findPath() for search - logic
   *
   * @param fileName e.g. "cool.properties"
   * @param args Only "-propertyFile /tmp/xy.properties" is evaluated (if given)
   *             this has precedence over the given fileName!
   * @return The path to file, e.g. "\jutils\"
      */
   public final FileInfo findGivenFile(String fileName) {

      if (fileName == null) {
        if (verbose>=2) System.out.println("Property: No property file specified.");
        return null;
      }
      // The method 'findPath' now return the full path including the filename
      FileInfo info = findPath(fileName);
      if(info == null) {
        if (verbose>=1) System.err.println("Property: File '" + fileName + "' not found");
      }
      return info;
   }


   /**
   * Find properties file.
   * <p />
   * See findPath() for search - logic
   *
   * @param fileName e.g. "cool.properties"
   * @param args Only "-propertyFile /tmp/xy.properties" is evaluated (if given)
   *             this has precedence over the given fileName!
   * @return The path to file or null
      */
   public final FileInfo findFile(String fileName, Properties argsProps) {

      FileInfo info = findArgsFile(argsProps);
      if (info != null) return info;

      return findGivenFile(fileName);
   }


   /**
   * Add key/values, for example from startup command line args to
   * the property variable.
   * <p />
   * Args parameters are stronger and overwrite the property file variables.
   * <p />
   * The arg key must have a leading - or -- (as usual on command line).<br />
   * The leading - are stripped (to match the property variable)
   * args must be a tuple (key / value pairs).
   * <p />
   */
   public void addArgs2Props(String[] args) throws XmlBlasterException {

      if (args == null)
         return;

      if (System.getProperty("property.verbose") != null) {
         try { verbose = Integer.parseInt(System.getProperty("property.verbose").trim()); } catch(NumberFormatException e) { System.err.println("-property.verbose expects a number"); }
      }
      if (properties == null) properties = new Properties();
      Properties pp = argsToProps(args);
      if (pp != null && pp.get("---help") != null) {
         wantsHelp = true;
      }
      addArgs2Props(properties, pp);

      replaceVariables();

      if (supportArrays == true) scanArrays(); // Buggy: scans everything again

      if (verbose>=1) System.out.println("Property: Added " + args.length/2 + " property pairs");
   }

   /**
   * The same as addArgs2Props(String[] args) but passing the args in a Properties hashtable.
   */
   public void addArgs2Props(Properties argsProps) throws XmlBlasterException {
      addArgs2Props(propsToArgs(argsProps));
   }


   /**
    * Convert a properties hashtable to a String array with leading "-" in front of the keys
    */
   public static String[] propsToArgs(Map props)
   {
      if (props == null) return new String[0];

      String[] args = new String[props.size()*2];
      Iterator e = props.keySet().iterator();
      int ii = 0;
      while (e.hasNext()) {
         String key = (String)e.next();
         args[ii] = "-"+key;
         ii++;
         args[ii] = (String)props.get(key);
         //System.out.println("ii=" + ii + ": " + args[ii] + " key=" + key);
         ii++;
      }
      return args;
   }


   /**
    * See method addArgs2Props(args)
    */
   private void addArgs2Props(Properties props, Properties argsProps) 
      throws XmlBlasterException
   {
      if(argsProps == null) {
         return;
      }

      // 1. Load property file if given on command line
      FileInfo info = findArgsFile(argsProps);
      if (info != null) {
         InputStream inputStream = info.getInputStream();
         if (inputStream != null) {
            try {
               props.load(inputStream);
               if (verbose>=2) System.out.println("Property: Loaded file " + info.getFullPath());
            } catch(IOException e) {
               if (verbose>=0) System.out.println("Property: ERROR loading file " + info.getFullPath());
            } finally {
               info.closeInputStream();
            }
         }
         else {
            if (verbose>=1) System.out.println("Property: No property file given.");
         }
      }

      // 2. Load other command line properties
      Enumeration e = argsProps.keys();
      while (e.hasMoreElements()) {
         String key = (String)e.nextElement();
         String value = (String)argsProps.get(key);
         String oldValue = (String)props.put(key, value);
         fireChangeEvent(key, oldValue, value);
      }
   }

   /**
    * Scan args, correct and check them and return the result in a Properties hash table
    * @parameter null if nothing found
    */
   public static Properties argsToProps(String[] args) throws XmlBlasterException {
      Properties props = null;
      if (args == null) return null;
      for (int ii = 0; ii < args.length; ii++) {
         String arg = args[ii];
         if (arg != null && arg.startsWith("-")) { // only parameters starting with "-" are recognized
            String key = arg;
            if (arg.equals("--help") || arg.equals("-?") || arg.equals("-h") || arg.equals("-help")) {
               if (props == null) props = new Properties();
               props.put("---help", "true");
               continue; // ignore
            }
            if (arg.startsWith("--"))
               key = arg.substring(2); // strip "--"
            else if (arg.startsWith("-"))
               key = arg.substring(1); // strip "-"

            if (key.length() < 1) {
               throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".Invalid", "Ignoring argument <" + arg + ">.");
            }
            if ((ii + 1) >= args.length) {
               // System.err.println("Property: The property '" + arg + "' has no value");
               throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".MissingValue", "The property '" + arg + "' has no value.");
            }
            String arg2 = args[ii + 1];
            if (arg2 == null) arg2 = "";
            if (props == null) props = new Properties();
            //if (key.equals("property.verbose")) {
            //   try { verbose = Integer.parseInt(arg2.trim()); } catch(NumberFormatException e) { System.err.println("-property.verbose expects a number"); }
            //}
            props.put(key, arg2);
            ii++;
            // System.out.println("Property: Setting command line argument " + key + " with value " + arg2);
         }
         else {
            throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, ME + ".Invalid", "Ignoring unknown argument <" + arg + ">, it should start with '-'.");
         }
      }
      return props;
   }


   /**
    * Listen on change or creation events of the given key.
    * Note that no initial event is sent.
    * @return The listener you provided (nice if it is an anonymous class)
    */
   public final I_PropertyChangeListener addPropertyChangeListener(String key, I_PropertyChangeListener l) {
      return addPropertyChangeListener(key, null, l);
   }

   /**
    * Listen on change or creation events of the given key
    * @param defaultValue If not null an initial event is sent: If the property is known its value is used else the defaultValue is used.
    *                     The defaultValue is not stored in this case, only bounced back.
    * @return The listener you provided (nice if it is an anonymous class)
    */
   public final I_PropertyChangeListener addPropertyChangeListener(String key, String defaultValue, I_PropertyChangeListener l) {
      if (key == null || l == null) {
         throw new IllegalArgumentException("addPropertyChangeListener got illegal arguments key=" + key);
      }
      Object o = changeListenerMap.get(key);
      if (o == null) {
         o = new HashSet();
         changeListenerMap.put(key, o);
      }
      Set listenerSet = (HashSet)o;
      listenerSet.add(l);

      if (defaultValue != null) { // wants initial notification
         String value = get_(key);
         l.propertyChanged(new PropertyChangeEvent(key, value, (value==null)?defaultValue:value ));
      }

      return l;
   }

   /**
    * Listen on change or creation events of the given key
    * @param l If null all listeners are removed
    */
   public final void  removePropertyChangeListener(String key, I_PropertyChangeListener l) {
      if (key == null) {
         throw new IllegalArgumentException("removePropertyChangeListener got illegal arguments key=" + key);
      }
      Object o = changeListenerMap.get(key);
      if (o != null) {
         Set listenerSet = (Set)o;
         if (l != null) {
            listenerSet.remove(l);
         }
         else {
            listenerSet.clear();
         }
         if ( listenerSet.size() < 1 ) {
            changeListenerMap.remove(key);
         }
      }
   }

   public String toXml() {
      return toXml((String)null);
   }
 
    /**
   * Dump all properties to xml representation.
   */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(5000);
      if (extraOffset == null) extraOffset = "";
      String offset = "\n " + extraOffset;

      sb.append(offset).append("<Property>");
      for (Enumeration e = properties.propertyNames(); e.hasMoreElements();) {
        String key = (String) e.nextElement();
        String value = (String)properties.get(key);
        // Avoid illegal characters in tag name:
        if (key.indexOf("[") != -1) 
          continue; // Will be dumped later in <arrays> section
        key = org.xmlBlaster.util.ReplaceVariable.replaceAll(key, "/", ".");
        sb.append(offset).append("  <");
        sb.append(key).append(">");
        sb.append(org.xmlBlaster.util.ReplaceVariable.replaceAll(value, "<", "&lt;"));
        sb.append("</").append(key).append(">");
      }
      if (propMap != null) {
         Iterator it = propMap.keySet().iterator();
         sb.append(offset).append("  <arrays>");
         while (it.hasNext()) {
            String key = (String) it.next();
            Map map = get(key, (Map)null);
            key = org.xmlBlaster.util.ReplaceVariable.replaceAll(key, "/", ".");
            Iterator iter = map.keySet().iterator();
            while (iter.hasNext()) {
               String arg = (String) iter.next();
               sb.append(offset).append("    <").append(key).append(" index='").append(arg).append("'>");
               sb.append(org.xmlBlaster.util.ReplaceVariable.replaceAll((String)map.get(arg), "<", "&lt;"));
               sb.append("</").append(key).append(">");
            }
         }
         sb.append(offset).append("  </arrays>");
      }
      sb.append(offset).append("</Property>");
      return sb.toString();
   }



/**
* For testing only
* <p />
* <pre>
* java -Djava.compiler= org.xmlBlaster.util.property.Property -Persistence.Driver myDriver -isCool true -xml "<hello>world</hello>"
* java -Djava.compiler= org.xmlBlaster.util.property.Property -dump true -uu "You are the user \${user.name}" -double "\${uu} using \${java.vm.name}"
* java -Djava.compiler= org.xmlBlaster.util.property.Property -NameList Josua,David,Ken,Abel
* java -Djava.compiler= org.xmlBlaster.util.property.Property -urlVariant true -url http://localhost/xy.properties
* java -Djava.compiler= org.xmlBlaster.util.property.Property -hashVariant true
* java -Djava.compiler= org.xmlBlaster.util.property.Property -dump true -val[A] aaaa -val[B] bbbb -val[C][1] cccc -val[C][2] c2c2
* </pre>
*/
public static void main(String args[]) {
  try {
      boolean dump = Args.getArg(args, "-dump", false);
      if (dump) {
         Property props = new Property("jutils-test.properties", true, args, true); // initialize
         System.out.println(props.toXml());
      }
   } catch (Exception e) {
      System.err.println(e.toString());
   }

   try {
     boolean testClasspathProperties = Args.getArg(args, "-testClasspathProperties", false);
     if (testClasspathProperties) {
        Property props = new Property("jutils-test.properties", true, args, true); // initialize
        System.out.println("data=" + props.get("data", "ERROR"));
        props.saveProps();
     }
   } catch (Exception e) {
     System.err.println(e.toString());
   }

   try {
     boolean testSetArray = Args.getArg(args, "-testSetArray", false);
     if (testSetArray) {
        Property props = new Property(null, true, args, true); // initialize
        props.set("Array[a][b]", "arrayvalue");
        System.out.println(props.toXml());
        System.exit(0);
     }
   } catch (Exception e) {
     System.err.println(e.toString());
   }

   try {
     boolean urlVariant = Args.getArg(args, "-urlVariant", false);
     if (urlVariant) {
       {
         System.out.println("*** Test 1");
         Property props = new Property(null, true, (java.applet.Applet)null, true); // initialize
         System.out.println("TestVariable=" + props.get("TestVariable", "ERROR"));
         System.out.println("java.home=" + props.get("java.home", "ERROR"));
       }
       {
         System.out.println("*** Test 2");
         String url = Args.getArg(args, "-url", "http://localhost/xy.properties");
         Property props = new Property(url, true, (java.applet.Applet)null, true); // initialize
         System.out.println("TestVariable=" + props.get("TestVariable", "ERROR"));
         System.out.println("java.home=" + props.get("java.home", "ERROR"));
       }
       System.exit(0);
     }
   } catch (XmlBlasterException e) {
     //System.err.println(e.toXml(true));
     System.err.println(e.toString());
     System.exit(0);
   }

   try {
     boolean hashVariant = Args.getArg(args, "-hashVariant", false);
     if (hashVariant) {
       {
         System.out.println("*** Test 1");
         Properties extra = new Properties();
         extra.put("city", "Auckland");
         extra.put("country", "NewZealand");
         Property props = new Property(null, true, extra, true); // initialize
         System.out.println("city=" + props.get("city", "ERROR"));
         System.out.println("country=" + props.get("country", "ERROR"));
         System.out.println("TestVariable=" + props.get("TestVariable", "shouldBeUndef"));
         System.out.println("java.home=" + props.get("java.home", "ERROR"));
       }
       System.exit(0);
     }
   } catch (XmlBlasterException e) {
     //System.err.println(e.toXml(true));
     System.err.println(e.toString());
     System.exit(0);
   }
}



   /**
   * The default constructor. Deeded for some IDEs to work properly.
   * Creation date: (07.11.2000 20:14:53)
   */
   public Property() {
     properties = new Properties();
   }



   /**
    * Saves the property to a file; this also will include the system properties!
    * @author M.Winkler, doubleslash NetBusiness GmbH, Friedrichshafen
    * @exception java.io.IOException Die Beschreibung der Ausnahmebedingung.
    */
   public void saveProps() throws java.io.IOException {

      // check wether or not we can save
      FileInfo info = findGivenFile(propertyFileName);
      if (info.isLocalDisk())
         saveProps(info.getFullPath());
      else
         saveProps(propertyFileName);
   }



/**
 * Saves the property to a file; this also will include the system properties!
 * @author M.Winkler, doubleslash NetBusiness GmbH, Friedrichshafen
 * @exception java.io.IOException Die Beschreibung der Ausnahmebedingung.
 */
public void saveProps(String fileName) throws java.io.IOException {

   // check wether or not we can save
   File saveFile = new File(fileName);
   System.out.println("Property: Saving property to file '" + saveFile.getAbsolutePath() + "'");

   // retrieve property
   Properties properties = getProperties();

   // try to save to the original loading place
   OutputStream out = new FileOutputStream(saveFile);
   properties.store(out, "JUtils property file, including system properties");
   out.close();
   System.out.println("Property: Done.");

}

   public class FileInfo
   {
      String path;     // Without fileName
      String fileName; // file name without path
      String fullPath; // e.g. from URL
      InputStream in;  //

      FileInfo(String fileName) {
         this.fileName = fileName;
      }

      public String getFullPath() {
         if (fullPath != null)
            return fullPath;
         if (path == null && fileName == null)
            return null;
         if (path == null)
            return fileName;
         if (fileName == null)
            return path;
         return FileLocator.concatPath(path, fileName);
      }

      public boolean isLocalDisk() {
         if (getFullPath() != null && getFullPath().indexOf("!") < 0)
            return true;
         return false;
      }

      /**
       * You need to call closeInputStream() when finished.
       */
      public InputStream getInputStream() throws XmlBlasterException {
         try {
            if (in != null)
               return in;  // e.g. from URL or Classloader
            if (getFullPath() != null) {
               if (getFullPath().indexOf("!") >= 0) {
                  ClassLoader loader = Property.class.getClassLoader();
                  in = loader.getResourceAsStream(getFullPath());
               }
               else {
                  File file = new File(getFullPath());
                  in = new FileInputStream(file);
               }
            }
            return in;
         }
         catch (IOException e) {
            throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION, "Property.FileInfo", e.toString());
         }
      }

      public void closeInputStream() {
         try {
            if (in != null) {
               in.close();
               in = null;
            }
         }
         catch (IOException e) {
         }
      }
   } // class FileInfo
} // class Property
