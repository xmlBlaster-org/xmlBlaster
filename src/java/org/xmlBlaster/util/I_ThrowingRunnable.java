package org.xmlBlaster.util;

import java.io.IOException;

@FunctionalInterface
public interface I_ThrowingRunnable {
   void run() throws IOException, XmlBlasterException;
}
