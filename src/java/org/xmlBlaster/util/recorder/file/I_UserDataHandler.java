/*------------------------------------------------------------------------------
Name:      I_UserDataHandler.java
Project:   xmlBlaster.org
Comment:   Interface for FileRecorder
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder.file;

import org.xmlBlaster.util.XmlBlasterException;

import java.io.RandomAccessFile;
import java.io.IOException;

/**
 * FileIO calls back user supplied implementation of its data dump/recover through this interface. 
 */
public interface I_UserDataHandler
{
   /** Write your data to RandomAccessFile */
   public void writeData(final RandomAccessFile ra, final Object userData) throws IOException, XmlBlasterException;

   /** Access your data from RandomAccessFile again */
   public Object readData(final RandomAccessFile ra) throws IOException, XmlBlasterException;
}
