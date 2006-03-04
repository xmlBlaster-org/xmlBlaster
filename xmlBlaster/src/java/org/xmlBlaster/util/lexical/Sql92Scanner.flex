/*------------------------------------------------------------------------------
Name:      Sql92Scanner.flex
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/*
  To manually create it (without ant): > jflex Sql92Scanner.flex
  and then after the CUP parser has been created 
  > javac Sql92Scanner.java
*/
   
/* ----------------------------- Usercode Section ---------------------------- */
   
package org.xmlBlaster.util.lexical;

import java_cup.runtime.*;
import java_cup.sym;


import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.Global;
import java.util.logging.Logger;
import java.util.logging.Level;

      
%%
   
/* -----------------Options and Declarations Section----------------- */
   
/* 
   The name of the class JFlex will create will be Sql92Scanner.
   Will write the code to the file Sql92Scanner.java. 
*/
%class Sql92Scanner

/*
  The current line number can be accessed with the variable yyline
  and the current column number with the variable yycolumn.
*/
%line
%column
    
/* 
   Will switch to a CUP compatibility mode to interface with a CUP
   generated parser.
*/
%cup
   
/*
  Declarations
   
  Code between %{ and %}, both of which must be at the beginning of a
  line, will be copied letter to letter into the lexer class source.
  Here you declare member variables and functions that are used inside
  scanner actions.  
*/
%{  

    /**
     * This must be filled to be determined
     */
    private final static String ME = "Sql92Scanner";
    
    /** The client properties on which to do the query. These are set on each query */
    private java.util.Map properties;

    /** A placeholder for the string constants */
    private StringBuffer stringBf = new StringBuffer();

    private Global global;
    private static Logger log = Logger.getLogger(Sql92Scanner.class.getName();
    
    /** A buffer used only for logging purposes (active only when TRACE is on) */
    private StringBuffer logBuffer = new StringBuffer();

    /**
     * This is the constructor which we need to use in xmlBlaster.
     */
     public Sql92Scanner(Global global) {
        super();
        this.global = global;
        if (log.isLoggable(Level.FINER)) log.finer("Constructor");
     }

     /**
      * Sets the client properties for a querty. Note that the code is not threadsafe. 
      * The invoker must ensure threadsafety.
      */
     public void setClientPropertyMap(java.util.Map clientProperties) {
        this.properties = clientProperties;
     }

    /* To create a new java_cup.runtime.Symbol with information about
       the current token, the token will have no value in this
       case. */
    private Symbol symbol(int type) {
        return new Symbol(type, yyline, yycolumn);
    }
    
    /* Also creates a new java_cup.runtime.Symbol with information
       about the current token, but this object has a value. */
    private Symbol symbol(int type, Object value) {
        return new Symbol(type, yyline, yycolumn, value);
    }

    /**
     * Helper method to retrieve a particular client property. If the property has not
     * been found in the map, then a NULL_OBJECT symbol is returned. The type of this
     * object is not determined here. It is handled syntactically in the parser.
     */
    private Symbol symbolFromProperty(String propertyName) {
       ClientProperty clientProperty = null;
       if (properties != null) clientProperty = (ClientProperty)properties.get(propertyName);
       if (clientProperty == null) {
          return symbol(Sql92Symbols.NULL_OBJECT, null);
       }   
       String str = clientProperty.getStringValue();
       if (log.isLoggable(Level.FINE)) logBuffer.append(propertyName).append("(").append(str).append(")");
       if (clientProperty.getType() == null) { // then it is a string
          return symbol(Sql92Symbols.STRING, str);
       }
       return symbol(Sql92Symbols.NUMBER, new Double(str));
    }

%}
   

/*
  Macro Declarations
  
  These declarations are regular expressions that will be used latter
  in the Lexical Rules Section.  
*/
   
/* A line terminator is a \r (carriage return), \n (line feed), or
   \r\n. */
LineTerminator = \r|\n|\r\n
   
/* White space is a line terminator, space, tab, or line feed. */
WhiteSpace     = {LineTerminator} | [ \t\f]
   
/* A literal integer is is a number beginning with a number between
   one and nine followed by zero or more numbers between zero and nine
   or just a zero.  */
/* number_literal = 0 | [1-9][0-9] |.*  */
   
/* A identifier integer is a word beginning a letter between A and
   Z, a and z, or an underscore followed by zero or more letters
   between A and Z, a and z, zero and nine, or an underscore. */
propName = [A-Za-z_][A-Za-z_0-9]*

StringCharacter = [^\r\n\']

number_literal = ({FLit1}|{FLit2}|{FLit3}) {Exponent}?
FLit1    = [0-9]+ \. [0-9]*
FLit2    = \. [0-9]+
FLit3    = [0-9]+
Exponent = [eE] [+-]? [0-9]+



%state CONST_STRING
   
%%
/* ------------------------Lexical Rules Section---------------------- */

   
/*
   This section contains regular expressions and actions, i.e. Java
   code, that will be executed when the scanner matches the associated
   regular expression. */
   
   /* YYINITIAL is the state at which the lexer begins scanning.  So
   these regular expressions will only be matched if the scanner is in
   the start state YYINITIAL. */
   
<YYINITIAL> {
   
    /* Print the token found that was declared in the class sym and then
       return it. */
    "+"                { if (log.isLoggable(Level.FINE)) logBuffer.append(" + ");  return symbol(Sql92Symbols.PLUS); }
    "-"                { if (log.isLoggable(Level.FINE)) logBuffer.append(" - ");  return symbol(Sql92Symbols.MINUS); }
    "*"                { if (log.isLoggable(Level.FINE)) logBuffer.append(" * ");  return symbol(Sql92Symbols.TIMES); }
    "/"                { if (log.isLoggable(Level.FINE)) logBuffer.append(" / ");  return symbol(Sql92Symbols.DIV); }
    "("                { if (log.isLoggable(Level.FINE)) logBuffer.append(" ( ");  return symbol(Sql92Symbols.L_BRACKET); }
    ")"                { if (log.isLoggable(Level.FINE)) logBuffer.append(" ) ");  return symbol(Sql92Symbols.R_BRACKET); }
    ","                { if (log.isLoggable(Level.FINE)) logBuffer.append(" , ");  return symbol(Sql92Symbols.COMMA); }
    "AND"              { if (log.isLoggable(Level.FINE)) logBuffer.append(" && "); return symbol(Sql92Symbols.AND); }
    "OR"               { if (log.isLoggable(Level.FINE)) logBuffer.append(" || "); return symbol(Sql92Symbols.OR); }
    "="                { if (log.isLoggable(Level.FINE)) logBuffer.append(" = ");  return symbol(Sql92Symbols.EQUAL); }
    "<>"               { if (log.isLoggable(Level.FINE)) logBuffer.append(" != ");  return symbol(Sql92Symbols.DIFF); }
    "!="               { if (log.isLoggable(Level.FINE)) logBuffer.append(" != ");  return symbol(Sql92Symbols.DIFF); }
    "^="               { if (log.isLoggable(Level.FINE)) logBuffer.append(" != ");  return symbol(Sql92Symbols.DIFF); }
    ">"                { if (log.isLoggable(Level.FINE)) logBuffer.append(" > ");  return symbol(Sql92Symbols.GT); }
    "<"                { if (log.isLoggable(Level.FINE)) logBuffer.append(" < ");  return symbol(Sql92Symbols.LT); }
    "=>"               { if (log.isLoggable(Level.FINE)) logBuffer.append(" => "); return symbol(Sql92Symbols.GET); }
    "<="               { if (log.isLoggable(Level.FINE)) logBuffer.append(" <= "); return symbol(Sql92Symbols.LET); }
    "NOT"              { if (log.isLoggable(Level.FINE)) logBuffer.append(" ! "); return symbol(Sql92Symbols.NOT); }
    "BETWEEN"          { if (log.isLoggable(Level.FINE)) logBuffer.append(" BETWEEN "); return symbol(Sql92Symbols.BETWEEN); }
    "IN"               { if (log.isLoggable(Level.FINE)) logBuffer.append(" IN "); return symbol(Sql92Symbols.IN); }
    "IS"               { if (log.isLoggable(Level.FINE)) logBuffer.append(" IS "); return symbol(Sql92Symbols.IS); }
    "LIKE"             { if (log.isLoggable(Level.FINE)) logBuffer.append(" LIKE "); return symbol(Sql92Symbols.LIKE); }
    "ESCAPE"           { if (log.isLoggable(Level.FINE)) logBuffer.append(" ESCAPE "); return symbol(Sql92Symbols.ESCAPE); }
    "NULL"             { if (log.isLoggable(Level.FINE)) logBuffer.append(" NULL "); return symbol(Sql92Symbols.NULL); }
    "REGEX"            { if (log.isLoggable(Level.FINE)) logBuffer.append(" NULL "); return symbol(Sql92Symbols.REGEX); }

/*
    "["                { if (log.isLoggable(Level.FINE)) logBuffer.append(" ( ");  return symbol(Sql92Symbols.L_SBRACKET); }
    "]"                { if (log.isLoggable(Level.FINE)) logBuffer.append(" ) ");  return symbol(Sql92Symbols.R_SBRACKET); }
    "{"                { if (log.isLoggable(Level.FINE)) logBuffer.append(" ( ");  return symbol(Sql92Symbols.L_FBRACKET); }
    "}"                { if (log.isLoggable(Level.FINE)) logBuffer.append(" ) ");  return symbol(Sql92Symbols.R_FBRACKET); }
 */

   
    /* If an integer is found print it out, return the token NUMBER
       that represents an integer and the value of the integer that is
       held in the string yytext which will get turned into an integer
       before returning */
    {number_literal}  { 
                         if (log.isLoggable(Level.FINE)) logBuffer.append(yytext());
                         return symbol(Sql92Symbols.NUMBER, new Double(yytext())); 
                      }
   

    \'                { yybegin(CONST_STRING); this.stringBf.setLength(0); }


    /* If an identifier is found print it out, return the token ID
       that represents an identifier and the default value one that is
       given to all identifiers. */
    {propName}       { 
                        return symbolFromProperty(yytext());
                     }
   
    /* Don't do anything if whitespace is found */
    {WhiteSpace}       { /* just skip what was found, do nothing */ }   

    ";"                { }
   
}

<CONST_STRING>
{
   \'      { 
              yybegin(YYINITIAL);
              String tmp = this.stringBf.toString();
              if (log.isLoggable(Level.FINE)) logBuffer.append(tmp).append("(string litteral)");
              return symbol(Sql92Symbols.STRING, tmp); 
           }

   {StringCharacter}+      { this.stringBf.append( yytext() ); }

}


/* No token was found for the input so through an error.  Print out an
   Illegal character message with the illegal character that was found. */
[^]                    { throw new Error(logBuffer.toString() + " Illegal character <"+yytext()+">"); }
