package org.xmlBlaster.util;

import java.io.IOException;

@FunctionalInterface
public interface I_ThrowingConsumer<T> {
    void accept(T t) throws IOException, XmlBlasterException;
}