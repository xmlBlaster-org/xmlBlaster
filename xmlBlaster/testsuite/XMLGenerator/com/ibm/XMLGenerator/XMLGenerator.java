// Decompiled by Jad v1.5.7. Copyright 1997-99 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/SiliconValley/Bridge/8617/jad.html
// Decompiler options: packimports(3) 
// Source File Name:   XMLGenerator.java

package testsuite.XMLGenerator.com.ibm.XMLGenerator;

import com.ibm.xml.framework.XMLParser;
import com.ibm.xml.parser.*;
import com.ibm.xml.parsers.NonValidatingTXDOMParser;
import com.ibm.xml.parsers.TXDOMParser;
import java.io.*;
import java.util.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

public class XMLGenerator
{

    public XMLGenerator()
    {
    }

    void addAttr(AttDef attdef, Element element)
    {
        switch(attdef.getDeclaredType())
        {
        default:
            break;

        case 1: // '\001'
            element.setAttribute(attdef.getName(), defaultCDATA + cDataCount);
            cDataCount++;
            break;

        case 9: // '\t'
        case 10: // '\n'
            int i = rand.nextInt(attdef.size());
            element.setAttribute(attdef.getName(), attdef.elementAt(i));
            break;

        case 2: // '\002'
            element.setAttribute(attdef.getName(), "a" + idCount);
            idCount++;
            break;

        case 3: // '\003'
        case 4: // '\004'
            if(!idRefNodes.contains(element))
                idRefNodes.add(element);
            element.setAttribute(attdef.getName(), "noValue");
            break;

        case 5: // '\005'
        case 6: // '\006'
            if(entUnparsed.size() < 1)
            {
                element.setAttribute(attdef.getName(), "noValue");
                return;
            }
            int j = 1;
            String s = new String();
            if(attdef.getDeclaredType() == 6)
                j = rand.nextInt(maxEntities) + 1;
            for(int k = 0; k < j; k++)
            {
                int i1 = rand.nextInt(entUnparsed.size());
                s = s + ((EntityDecl)entUnparsed.get(i1)).getNodeName() + " ";
            }

            element.setAttribute(attdef.getName(), s);
            break;

        case 7: // '\007'
            element.setAttribute(attdef.getName(), defaultNMToken);
            break;

        case 8: // '\b'
            int l = rand.nextInt(maxNMTokens) + 1;
            String s1 = new String();
            for(int j1 = 0; j1 < l; j1++)
                s1 = s1 + defaultNMToken + " ";

            element.setAttribute(attdef.getName(), s1);
            break;

        }
    }

    void addChildren(CMNode cmnode, Element element)
        throws Exception
    {
        if(cmnode == null)
            return;
        if(cmnode instanceof CMLeaf)
        {
            String s = ((CMLeaf)cmnode).getName();
            if(s == "#PCDATA")
            {
                element.appendChild(dom.createTextNode("level " + currentLevel));
                if(entFile != null && entPCData.size() > 0 && rand.nextInt(entOdds) == 0)
                {
                    int i = rand.nextInt(entPCData.size());
                    element.appendChild(dom.createEntityReference((String)entPCData.get(i)));
                }
            }
            else
            {
                element.appendChild(createTag(s));
            }
        }
        else
        if(cmnode instanceof CM1op)
        {
            CM1op cm1op = (CM1op)cmnode;
            if((char)cm1op.getType() == '?')
            {
                if(maxRepeats == 0 || currentLevel >= numberLevels)
                    return;
                int j = rand.nextInt(2);
                if(j == 1)
                    addChildren(cm1op.getNode(), element);
            }
            else
            if((char)cm1op.getType() == '+')
            {
                if(maxRepeats == 0 || currentLevel >= numberLevels)
                {
                    addChildren(cm1op.getNode(), element);
                    return;
                }
                int k = rand.nextInt(maxRepeats) + 1;
                for(int i1 = 0; i1 < k; i1++)
                    addChildren(cm1op.getNode(), element);

            }
            else
            {
                if(maxRepeats == 0 || currentLevel >= numberLevels)
                    return;
                int l = rand.nextInt(maxRepeats + 1);
                for(int j1 = 0; j1 < l; j1++)
                    addChildren(cm1op.getNode(), element);

            }
        }
        else
        if(cmnode instanceof CM2op)
        {
            CM2op cm2op = (CM2op)cmnode;
            if((char)cm2op.getType() == ',')
            {
                addChildren(cm2op.getLeft(), element);
                addChildren(cm2op.getRight(), element);
            }
            else
            {
                addChildren(addOr(cm2op), element);
            }
        }
    }

    CMNode addOr(CM2op cm2op)
        throws Exception
    {
        if((char)cm2op.getType() != '|')
            return null;
        LinkedList linkedlist = new LinkedList();
        CM2op cm2op1 = cm2op;
        linkedlist.add(cm2op1.getRight());
        for(; cm2op1.getLeft() instanceof CM2op; linkedlist.add(cm2op1.getRight()))
            cm2op1 = (CM2op)cm2op1.getLeft();

        linkedlist.add(cm2op1.getLeft());
        int i = rand.nextInt(linkedlist.size());
        return (CMNode)linkedlist.get(i);
    }

    Element createTag(String s)
        throws Exception
    {
        currentLevel++;
        if(dtd.getElementDeclaration(s) == null)
            throw new Exception("Invalid DTD! -- no element by the name \"" + s + "\"");
        Element element = dom.createElement(s);
        for(Enumeration enumeration = dtd.getAttributeDeclarations(s); enumeration.hasMoreElements();)
        {
            AttDef attdef = (AttDef)enumeration.nextElement();
            switch(attdef.getDefaultType())
            {
            case 0: // '\0'
            default:
                break;

            case 2: // '\002'
                addAttr(attdef, element);
                break;

            case 1: // '\001'
                if(fixedOdds != 0 && rand.nextInt(fixedOdds) == 0)
                    element.setAttribute(attdef.getName(), attdef.getDefaultStringValue());
                break;

            case -1: 
                if(defaultOdds == 0 || rand.nextInt(defaultOdds) != 0)
                    addAttr(attdef, element);
                break;

            case 3: // '\003'
                if(impliedOdds == 0 || rand.nextInt(impliedOdds) != 0)
                    addAttr(attdef, element);
                break;

            }
        }

        CMNode cmnode = dtd.getElementDeclaration(s).getXML4JContentModel().getContentModelNode();
        addChildren(cmnode, element);
        currentLevel--;
        return element;
    }

    void fixIdRefs()
    {
        if(idCount < 1)
            return;
        while(idRefNodes.size() > 0) 
        {
            Element element = (Element)idRefNodes.removeFirst();
            for(Enumeration enumeration = dtd.getAttributeDeclarations(element.getTagName()); enumeration.hasMoreElements();)
            {
                AttDef attdef = (AttDef)enumeration.nextElement();
                Attr attr = element.getAttributeNode(attdef.getName());
                if(attr != null)
                    if(attdef.getDeclaredType() == 3)
                        attr.setValue("a" + rand.nextInt(idCount));
                    else
                    if(attdef.getDeclaredType() == 4)
                    {
                        int i = rand.nextInt(maxIdRefs) + 1;
                        String s = "";
                        for(int j = 0; j < i; j++)
                            s = s + "a" + rand.nextInt(idCount) + " ";

                        attr.setValue(s);
                    }
            }

        }

    }

    public TXDocument generateXML(String as[])
    {
        try
        {
            if(as.length < 3)
                throw new Exception("Error -- Must have at least 3 arguments!");
            dom = new TXDocument();
            dtdFile = as[0];
            outFile = as[2];
            TXDOMParser txdomparser = new TXDOMParser();
            txdomparser.parse(as[1]);
            TXDocument txdocument = (TXDocument)txdomparser.getDocument();
            dtd = txdocument.getDTD();
            currentLevel = -1;
            numberLevels = 7;
            maxRepeats = 3;
            idCount = 0;
            maxIdRefs = 3;
            idRefNodes = new LinkedList();
            maxEntities = 3;
            entUnparsed = new LinkedList();
            maxNMTokens = 3;
            entFile = null;
            entPCData = null;
            entOdds = 1;
            fixedOdds = 4;
            impliedOdds = 4;
            defaultOdds = 4;
            seed = (new Date()).getTime();
            NamedNodeMap namednodemap = dtd.getEntities();
            for(int i = 0; i < namednodemap.getLength(); i++)
            {
                EntityDecl entitydecl = (EntityDecl)namednodemap.item(i);
                if(entitydecl.getNotationName() != null)
                    entUnparsed.add(entitydecl);
            }

            parseFlags(as);
            rand = new Random(seed);
            if(entFile != null)
            {
                LineNumberReader linenumberreader = new LineNumberReader(new FileReader(entFile));
                entPCData = new LinkedList();
                String s1;
                while((s1 = linenumberreader.readLine()) != null) 
                {
                    s1 = s1.trim();
                    if(!s1.equals(""))
                        entPCData.add(s1);
                }

            }
            String s = dtd.getName();
            dom.setVersion(txdocument.getVersion());
            if(!dtdFile.equals("NONE") && !dtdFile.equals("\"NONE\""))
                dom.appendChild(dom.createDTD(s, new ExternalID(dtdFile)));
            String s2 = new String(" Created by IBM's XMLGenerator\n\n");
            s2 = s2 + "\tnumberLevels=" + numberLevels + ", maxRepeats=" + maxRepeats;
            s2 = s2 + ", Random seed=" + seed + "\n";
            s2 = s2 + "\tfixedOdds=" + fixedOdds + ", impliedOdd=" + impliedOdds;
            s2 = s2 + ", defaultOdds=" + defaultOdds + "\n";
            s2 = s2 + "\tmaxIdRefs=" + maxIdRefs + ", maxEntities=" + maxEntities;
            s2 = s2 + ", maxNMTokens=" + maxNMTokens + "\n";
            s2 = s2 + "\tentConfigFile=" + entFile + ", entOdds=" + entOdds + "\n";
            dom.appendChild(dom.createComment(s2));
            dom.appendChild(createTag(s));
            fixIdRefs();
        }
        catch(SAXException saxexception)
        {
            saxexception.printStackTrace();
            return null;
        }
        catch(IOException ioexception)
        {
            ioexception.printStackTrace();
            return null;
        }
        catch(Exception exception)
        {
            exception.printStackTrace();
            return null;
        }
        return dom;
    }

    public static void main(String args[])
    {
        if(args.length < 3)
        {
            System.err.println("Usage: java CreateXML <srcDTD> <srcXML> <destXML> [flags]");
            System.err.println("srcDTD -- name of the DTD file to be declared in destXML");
            System.err.println("\t  write \"NONE\" if no DTD is to be declared");
            System.err.println("srcXML -- a valid dummy XML file using srcDTD as its DTD");
            System.err.println("destXML -- name of the XML file to be generated");
            System.err.println("Optional flags:");
            System.err.println("    -l <Maximum number of levels in XML tree>");
            System.err.println("    -r <Maximum number of repeats of children with * or + option>");
            System.err.println("    -f <fixedOdds> (1/fixedOdds is the probability that a fixed");
            System.err.println("\tattribute will appear)");
            System.err.println("    -i <impliedOdds> (1/impliedOdds is the probability that an");
            System.err.println("\timplied attribute will not appear)");
            System.err.println("    -d <defaultOdds> (1/defaultOdds is the probability that an");
            System.err.println("\tattribute with a default value will not appear)");
            System.err.println("    -s <integer seed for random number generator>");
            System.err.println("    -id <maximum number of IDs an IDREFS attribute can reference>");
            System.err.println("    -nm <maximum number of NMTOKENs an NMTOKENS attribute can contain>");
            System.err.println("    -en <maximum number of ENTITYs an ENTITIES attribute can contain>");
            System.err.println("    -e <entConfigFile> <entOdds> (entConfigFile contains the names of");
            System.err.println("\tentities that can be placed within PCDATA, and 1/entOdds is the");
            System.err.println("\tprobability that an entity will appear with PCDATA)");
            return;
        }
        XMLGenerator xmlgenerator = new XMLGenerator();
        TXDocument txdocument = xmlgenerator.generateXML(args);
        if(txdocument == null)
            return;
        try
        {
            PrintWriter printwriter = new PrintWriter(new FileOutputStream(xmlgenerator.outFile));
            txdocument.printWithFormat(printwriter);
            printwriter.close();
        }
        catch(IOException ioexception)
        {
            ioexception.printStackTrace();
        }
    }

    void parseFlags(String as[])
    {
        byte byte0;
        for(int i = 3; i < as.length; i += byte0)
        {
            byte0 = 1;
            if(as[i].equals("-l"))
            {
                byte0 = 2;
                if(i + 1 >= as.length)
                    return;
                numberLevels = Integer.decode(as[i + 1]).intValue();
                if(numberLevels < 0)
                    numberLevels = 0;
            }
            else
            if(as[i].equals("-r"))
            {
                byte0 = 2;
                if(i + 1 >= as.length)
                    return;
                maxRepeats = Integer.decode(as[i + 1]).intValue();
                if(maxRepeats < 0)
                    maxRepeats = 0;
            }
            else
            if(as[i].equals("-f"))
            {
                byte0 = 2;
                if(i + 1 >= as.length)
                    return;
                fixedOdds = Integer.decode(as[i + 1]).intValue();
                if(fixedOdds < 0)
                    fixedOdds = 0;
            }
            else
            if(as[i].equals("-i"))
            {
                byte0 = 2;
                if(i + 1 >= as.length)
                    return;
                impliedOdds = Integer.decode(as[i + 1]).intValue();
                if(impliedOdds < 0)
                    impliedOdds = 0;
            }
            else
            if(as[i].equals("-d"))
            {
                byte0 = 2;
                if(i + 1 >= as.length)
                    return;
                defaultOdds = Integer.decode(as[i + 1]).intValue();
                if(defaultOdds < 0)
                    defaultOdds = 0;
            }
            else
            if(as[i].equals("-s"))
            {
                byte0 = 2;
                if(i + 1 >= as.length)
                    return;
                seed = Integer.decode(as[i + 1]).intValue();
            }
            else
            if(as[i].equals("-id"))
            {
                byte0 = 2;
                if(i + 1 >= as.length)
                    return;
                maxIdRefs = Integer.decode(as[i + 1]).intValue();
                if(maxIdRefs < 1)
                    maxIdRefs = 1;
            }
            else
            if(as[i].equals("-nm"))
            {
                byte0 = 2;
                if(i + 1 >= as.length)
                    return;
                maxNMTokens = Integer.decode(as[i + 1]).intValue();
                if(maxNMTokens < 1)
                    maxNMTokens = 1;
            }
            else
            if(as[i].equals("-en"))
            {
                byte0 = 2;
                if(i + 1 >= as.length)
                    return;
                maxEntities = Integer.decode(as[i + 1]).intValue();
                if(maxEntities < 1)
                    maxEntities = 1;
            }
            else
            if(as[i].equals("-e"))
            {
                byte0 = 3;
                if(i + 1 >= as.length || i + 2 >= as.length)
                    return;
                entFile = as[i + 1];
                entOdds = Integer.decode(as[i + 2]).intValue();
                if(entOdds < 1)
                    entOdds = 1;
            }
        }

    }

    static String defaultCDATA = "defaultCDATA";
    static String defaultNMToken = "4N.Mt-o_3:ken";
    TXDocument dom;
    String dtdFile;
    public String outFile;
    DTD dtd;
    Random rand;
    long seed;
    int currentLevel;
    int numberLevels;
    int maxRepeats;
    int cDataCount;
    int idCount;
    int maxIdRefs;
    LinkedList idRefNodes;
    int maxEntities;
    LinkedList entUnparsed;
    int maxNMTokens;
    String entFile;
    LinkedList entPCData;
    int entOdds;
    int fixedOdds;
    int impliedOdds;
    int defaultOdds;

}
