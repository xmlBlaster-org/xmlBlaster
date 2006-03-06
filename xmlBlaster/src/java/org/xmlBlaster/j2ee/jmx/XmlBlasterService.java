/*
 * Copyright (c) 2002,2003 Peter Antman, Teknik i Media  <peter.antman@tim.se>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package org.xmlBlaster.j2ee.jmx;
import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.net.URL;
import java.rmi.RMISecurityManager;

import javax.naming.InitialContext;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.property.Property;
import org.xmlBlaster.util.property.Property.FileInfo;
import org.xmlBlaster.j2ee.util.JacorbUtil;
import org.xmlBlaster.j2ee.util.GlobalUtil;
import org.jutils.JUtilsException;
import java.util.logging.Logger;
import java.util.logging.Level;
/**
 * XmlBlaster for embedded use in a JMX server.
 *
 * <p>You may use this MBean to start one or more XmlBlaster instances in a
 JMX container. It has, however, only been tested with the JBoss 3.0 server. To start it in JBoss copy the xmlBlaster.sar archive into deploy. If you need to change the settings either edit the enbedded xmlBlaster.properties file or change the name of the property file in META-INF/jboss-service.xml and make it availabl1e in the XmlBlaster search path or embed it in the sar.</p>
<p>To get better control ower the XmlBlaster setup process, the xmlBlaster.jar
that's embedded in the sar has had its xmlBlaster.properties and xmlBlasterPlugins.xml files removed. It's recomended to do this also in any xmlBlaster.jar that is placed in the global classpath of JBoss, otherwise it might screw up client.</p>

<h3>Requirements</h3>
<p>.You need to copy the file concurrent.jar from xmlBlaster/lib to the system lib directory of JBoss, overwriting the older version distributed with JBoss.</p><p>When using the RMIDriver JBoss must be run with a security policy file specified, eg, sh run.sh -Djava.security.policy=../server/default/conf/server.policy.</p>
<h3>Local clients</h3>
<p>It is possible to use local client (in vm) clients by specifying a jndiName
where a @link { org.xmlBlaster.j2ee.util.GlobalLookup} will be bound. If a client in the same VM looks this object upp through jndi, it will have access to the server engine.Global, and it is therefore possible to use the in vm client protocol.</p>


 *
 *
 * @author Peter Antman
 * @version $Revision: 1.9 $ $Date$
 */

public class XmlBlasterService implements XmlBlasterServiceMBean {
   private static final String ME = "XmlBlasterService";
   private EmbeddedXmlBlaster blaster = null;
   private Global glob;
   private String propFile;
   private static Logger log = Logger.getLogger(XmlBlasterService.class.getName());
   private String jndiName;
   private Properties args = new Properties();
   private GlobalUtil globalUtil;

   public XmlBlasterService() {
   }



   /**
    * Set the name of a propertyfile to read settings from.
    *
    * <p>if this option is set, all properties specifyed in it will <i>overwrite</i> any properties sett on this MBean, since the file will be loaded last.</p>
    * <p>The context classloader will be searched first, then normal XmlBlaster search algoritm will be used.</p>
    */
   public void setPropertyFileName(String fileName) {
      propFile = fileName;
   }

   public String getPropertyFileName() {
      return propFile;
   }

   /**
    * Set the bootstrap port the instance should run at.
    * @param port Default bootstrapPort is 3412
    */
   public void setPort(String port) {
      args.setProperty("bootstrapPort", port);
   }

   public String getPort() {
      return args.getProperty("bootstrapPort");
   }

   /**
    * Set a JNDI name where a GlobalUtil will be bound.
    */
   public void setJNDIName(String jndiName) {
      this.jndiName = jndiName;
   }

   public String getJNDIName() {
      return jndiName;
   }
   
   /**
    * Start the embedded XmlBlaster.
    */
   public void start() throws Exception {
      globalUtil = new GlobalUtil();
      glob = globalUtil.newGlobal(propFile,args );

      loadJacorbProperties();
      globalUtil.setupSecurityManager(glob);


      log.info("Starting XmlBlasterService");

      blaster = EmbeddedXmlBlaster.startXmlBlaster(glob);

      if ( jndiName != null) {
         bind( blaster.getMain().getGlobal() );
      } // end of if ()
   }
   
   public void stop() throws Exception {
      log.info("Stopping XmlBlaster service");
      if (blaster != null ) {
         EmbeddedXmlBlaster.stopXmlBlaster(blaster);
      } // end of if ()
      if ( jndiName != null) {
         new InitialContext().unbind(jndiName);
      } // end of if ()
      
   }

   public String dumpProperties() {
      if ( glob == null) {
         return "";
      } // end of if ()
      
      return glob.getProperty().toXml();
   }

   /**
    * Bind a GlobalLookup into jndi.
    */
   private void bind(org.xmlBlaster.engine.ServerScope engineGlobal) throws Exception{
      if ( jndiName == null) {
         return;
      } // end of if ()
      
      // Do we have JNDI at all?
      Context ctx = null;
      try {
         ctx =  new InitialContext();
      } catch (NamingException e) {
         throw new IllegalStateException("No NamingContext available, trying to run with a jndiName in a server withouth jndi is not valid: "+e);
      } // end of try-catch
      
      GlobalUtil gu = new GlobalUtil(engineGlobal);
      bind(ctx,jndiName,gu);

   }


   /**
    * Jacorb is not capable of finding its jacorb.properties in the
    * context classpath (actually it uses the system classloader.
    * Remember that jacorb.properties is in xmlBlaster.jar.
    */
   private void loadJacorbProperties() throws Exception {
      JacorbUtil.loadJacorbProperties("jacorb.properties",glob);
   }


   public void  bind(Context ctx, String name, Object val) throws NamingException
   {
      // Bind val to name in ctx, and make sure that all intermediate contexts exist
      Name n = ctx.getNameParser("").parse(name);
      while (n.size() > 1)
      {
         String ctxName = n.get(0);
         try
         {
            ctx = (Context)ctx.lookup(ctxName);
         } catch (NameNotFoundException e)
         {
            ctx = ctx.createSubcontext(ctxName);
         }
         n = n.getSuffix(1);
      }

      ctx.bind(n.get(0), val);
   }
   
}

