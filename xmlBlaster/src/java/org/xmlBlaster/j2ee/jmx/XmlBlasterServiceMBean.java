/*
 * Copyright (c) 2002 Peter Antman, Teknik i Media  <peter.antman@tim.se>
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
/**
 * MBean interface marker.
 *
 * @author Peter Antman
 * @version $Revision: 1.3 $ $Date$
 */

public interface XmlBlasterServiceMBean {
   public void setPropertyFileName(String fileName);
   public String getPropertyFileName();

   public void setPort(String port);
   public String getPort();

   public void setJNDIName(String jndiName);
   public String getJNDIName();
   
   public void start() throws Exception;
   public void stop() throws Exception;

   public String dumpProperties();
}
