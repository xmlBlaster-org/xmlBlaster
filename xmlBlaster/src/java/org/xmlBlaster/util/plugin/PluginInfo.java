package org.xmlBlaster.util.plugin;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Enumeration;

public class PluginInfo {

   private final LogChannel log;
   private final String ME;

   /** e.g. "ProtocolPlugin" */
   private final String propertyName;
   /** e.g. "ProtocolPlugin[IOR][1.0]" */
   private final String propertyKey;

   /** e.g. "IOR" */
   private final String type;
   /** e.g. "1.0" */
   private final String version;
   /** e.g. "org.xmlBlaster.protocol.soap.SoapDriver" */
   private String className;

   /** key/values from "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100" */
   private Hashtable params;
   

   public PluginInfo( Global glob, PluginManagerBase manager, String type_, String version_ ) throws XmlBlasterException {
      log = glob.getLog("core");
      propertyName = manager.getPluginPropertyName();
      ME = "PluginInfo-"+propertyName;
      type = type_;
      version = version_;

      propertyKey = manager.createPluginPropertyKey(type, version); // "ProtocolPlugin[IOR][1.0]"
      String rawString = glob.getProperty().get(propertyKey, (String)null);// "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"

      if (rawString==null) {
         if (type != null)
            log.warn(ME, "Plugin type=" + type + " version=" + version + " not found, choosing default plugin");
         rawString = manager.getDefaultPluginName(type, version);   // "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"
      }

      parsePropertyValue(rawString);
   }

   private void parsePropertyValue(String rawString) throws XmlBlasterException {
      if (rawString==null) throw new IllegalArgumentException(ME + ".parsePropertyValue(null)");

      params = new Hashtable();
      if(rawString!=null) {
         StringTokenizer st = new StringTokenizer(rawString, ",");
         boolean first=true;
         while(st.hasMoreTokens()) {
            String tok = (String)st.nextToken();
            if (first) { // The first is always the class name
               className = tok;
               first = false;
               continue;
            }
            int pos = tok.indexOf("=");
            if (pos < 0) {
               log.info(ME, "Accepting param " + tok + " without value (missing '=')");
               params.put(tok, "");
            }
            params.put(tok.substring(0,pos), tok.substring(pos+1));
         }
      }
      else
         throw new XmlBlasterException(ME, "Missing plugin configuration for property " + propertyKey + ", please check your settings");
   }

   public String getClassName() {
      return className;
   }

   public Hashtable getParameters() {
      return params;
   }

   public String[] getParameterArr() {
      String[] arr = new String[params.size()*2];
      Enumeration e = params.keys();
      int i = 0;
      while(e.hasMoreElements()) {
         String key = (String)e.nextElement();
         arr[i++] = key;
         arr[i++] = (String)params.get(key);
      }
      return arr;
   }



}
