<?xml version='1.0' encoding='iso-8859-1' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
<xsl:output method='html' version='3.0' encoding='iso-8859-1'/>

<!--


http://localhost:9999/mbean?objectname=org.xmlBlaster:contribClass=contrib,*&attributes=true&operations=false&notifications=false&constructors=false&template=sqlStatements

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

-->


<xsl:include href="customize.xsl"/>

<xsl:template name="replaceString">
   <xsl:param name="content" />
   <xsl:choose>
     <xsl:when test="contains($content, ',')">
   <xsl:element name="option">
       <xsl:attribute name="value"><xsl:value-of select="substring-before($content, ',')"/></xsl:attribute>
       <xsl:value-of select="substring-before($content, ',')"/>
   </xsl:element>
        <xsl:call-template name="replaceString">
           <xsl:with-param name="content" select="substring-after($content, ',')"/>
        </xsl:call-template>
     </xsl:when>
     <xsl:otherwise>
   <xsl:element name="option">
       <xsl:attribute name="value"><xsl:value-of select="$content"/></xsl:attribute>
       <xsl:value-of select="$content"/>
   </xsl:element>
     </xsl:otherwise>
   </xsl:choose>
</xsl:template>


<!-- the variables used in this document: -->

<xsl:template match ='/'>
<html>
<head>											     
<title>SQL Statements</title>
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>

<link href="styles.css" rel="stylesheet" type="text/css"/>


<script language="JavaScript" type="text/javascript">

function gotoSubmit() {
   var repl = document.getElementById("replication").value;
   var sql = document.getElementById("sqlTxt").value;
   var objName = '<xsl:value-of select="//MBean[@classname='org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin']/@objectname"/>';
   var url = 'invoke?objectname=' + objName + '&amp;operation=broadcastSql&amp;type0=java.lang.String&amp;value0=' +
      repl + '&amp;type1=java.lang.String&amp;value1=' + sql + '&amp;destination=sqlStatements&amp;template=result' ;
   self.location.href= url;
}

function back() {
   var url = '<xsl:value-of select="$sqlStatementsUrl"/>';
   self.location.href= url;
}

</script>

</head>


<body>
  <center>
    <xsl:call-template name="header"/>
    <div class="middle">Sql Statements<br/>
      <table width="650" align="center" class="external" summary="">
        <tr>
	   <td>
            <table class="inner" width="550" align="center" summary="" border="1">
              <tr>
  	        <td class="normal" title="" colspan="4">
		  <center>Replication Source:
                    <select class="values" id="replication" name="replication" size="1">
                       <option value=""></option>
                       <xsl:call-template name="replaceString">
                         <xsl:with-param name="content" select="//MBean[@classname='org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin']/Attribute[@name='Replications']/@value"/>
                       </xsl:call-template>
                    </select>
		  </center>
	        </td>
              </tr>

              <tr>
  	        <td class="normal" title="" colspan="4"><center>SQL Statement to submit:</center></td>
              </tr>
              <tr>
  	        <td class="normal" title="" colspan="4"><center><textarea id="sqlTxt" name="sqlTxt" cols="70" rows="3"></textarea></center></td>
              </tr>

    	      <tr>
    	        <td align="center" colspan="2" class="normal"><button title="Click to go back to statement list page" class="common" onClick="back()">Cancel</button></td>
    	        <td align="center" colspan="2" class="normal"><button title="Click to submit the sql statement" class="common" onClick="gotoSubmit()">Submit</button></td>
    	      </tr>
	    </table>
	   </td>
	</tr>

      </table>
     </div>
     <xsl:call-template name="footer"/>
    </center>
</body>
</html>
</xsl:template>


<!-- end body -->

</xsl:stylesheet>
