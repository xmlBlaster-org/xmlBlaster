/*------------------------------------------------------------------------------
Name:      I_Connection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.protocol.http.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * I_Connection
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public interface I_Connection {
   public InputStream getInputStream() throws IOException;
   public OutputStream getOutputStream() throws IOException;
   public String getHeaderField(String key);
   public void setRequestProperty(String key, String val);
   public void setPostMethod();
   public void setDoInput(boolean doInput);
   public void setDoOutput(boolean doInput);
   public void setUseCaches(boolean useCaches);
   public void connect() throws IOException;
   
}
