/*
 * Copyright (c) 2001 Peter Antman, Teknik i Media  <peter.antman@tim.se>
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
package org.xmlBlaster.j2ee.k2;

import java.io.PrintWriter;

import javax.resource.ResourceException;

import org.jutils.log.LogChannel;
import org.jutils.log.LogDeviceConsole;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;

/**
 * BlasterLogger.java
 *
 *
 * Created: Thu Feb 15 11:38:29 2001
 *
 * @author Peter Antman
 */

public class BlasterLogger  implements org.jutils.log.LogableDevice {
    private PrintWriter logWriter = null;
    private LogChannel channel;
    private LogDeviceConsole console = null;
    private final Global glob;
    
    public BlasterLogger(Global glob) throws ResourceException {
        this.glob = glob;
        try {
            glob.getProperty().set("logConsole", "false");
            channel = Log.getLogChannel();
            channel.removeAllDevices();
        }catch(org.jutils.JUtilsException ex) {
            ResourceException re = new ResourceException("Could not instantiate logger: " + ex);
            re.setLinkedException(ex);
            throw re;

        }
    }
    
    public void setLogWriter(PrintWriter out)
        throws ResourceException {
        this.logWriter = out;
        Log.info("BlasterLogger","Setting LogWriter: " + out);
    }

    public void setLogging(boolean log) {
        // If true, set this as a logchannel
        if (log)
            channel.addLogDevice(this);
        else
            channel.removeAllDevices();
    }

    /**
     * The logging in XmlBlaster is smart, but I can not see any simple
     * way to just say: log at this level, since logging is based on an
     * order number of levels. This does currently not work
     */
    public void setLogLevel(String level) {
        
    }
    
    private String convertLevelToString(int level) {
      if ((level & LogChannel.LOG_ERROR) > 0) {
          return "ERROR";
      }
      if ((level &  LogChannel.LOG_WARN) > 0) {
          return "WARN";
      }
      if ((level &  LogChannel.LOG_INFO) > 0) {
          return "INFO";
      }
      if ((level &  LogChannel.LOG_CALL) > 0) {
          return "CALL";
      }
      if ((level &  LogChannel.LOG_TIME) > 0) {
          return "TIME";
      }
      if ((level &  LogChannel.LOG_TRACE) > 0) {
          return "TRACE";
      }
      if ((level &  LogChannel.LOG_DUMP) > 0) {
          return "DUMP";
      }
      return "UNKNOWN LEVEL" + level;
   }
    //--- LogableDevice ---
    public void log(int level, String source, String text) {
        if (logWriter != null) {
            String levelString = convertLevelToString(level);
            String logEntry = channel.formatLogData(levelString, level, source, text);

            logWriter.println(logEntry + "\n");
        } else {
            //Log to console
            console.log(level,source,text);
        }
    }
    
    
} // BlasterLogger
