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

package com.mchange.v2.c3p0.test.junit;

import java.lang.reflect.*;
import java.sql.SQLException;
import java.util.*;
import javax.naming.*;
import javax.naming.spi.*;
import junit.framework.TestCase;
import com.mchange.v2.c3p0.JndiRefConnectionPoolDataSource;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.cfg.MultiPropertiesConfig;

public final class JndiNameGuardLookupJUnitTestCase extends TestCase
{
    final static String REMOTE_NAME   = "ldap://attacker.example.com:1389/exploit";
    final static String LOCAL_NAME    = "java:comp/env/jdbc/MyDB";
    final static String RELATIVE_NAME = "jdbc/MyDataSource";

    final static List<String> asked = Collections.synchronizedList( new ArrayList<String>() );

    // While an InitialContextFactoryBuilder is installed, InitialContext sends every name, URL names included, to the
    // context it builds, so no naming provider of the JDK is reached. It cannot be uninstalled.
    private static boolean builderInstalled = false;

    private static synchronized void installRecordingBuilder() throws NamingException
    {
	if (builderInstalled)
	    return;
	if ( NamingManager.hasInitialContextFactoryBuilder() )
	    throw new IllegalStateException("Another InitialContextFactoryBuilder is installed; lookups could leave the JVM.");
	final Context recording = (Context) Proxy.newProxyInstance( Context.class.getClassLoader(), new Class[] { Context.class }, new InvocationHandler()
	    {
		public Object invoke( Object proxy, Method method, Object[] args ) throws Throwable
		{
		    String mname = method.getName();
		    if ( method.getDeclaringClass() == Object.class )
		    {
			if ( "equals".equals( mname ) ) return proxy == args[0];
			if ( "hashCode".equals( mname ) ) return System.identityHashCode( proxy );
			return "recording context";
		    }
		    if ( "close".equals( mname ) ) return null;
		    if ( "getEnvironment".equals( mname ) ) return new Hashtable();
		    asked.add( mname + " " + (args == null || args.length == 0 ? "" : String.valueOf( args[0] )) );
		    throw new NameNotFoundException( "the recording context binds nothing" );
		}
	    } );
	NamingManager.setInitialContextFactoryBuilder( new InitialContextFactoryBuilder()
	    {
		public InitialContextFactory createInitialContextFactory( Hashtable env )
		{
		    return new InitialContextFactory()
			{
			    public Context getInitialContext( Hashtable env ) { return recording; }
			};
		}
	    } );
	builderInstalled = true;
    }

    private static void configureNameGuard( String nameGuardClassName )
    {
	Properties props = new Properties();
	props.setProperty( "com.mchange.v2.naming.nameGuardClassName", nameGuardClassName );
	C3P0Config.refreshMainConfig( new MultiPropertiesConfig[] { MultiPropertiesConfig.fromProperties( props ) }, "JndiNameGuardLookupJUnitTestCase" );
    }

    private static JndiRefConnectionPoolDataSource dataSourceNamed( String jndiName ) throws Exception
    {
	JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource( false );
	ds.setJndiName( jndiName );
	return ds;
    }

    // A data source can carry a name the current configuration would refuse, when deserialized, dereferenced or made under other configuration.
    private static JndiRefConnectionPoolDataSource dataSourceCarrying( String jndiName ) throws Exception
    {
	configureNameGuard( "com.mchange.v2.naming.AnyNameNameGuard" );
	try { return dataSourceNamed( jndiName ); }
	finally { C3P0Config.refreshMainConfig(); }
    }

    private static void use( JndiRefConnectionPoolDataSource ds )
    {
	try { ds.getPooledConnection(); fail( "the recording context binds nothing, so no connection can come back" ); }
	catch ( SQLException expected ) {}
	try { ds.getLogWriter(); fail( "the recording context binds nothing, so no log writer can come back" ); }
	catch ( SQLException expected ) {}
    }

    protected void setUp() throws Exception
    {
	installRecordingBuilder();
	asked.clear();
    }

    protected void tearDown() throws Exception
    { C3P0Config.refreshMainConfig(); }

    public void testCarriedRemoteNameIsNeverLookedUp() throws Exception
    {
	use( dataSourceCarrying( REMOTE_NAME ) );
	assertEquals( Collections.emptyList(), asked );
    }

    public void testLocalNameIsLookedUp() throws Exception
    {
	use( dataSourceNamed( LOCAL_NAME ) );
	assertEquals( Arrays.asList( "lookup " + LOCAL_NAME, "lookup " + LOCAL_NAME ), asked );
    }

    public void testCarriedRelativeNameIsNeverLookedUp() throws Exception
    {
	use( dataSourceCarrying( RELATIVE_NAME ) );
	assertEquals( Collections.emptyList(), asked );
    }

    public void testMBeansRefuseRemoteBindNameWithoutTouchingJndi() throws Exception
    {
	try { new com.mchange.v2.c3p0.jboss.C3P0PooledDataSource().setJndiName( REMOTE_NAME ); fail( "jboss C3P0PooledDataSource accepted " + REMOTE_NAME ); }
	catch ( NamingException expected ) {}
	try { new com.mchange.v2.c3p0.mbean.C3P0PooledDataSource().setJndiName( REMOTE_NAME ); fail( "mbean C3P0PooledDataSource accepted " + REMOTE_NAME ); }
	catch ( NamingException expected ) {}
	assertEquals( Collections.emptyList(), asked );
    }

    public void testConfiguredAnyNameNameGuardAcceptsRemoteName() throws Exception
    {
	configureNameGuard( "com.mchange.v2.naming.AnyNameNameGuard" );
	assertEquals( REMOTE_NAME, dataSourceNamed( REMOTE_NAME ).getJndiName() );
    }

    public void testConfiguredUnknownNameGuardAcceptsNoName() throws Exception
    {
	configureNameGuard( "com.example.NoSuchNameGuard" );
	try { dataSourceNamed( LOCAL_NAME ); fail( "an unknown NameGuard accepted " + LOCAL_NAME ); }
	catch ( java.beans.PropertyVetoException expected ) {}
    }
}
