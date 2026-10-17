/*
 * Distributed as part of c3p0 v.0.9.5.3
 *
 * Copyright (C) 2018 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

package com.mchange.v2.c3p0.impl;

import java.io.*;
import java.beans.*;
import java.util.*;
import java.lang.reflect.*;

import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.cfg.*;

import com.mchange.v2.csv.*;

import javax.naming.NamingException;
import java.sql.Connection;
import java.sql.SQLException;
import com.mchange.lang.ByteUtils;
import com.mchange.v1.identicator.IdentityHashCodeIdenticator;
import com.mchange.v2.encounter.EncounterCounter;
import com.mchange.v2.encounter.EncounterUtils;
import com.mchange.v2.encounter.WeakIdentityEncounterCounter;
import com.mchange.v2.lang.VersionUtils;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.log.jdk14logging.ForwardingLogger;
import com.mchange.v2.ser.SerializableUtils;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.uid.UidUtils;

public final class C3P0ImplUtils
{
    // turning this on would only test to generate long tokens
    // on 64 bit machines, but since identityHashCode() is not
    // GUARANTEED unique under 32-bit JVMs, even if in practice
    // always is, we always test to be sure we're not reusing
    // an identity token.
    private final static boolean CONDITIONAL_LONG_TOKENS = false;

    final static MLogger logger = MLog.getLogger( C3P0ImplUtils.class );

    public final static DbAuth NULL_AUTH = new DbAuth(null,null);

    public final static Object[] NOARGS = new Object[0]; 

    public final static java.util.logging.Logger PARENT_LOGGER = new ForwardingLogger( MLog.getLogger("com.mchange.v2.c3p0"), null );

    // we use a wrapped/synchronized version for Thread safety
    private final static EncounterCounter ID_TOKEN_COUNTER;

    static
    {
	if (CONDITIONAL_LONG_TOKENS)
	    {
		boolean long_tokens;
		Integer jnb = VersionUtils.jvmNumberOfBits();
		if (jnb == null)
		    long_tokens = true;
		else if (jnb.intValue() > 32)
		    long_tokens = true;
		else
		    long_tokens = false;
		
		if (long_tokens)
		    ID_TOKEN_COUNTER = createEncounterCounter();
		else
		    ID_TOKEN_COUNTER = null;
	    }
	else
	    ID_TOKEN_COUNTER = createEncounterCounter();
     }
    
    // Note that is important that EncounterCounters be based on identity hash code here,
    // since they will be used to test IdentityTokenized, whose equals methods aren't well-formed,
    // until their identity tokens are allocated, which is what we are doing here.
    //
    // We are using weak semantics, which should be fine and minimizes the possibility of unwanted
    // memory retention here. There is a hypothetical corner case, whereunder, with a single VM/ClassLoader,
    // an IdentityTokenized might be created with a given identityHashCode, then serialized or stored as a reference,
    // then closed within the VM, then another IdentityTokenized with coincidentally the same identityHashCode could
    // be allocated, then the origial referenced or deserialized, again within the same VM/ClassLoader. This
    // very, very unlikely case is not dangerous enough to justify the memory cost of strong semantics. In the
    // very unlikely event it should ever prove an issue, we can add some randomness to the within-VM/ClassLoader
    // portion of the tokens.
    private static EncounterCounter createEncounterCounter()
    { return EncounterUtils.syncWrap( EncounterUtils.createWeak( IdentityHashCodeIdenticator.INSTANCE ) ); }
    
    public final static String VMID_PROPKEY = "com.mchange.v2.c3p0.VMID";
    private final static String VMID_PFX;
    
    static
    {
        String vmid = C3P0Config.getPropsFileConfigProperty( VMID_PROPKEY );
        if (vmid == null || (vmid = vmid.trim()).equals("") || vmid.equals("AUTO"))
            VMID_PFX = UidUtils.VM_ID + '|';
        else if (vmid.equals("NONE"))
            VMID_PFX = "";
        else
            VMID_PFX = vmid + "|";
    }

    //MT: protected by class' lock
    static String connectionTesterClassName = null;

    //static ConnectionTester cachedTester = null;
    

    // identityHashCode() is not a sufficient unique token, because they are
    // not guaranteed unique, and in practice are occasionally not unique,
    // particularly on 64-bit systems.

    public static String allocateIdentityToken(Object o)
    { 
	if (o == null)
	    return null;
	else
	    {
		String shortIdToken = Integer.toString( System.identityHashCode( o ), 16 );

		//new Exception( "DEBUG_STACK_TRACE: " + o.getClass().getName() + " " + shortIdToken ).printStackTrace();

		String out;
		long count;
		StringBuffer sb = new StringBuffer(128);
		sb.append(VMID_PFX);
		if (ID_TOKEN_COUNTER != null && ((count = ID_TOKEN_COUNTER.encounter( shortIdToken )) > 0))
		    {
			sb.append( shortIdToken );
			sb.append('#');
			sb.append( count );
		    }
		else
		    sb.append(shortIdToken);

		out = sb.toString().intern();

		return out;
	    }
    }

    public static DbAuth findAuth(Object o)
	throws SQLException
    {
	if ( o == null )
	    return NULL_AUTH;

	String user = null;
	String password = null;

	String overrideDefaultUser    = null;
	String overrideDefaultPassword = null;

	try
	    {
		BeanInfo bi = Introspector.getBeanInfo( o.getClass() );
		PropertyDescriptor[] pds = bi.getPropertyDescriptors();
		for (int i = 0, len = pds.length; i < len; ++i)
		    {
			PropertyDescriptor pd = pds[i];
			Class propCl = pd.getPropertyType();
			String propName = pd.getName();
			if (propCl == String.class)
			    {
//  				System.err.println( "---> " + propName );
//  				System.err.println( o.getClass() );
//  				System.err.println( pd.getReadMethod() );

				Method readMethod = pd.getReadMethod();
				if (readMethod != null)
				    {
					Object propVal = readMethod.invoke( o, NOARGS );
					String value = (String) propVal;
					if ("user".equals(propName))
					    user = value;
					else if ("password".equals(propName))
					    password = value;
					else if ("overrideDefaultUser".equals(propName))
					    overrideDefaultUser = value;
					else if ("overrideDefaultPassword".equals(propName))
					    overrideDefaultPassword = value;
				    }
			    }
		    }
		if (overrideDefaultUser != null)
		    return new DbAuth( overrideDefaultUser, overrideDefaultPassword );
		else if (user != null)
		    return new DbAuth( user, password );
		else
		    return NULL_AUTH;
	    }
	catch (Exception e)
	    {
		if (Debug.DEBUG && logger.isLoggable( MLevel.FINE ))
		    logger.log( MLevel.FINE, "An exception occurred while trying to extract the default authentification info from a bean.", e );
		throw SqlUtils.toSQLException(e);
	    }
    }

    /*
     * This method is called ONLY when user-visible proxy Connections are close()ing,
     * or when the PooledConnection that hosts pCon is close()ing. It is NOT called
     * on commit() and/or rollback() of a continuing user Connection. Given that "fresh"
     * user Connections always begin with autoCommit = true, the logic here is good.
     * We do not setAutoCommit( true ) underneath users holding visible Connections.
     *
     * Perhaps we should rename this to resetTxnStateOnProxyConnectionClose to avoid
     * confusion...
     */ 
    static void resetTxnState( Connection pCon, 
			       boolean forceIgnoreUnresolvedTransactions, 
			       boolean autoCommitOnClose, 
			       boolean txnKnownResolved ) throws SQLException
    {
	if ( !forceIgnoreUnresolvedTransactions && !pCon.getAutoCommit() )
	    {
		if (! autoCommitOnClose && ! txnKnownResolved)
		    {
			//System.err.println("Rolling back potentially unresolved txn...");
			pCon.rollback();
		    }	
		pCon.setAutoCommit( true ); //implies commit if not already rolled back.
	    }
    }

    /*
    public synchronized static ConnectionTester defaultConnectionTester()
    {
	String dfltCxnTesterClassName = PoolConfig.defaultConnectionTesterClassName();
	if ( connectionTesterClassName != null && connectionTesterClassName.equals(dfltCxnTesterClassName) )
	    return cachedTester;
	else
	    {
		try 
		    { 
			cachedTester = (ConnectionTester) Class.forName( dfltCxnTesterClassName ).newInstance(); 
			connectionTesterClassName = cachedTester.getClass().getName();
		    }
		catch ( Exception e )
		    {
			//e.printStackTrace();
			if ( logger.isLoggable( MLevel.WARNING ) )
			    logger.log(MLevel.WARNING, 
				       "Could not load ConnectionTester " + dfltCxnTesterClassName + ", using built in default.", 
				       e);
			cachedTester = C3P0Defaults.connectionTester();
			connectionTesterClassName = cachedTester.getClass().getName();
		    }
		return cachedTester;
	    }
    }
    */

    public static boolean supportsMethod(Object target, String mname, Class[] argTypes)
    {
	try {return (target.getClass().getMethod( mname, argTypes ) != null); }
	catch ( NoSuchMethodException e )
	    { return false; }
	catch (SecurityException e)
	    {
		if ( logger.isLoggable( MLevel.FINE ) )
		    logger.log(MLevel.FINE, 
			       "We were denied access in a check of whether " + target + " supports method " + mname + 
			       ". Prob means external clients have no access, returning false.",
			       e);
		return false;
	    }
    }

    /*
    // Java Serialization-based userOverridesAsString format creates an unnecessary attack surface for
    // placing malicious objects in the serialized format and provoking deserialization.
    //
    // We'll transition to a simpler, less dangerous format.

    private final static String HASM_HEADER = "HexAsciiSerializedMap";

    public static String createUserOverridesAsString( Map userOverrides ) throws IOException
    {
	StringBuffer sb = new StringBuffer();
	sb.append(HASM_HEADER);
	sb.append('[');
	sb.append( ByteUtils.toHexAscii( SerializableUtils.toByteArray( userOverrides ) ) );
	sb.append(']');
	return sb.toString();
    }

    public static Map parseUserOverridesAsString( String userOverridesAsString ) throws IOException, ClassNotFoundException
    {
	if (userOverridesAsString != null)
	    {
		String hexAscii = userOverridesAsString.substring(HASM_HEADER.length() + 1, userOverridesAsString.length() - 1);
		byte[] serBytes = ByteUtils.fromHexAscii( hexAscii );
		return Collections.unmodifiableMap( (Map) SerializableUtils.fromByteArray( serBytes ) );
	    }
	else
	    return Collections.EMPTY_MAP;
    }
    */

    // we serialize user overrides to "ragged CSV".
    // CSV lines containing only a single element are interpreted as the user for whom we are overriding config
    // lines following containing two elements are the config param overrides for that user.
    public static String createUserOverridesAsString( Map userOverrides ) throws IOException, MalformedCsvException
    {
        Writer w = new StringWriter();
        for (Object o : userOverrides.keySet())
        {
            // we don't check the type. We're treating this as basically an assertion, in our old-school, loosely typed Java
            // we'll let the user see a ClassCastException if something has been messed with
            String user = (String) o;
            w.append(generateQuotedCsvItem(user));
            w.append("\r\n");
            Map userProps = (Map) userOverrides.get(user);
            String[] propNamePropValAsString = new String[2];
            for (Object pn : userProps.keySet())
            {
                propNamePropValAsString[0] = (String) pn;
                propNamePropValAsString[1] = (String) userProps.get(pn);
                w.append(generateCsvLineQuotedUnterminated(propNamePropValAsString));
                w.append("\r\n");
            }
        }
        return w.toString();
    }

    private static Map parseSingleUserMap(String userOverridesAsString, BufferedReader br, String[] nextUserHolder) throws IOException, MalformedCsvException
    {
        Map out = new HashMap();
        nextUserHolder[0] = null;
        String line = FastCsvUtils.csvReadLine(br);
        if (line == null)
        {
            nextUserHolder[0] = null;
            return Collections.EMPTY_MAP;
        }
        else
        {
            do
            {
                String[] items = FastCsvUtils.splitRecord( line );
                switch ( items.length )
                {
                case 2: // this is an expected property override line
                    out.put(items[0],items[1]);
                    break;
                case 1: // this is the next user name
                    nextUserHolder[0] = items[0];
                    break;
                default:
                    throw new IOException("Unexpected CSV line in userOverridesAsString ('" + line + "'). All line should have 1 or 2 items:\r\n" + userOverridesAsString);
                }
            }
            while (nextUserHolder[0] == null && (line = FastCsvUtils.csvReadLine(br)) != null); // either EOL or discovery of next user terminates
            return Collections.unmodifiableMap(out);
        }
    }

    public static Map parseUserOverridesAsString( String userOverridesAsString ) throws IOException, MalformedCsvException
    {
        if ( userOverridesAsString != null )
        {
            String[] nextUserHolder = new String[1];
            BufferedReader br = new BufferedReader(new StringReader(userOverridesAsString));
            String line = FastCsvUtils.csvReadLine(br);
            if ( line == null )
                return Collections.EMPTY_MAP;
            else
            {
                Map out = new HashMap();
                String[] items = FastCsvUtils.splitRecord(line);
                if (items.length != 1)
                    throw new IOException("Cannot parse userOverridesAsString, one element line naming the user should come before other data:\r\n" + userOverridesAsString);
                String username = items[0];
                do
                {
                    Map overrides = parseSingleUserMap(userOverridesAsString, br, nextUserHolder);
                    out.put(username, overrides);
                    username = nextUserHolder[0];
                }
                while (username != null);
                return Collections.unmodifiableMap(out);
            }
        }
        else
            return Collections.EMPTY_MAP;
    }

    // CSV generation from mchange-commons-java 0.4.0's FastCsvUtils, which the mchange-commons-java
    // this c3p0 depends on (0.2.19) lacks. Its splitRecord(...) is 0.4.0's csvSplitLine(...) under its old name.
    private static String generateQuotedCsvItem(String s)
    {
        int quoteCount = countQuotes(s);
        if ( quoteCount == 0 )
            return "\"" + s + "\"";
        else
        {
            int len = s.length();
            StringBuilder sb = new StringBuilder(len + quoteCount + 2);
            sb.append('\"');
            for (int i = 0; i < len; ++i)
            {
                char c = s.charAt(i);
                if (c == '"')
                    sb.append("\"\"");
                else
                    sb.append(c);
            }
            sb.append('\"');
            return sb.toString();
        }
    }

    private static String generateCsvLineQuotedUnterminated(String[] ss)
    {
        int len = ss.length;
        StringBuffer sb = new StringBuffer( 512 );
        for (int i = 0; i < len; ++i)
        {
            sb.append(generateQuotedCsvItem(ss[i]));
            if (i != len-1) sb.append(',');
        }
        return sb.toString();
    }

    private static int countQuotes(String s)
    {
	char[] chars = s.toCharArray();
	int count = 0;
	for (int i = 0, len = chars.length; i < len; ++i)
	    {
		if (chars[i] == '"') ++count;
	    }
	return count;
    }

    /**
     *  never intended to be called. we just want a compiler error if somehow we are building/code-generating 
     *  against an old version of JDBC, as happened somehow with the c3p0-0.9.5-pre2 release
     */
    public static void assertCompileTimePresenceOfJdbc4_Jdk17Api( NewProxyConnection npc ) throws SQLException
    { npc.getNetworkTimeout(); }

    /**
     * Call to guard setting and use of JNDI names. Will throw a NamingException if the name is unsupported
     */
    public static void jndiAssertNameIsAcceptable(Object jndiName) throws NamingException
    { JndiNameGuard.assertAcceptableName( jndiName ); }

    private C3P0ImplUtils()
    {}
}



//  Class methodClass = readMethod.getDeclaringClass();
//  Package methodPkg = methodClass.getPackage();
//  System.err.println( methodPkg.getName() + '\t' + C3P0ImplUtils.class.getPackage().getName() );
//  if (! methodPkg.getName().equals( 
//  				 C3P0ImplUtils.class.getPackage().getName() ) )
//  {
//      System.err.println("public check: " + (methodClass.getModifiers() & Modifier.PUBLIC));
//      if ((methodClass.getModifiers() & Modifier.PUBLIC) == 0)
//  	{
//  	    System.err.println("SKIPPED -- Can't Access!");
//  	    continue;
//  	}
//  }
//  System.err.println( o );

    /*
    private final static ThreadLocal threadLocalConnectionCustomizer = new ThreadLocal();

    // used so that C3P0PooledConnectionPool can pass a ConnectionCustomizer 
    // to WrapperConnectionPoolDataSource without altering that class' public API
    public static void setThreadConnectionCustomizer(ConnectionCustomizer cc)
    { threadLocalConnectionCustomizer.set( cc ); }

    public static ConnectionCustomizer getThreadConnectionCustomizer()
    { return threadLocalConnectionCustomizer.get(); }

    public static void unsetThreadConnectionCustomizer()
    { setThreadConnectionCustomizer( null ); }
    */
