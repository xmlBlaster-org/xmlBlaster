package org.xmlBlaster.engine.xmldb.dom;

public class DOMParseException extends Exception
{

    public String toString()
    {
        return ex != null ? "DomParseException (" + ex.toString() + ")" : super.toString();
    }

    public Exception getException()
    {
        return ex;
    }

    DOMParseException(String s)
    {
        super(s);
    }

    DOMParseException(Exception exception)
    {
        ex = exception;
        fillInStackTrace();
    }

    Exception ex;
}
