<?xml version='1.0' encoding='iso-8859-1' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
<xsl:output method='html' version='3.0' encoding='iso-8859-1'/>

<!--

http://localhost:9999/mbean?objectname=org.xmlBlaster:contribClass=contrib,*&attributes=true&operations=false&notifications=false&constructors=false


<Server pattern="org.xmlBlaster:contribClass=contrib,*">


<MBean classname="org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin" description="Manageable Bean" objectname="org.xmlBlaster:nodeClass=node,node="replXbl",contribClass=contrib,contrib="replication"">
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Replications" strinit="true" type="java.lang.String" value="aicm,ndb"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Slaves" strinit="true" type="java.lang.String" value="client/ReplWriter-DEE_V_01-AIS/1,client/ReplWriter-NDB-AIS/1"/>
</MBean>
<MBean classname="org.xmlBlaster.contrib.replication.ReplSlave" description="Manageable Bean" objectname="org.xmlBlaster:nodeClass=node,node="replXbl",contribClass=contrib,contrib="replication/client/ReplWriter-DEE_V_01-AIS/1"">
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Active" strinit="true" type="boolean" value="true"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Connected" strinit="true" type="boolean" value="false"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="MaxReplKey" strinit="true" type="long" value="4167"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="MinReplKey" strinit="true" type="long" value="0"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="QueueEntries" strinit="true" type="long" value="23"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="true" name="SqlResponse" strinit="true" type="java.lang.String" value="null"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Status" strinit="true" type="java.lang.String" value="NORMAL"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Topic" strinit="true" type="java.lang.String" value="replTopic"/>
</MBean>
</Server>




Search Pattern Matching Examples for MBeans:

If the example MBeans with the following names are registered in the MBean server:

MyDomain:description=Printer,type=laser
MyDomain:description=Disk,capacity=2
DefaultDomain:description=Disk,capacity=1
DefaultDomain:description=Printer,type=ink
DefaultDomain:description=Printer,type=laser,date=1993
Socrates:description=Printer,type=laser,date=1993
Here are some examples of queries that can be performed using pattern matching:
n \u201c*:*\u201d will match all the objects of the MBean server. A null string object or
empty string (\u201c\u201d) name used as a pattern is equivalent to \u201c*:*\u201d.
n \u201c:*\u201d will match all the objects of the default domain
n \u201cMyDomain:*\u201d will match all objects in MyDomain
n \u201c??Domain:*\u201d will also match all objects in MyDomain
n \u201c*Dom*:*\u201d will match all objects in MyDomain and DefaultDomain
n \u201c*:description=Printer,type=laser,*\u201d will match the following objects:
MyDomain:description=Printer,type=laser
DefaultDomain:description=Printer,type=laser,date=1993
Socrates:description=Printer,type=laser,date=1993
n \u201c*Domain:description=Printer,*\u201d will match the following objects:
MyDomain:description=Printer,type=laser
DefaultDomain:description=Printer,type=ink
DefaultDomain:description=Printer,type=laser,date=1993t

-->



<!--
   The example document looks like:

<MBean classname="org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin" description="Manageable Bean" objectname="org.xmlBlaster:nodeClass=node,node="replXbl",contribClass=contrib,contrib="replication"">
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Replications" strinit="true" type="java.lang.String" value="aicm,ndb"/>
<Attribute availability="RO" description="Attribute exposed for management" isnull="false" name="Slaves" strinit="true" type="java.lang.String" value="client/ReplWriter-DEE_V_01-AIS/1,client/ReplWriter-NDB-AIS/1"/>
<Constructor description="Constructor exposed for management" name="org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin"/>
	<Operation description="Operation exposed for management" impact="unknown" name="initiateReplication" return="java.lang.String">
<Parameter description="Operation's parameter n. 1" id="0" name="param1" strinit="true" type="java.lang.String"/>
<Parameter description="Operation's parameter n. 2" id="1" name="param2" strinit="true" type="java.lang.String"/>
</Operation>
	<Operation description="Operation exposed for management" impact="unknown" name="broadcastSql" return="void">
<Parameter description="Operation's parameter n. 1" id="0" name="param1" strinit="true" type="java.lang.String"/>
<Parameter description="Operation's parameter n. 2" id="1" name="param2" strinit="true" type="java.lang.String"/>
<Parameter description="Operation's parameter n. 3" id="2" name="param3" strinit="true" type="boolean"/>
</Operation>
	<Operation description="Operation exposed for management" impact="unknown" name="recreateTriggers" return="java.lang.String">
<Parameter description="Operation's parameter n. 1" id="0" name="param1" strinit="true" type="java.lang.String"/>
</Operation>
</MBean>

url to go to:
concat('invoke?objectname=', $objectname, '&amp;operation=initiateReplication&amp;type0=java.lang.String&amp;value0=','XXX', '&amp;type1=java.lang.String&amp;value1=', 'YYY')


http://localhost:9999/mbean?objectname=org.xmlBlaster:nodeClass=node,node=%22replXbl%22,contribClass=contrib,contrib=%22replication%22&template=initiateReplication

<MBean classname="org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin" description="Manageable Bean" objectname="org.xmlBlaster:nodeClass=node,node="replXbl",contribClass=contrib,contrib="replication""/>

http://localhost:9999/mbean?objectname=org.xmlBlaster:contribClass=contrib,contrib=%22replication%22,*&amp;template=initiateReplication
-->

<xsl:include href="customize.xsl"/>

<xsl:template name="replaceString">
   <xsl:param name="content" />
   <xsl:choose>
     <xsl:when test="contains($content, ',')">
   <option>
        <xsl:value-of select="substring-before($content, ',')"/>
   </option>
        <xsl:call-template name="replaceString">
           <xsl:with-param name="content" select="substring-after($content, ',')"/>
        </xsl:call-template>
     </xsl:when>
     <xsl:otherwise>
   <option>
       <xsl:value-of select="$content"/>
   </option>
     </xsl:otherwise>
   </xsl:choose>
</xsl:template>


<!-- the variables used in this document: -->

<xsl:template match ='/'>
<html>
<head>
<title>Initiate Replication</title>
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>
<link href="styles.css" rel="stylesheet" type="text/css"/>


<script language="JavaScript" type="text/javascript">

function initiateReplication(objName) {
   var source = document.getElementById("sources").value;
   var dest = document.getElementById("destinations").value;

   <xsl:choose>
     <xsl:when test="$show.cascading = 'yes'">
   var extraSource = document.getElementById("extraSources").value;
   var extraDest = document.getElementById("extraDestinations").value;
     </xsl:when>
     <xsl:otherwise>
   var extraSource = "";
   var extraDest = "";
     </xsl:otherwise>
   </xsl:choose>
   var url = 'invoke?objectname=' + objName + '&amp;operation=initiateReplication&amp;type0=java.lang.String&amp;value0=' +
      dest + '&amp;type1=java.lang.String&amp;value1=' + source + '&amp;type2=java.lang.String&amp;value2=' + extraDest + '&amp;type3=java.lang.String&amp;value3=' + extraSource + '&amp;destination=destinationList&amp;template=result' ;
   self.location.href= url;
}

function cancel() {
   var url = '<xsl:value-of select="$destinationListUrl"/>';
   self.location.href= url;
}


</script>

</head>

<body>
  <center>
    <xsl:call-template name="header"/>
    <div class="middle">Initiate Replication<br/>
      <table width="650" align="center" class="external" summary="">

    <xsl:apply-templates/>

       </table>
     </div>
    <xsl:call-template name="footer"/>
   </center>

</body>
</html>
</xsl:template>

<!-- 
<xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text>
-->

<xsl:template match='MBean'>
        <tr> 
          <td>
            <table class="inner" width="350" align="center" summary="" border="1">
              <tr>
                <td colspan="1" class="normal" title="The Source of the Replication. (replication.prefix)">Source</td>
                <td colspan="1" class="value">
                    <select class="values" id="sources" name="sources" size="1">
   <xsl:call-template name="replaceString">
     <xsl:with-param name="content" select="Attribute[@name='Replications']/@value"/>
   </xsl:call-template>
                    </select>
                 </td>
              </tr>
         
              <tr>
                <td colspan="1" class="normal" title="The destination of the Replication.">Destination</td>
                <td colspan="1" class="value">
                  <select class="values" id="destinations" name="destinations" size="1">
   <xsl:call-template name="replaceString">
     <xsl:with-param name="content" select="Attribute[@name='Slaves']/@value"/>
   </xsl:call-template>
                  </select>
                </td>
              </tr>



   <xsl:choose>
     <xsl:when test="$show.cascading = 'yes'">
              <tr>
                <td colspan="2" class="normal"><xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text></td>
              </tr>
              <tr>
                <td colspan="2" class="normal" title="Only if you want to cascade initiation of several replications automatically">Cascaded Replication or back-replication (<b>optional</b>):</td>
              </tr>

              <tr>
                <td colspan="1" class="normal" title="Optional source of cascaded replication">Source</td>
                <td colspan="1" class="value">
                  <select class="values" id="extraSources" name="extraSources" size="1">
<!--                     <option><xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text></option> -->
                     <option></option>
   <xsl:call-template name="replaceString">
     <xsl:with-param name="content" select="Attribute[@name='Replications']/@value"/>
   </xsl:call-template>
                  </select>
                 </td>
              </tr>
              <tr>
                <td colspan="1" class="normal" title="Optional destination of cascaded replication">Destination</td>
                <td colspan="1" class="value">
                  <select class="values" id="extraDestinations" name="extraDestinations" size="1">
<!--                     <option><xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text></option> -->
                     <option></option>
   <xsl:call-template name="replaceString">
     <xsl:with-param name="content" select="Attribute[@name='Slaves']/@value"/>
   </xsl:call-template>
                  </select>
                </td>
              </tr>
     </xsl:when>
     <xsl:otherwise>
     </xsl:otherwise>
   </xsl:choose>
	      <tr>
                <td colspan="1" align="center"><button class="small" title="Click to return to the replication list" onClick="cancel()">Cancel</button></td>

                <td colspan="1" align="center">
   <xsl:element name="button">
      <xsl:attribute name="class">small</xsl:attribute>
      <xsl:attribute name="Title">Click to start (initiate) the choosen replication combination</xsl:attribute>
      <xsl:attribute name="onClick">initiateReplication('<xsl:value-of select="@objectname"/>')</xsl:attribute>
      Initiate Repl.
   </xsl:element>
                </td>
              </tr>
              </table>
            </td>
          </tr>

</xsl:template>      

<!-- end body -->

</xsl:stylesheet>
