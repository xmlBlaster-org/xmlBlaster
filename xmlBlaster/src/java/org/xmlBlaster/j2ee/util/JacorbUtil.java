/*
 * Copyright (c) 2003 Peter Antman, Teknik i Media  <peter.antman@tim.se>
 *
 * $Id$
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
package org.xmlBlaster.j2ee.util;
import java.util.Properties;
import java.io.InputStream;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.property.Property;
/**
 * Helpers class to work with Jacorb in embedded J2EE environment.
 *
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>
 * @version $Revision: 1.2 $
 */

public class JacorbUtil {
   private static Logger log = Logger.getLogger(JacorbUtil.class.getName());
   private static final String ME = "JacorbUtil";
   /**
    * Load the given jacorb properties file from the context classloader and
    * put it into the given global.
    * Jacorb is not capable of finding its jacorb.properties in the
    * context classpath (actually it uses the system classloader.)
    * Remember that jacorb.properties is in xmlBlaster.jar.
    */
   public static void loadJacorbProperties(String fileName, Global glob) throws XmlBlasterException {
      if ( fileName == null) {
         throw new XmlBlasterException(glob,ErrorCode.RESOURCE_CONFIGURATION,
                                        ME,"jacorb property filename not allowed to be null");
      } // end of if ()
      
      Properties props = new Properties();
      try {
         // Read orb properties file into props
         ClassLoader cl = Thread.currentThread().getContextClassLoader();
         InputStream is = cl.getResourceAsStream(fileName);
         if (is != null) {
            props.load(is);
            // Ad to global
            Property p = glob.getProperty();
            p.addArgs2Props( props );
         } else {

            log.warning("No "+fileName+" found in context classpath");
         }
      } catch (Exception e) {
         throw new XmlBlasterException(glob,ErrorCode.RESOURCE_CONFIGURATION,
                                        ME,"could not load jacorb properties "+e);
      } // end of try-catch

   }
}// JacorbUtil
