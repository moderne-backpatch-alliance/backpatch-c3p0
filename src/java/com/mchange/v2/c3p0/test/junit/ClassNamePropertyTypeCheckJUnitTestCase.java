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

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import javax.naming.*;
import junit.framework.TestCase;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.impl.DefaultConnectionTester;
import com.mchange.v2.naming.JavaBeanObjectFactory;

public final class ClassNamePropertyTypeCheckJUnitTestCase extends TestCase
{
    // Written by the nested classes' static initializers and constructors; reading it never initializes them.
    final static Set EVENTS = Collections.synchronizedSet( new HashSet() );

    public static class NotATesterFromReference
    {
        static { EVENTS.add( "NotATesterFromReference.<clinit>" ); }
        public NotATesterFromReference() { EVENTS.add( "NotATesterFromReference.<init>" ); }
    }

    public static class NotATesterFromRegistry
    {
        static { EVENTS.add( "NotATesterFromRegistry.<clinit>" ); }
        public NotATesterFromRegistry() { EVENTS.add( "NotATesterFromRegistry.<init>" ); }
    }

    public static class NotACustomizer
    {
        static { EVENTS.add( "NotACustomizer.<clinit>" ); }
        public NotACustomizer() { EVENTS.add( "NotACustomizer.<init>" ); }
    }

    public static class NotADriver
    {
        public NotADriver() { EVENTS.add( "NotADriver.<init>" ); }
    }

    public static abstract class OkayTester implements ConnectionTester
    {
        public int activeCheckConnection( Connection c ) { return CONNECTION_IS_OKAY; }
        public int statusOnException( Connection c, Throwable t ) { return CONNECTION_IS_OKAY; }
    }

    public static class RecordingTesterFromReference extends OkayTester
    {
        public RecordingTesterFromReference() { EVENTS.add( "RecordingTesterFromReference.<init>" ); }
    }

    public static class RecordingTesterFromRegistry extends OkayTester
    {
        public RecordingTesterFromRegistry() { EVENTS.add( "RecordingTesterFromRegistry.<init>" ); }
    }

    public static class RecordingCustomizer extends AbstractConnectionCustomizer
    {
        public RecordingCustomizer() { EVENTS.add( "RecordingCustomizer.<init>" ); }
    }

    public static class RecordingDriver implements Driver
    {
        public RecordingDriver() { EVENTS.add( "RecordingDriver.<init>" ); }
        public Connection connect( String url, Properties info ) { return null; }
        public boolean acceptsURL( String url ) { return false; }
        public DriverPropertyInfo[] getPropertyInfo( String url, Properties info ) { return new DriverPropertyInfo[0]; }
        public int getMajorVersion() { return 0; }
        public int getMinorVersion() { return 0; }
        public boolean jdbcCompliant() { return false; }
        public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
    }

    private static Object dereference( String testerClassName ) throws Exception
    {
        Reference ref = new Reference( WrapperConnectionPoolDataSource.class.getName(), JavaBeanObjectFactory.class.getName(), null );
        ref.add( new StringRefAddr( "connectionTesterClassName", testerClassName ) );
        return new JavaBeanObjectFactory().getObjectInstance( ref, null, null, null );
    }

    public void testReferenceNamingANonTesterClassIsRefusedBeforeTheClassIsInitialized() throws Exception
    {
        try
        {
            dereference( NotATesterFromReference.class.getName() );
            fail( "a Reference naming a class that is not a ConnectionTester should not dereference" );
        }
        catch (Exception expected) {}
        assertFalse( EVENTS.contains( "NotATesterFromReference.<clinit>" ) );
        assertFalse( EVENTS.contains( "NotATesterFromReference.<init>" ) );
    }

    public void testReferenceNamingAConnectionTesterStillDereferences() throws Exception
    {
        WrapperConnectionPoolDataSource wcpds = (WrapperConnectionPoolDataSource) dereference( RecordingTesterFromReference.class.getName() );
        assertEquals( RecordingTesterFromReference.class.getName(), wcpds.getConnectionTesterClassName() );
        assertTrue( EVENTS.contains( "RecordingTesterFromReference.<init>" ) );
    }

    public void testRegistryFallsBackToTheDefaultTesterWithoutInitializingANonTesterClass()
    {
        ConnectionTester tester = C3P0Registry.getConnectionTester( NotATesterFromRegistry.class.getName() );
        assertEquals( DefaultConnectionTester.class, tester.getClass() );
        assertFalse( EVENTS.contains( "NotATesterFromRegistry.<clinit>" ) );
        assertFalse( EVENTS.contains( "NotATesterFromRegistry.<init>" ) );
    }

    public void testRegistryStillInstantiatesAConnectionTester()
    {
        ConnectionTester tester = C3P0Registry.getConnectionTester( RecordingTesterFromRegistry.class.getName() );
        assertEquals( RecordingTesterFromRegistry.class, tester.getClass() );
        assertTrue( EVENTS.contains( "RecordingTesterFromRegistry.<init>" ) );
    }

    public void testRegistryRefusesANonCustomizerClassBeforeTheClassIsInitialized()
    {
        try
        {
            C3P0Registry.getConnectionCustomizer( NotACustomizer.class.getName() );
            fail( "a class that is not a ConnectionCustomizer should be refused" );
        }
        catch (SQLException expected) {}
        assertFalse( EVENTS.contains( "NotACustomizer.<clinit>" ) );
        assertFalse( EVENTS.contains( "NotACustomizer.<init>" ) );
    }

    public void testRegistryStillInstantiatesAConnectionCustomizer() throws Exception
    {
        ConnectionCustomizer customizer = C3P0Registry.getConnectionCustomizer( RecordingCustomizer.class.getName() );
        assertEquals( RecordingCustomizer.class, customizer.getClass() );
        assertTrue( EVENTS.contains( "RecordingCustomizer.<init>" ) );
    }

    private static DriverManagerDataSource forcedDriverDataSource( String driverClass )
    {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setJdbcUrl( "jdbc:example:test" );
        ds.setDriverClass( driverClass );
        ds.setForceUseNamedDriverClass( true );
        return ds;
    }

    public void testForcedDriverClassThatIsNotADriverIsNotInstantiated()
    {
        try
        {
            forcedDriverDataSource( NotADriver.class.getName() ).getConnection();
            fail( "a driverClass that is not a java.sql.Driver should not yield a Connection" );
        }
        catch (Exception expected) {}
        assertFalse( EVENTS.contains( "NotADriver.<init>" ) );
    }

    public void testForcedDriverClassThatIsADriverIsStillInstantiated()
    {
        try
        {
            forcedDriverDataSource( RecordingDriver.class.getName() ).getConnection();
            fail( "RecordingDriver accepts no URL, so getConnection() should fail after instantiating it" );
        }
        catch (SQLException expected)
        { assertTrue( expected.getMessage(), expected.getMessage().indexOf( "is not valid for the underlying driver" ) >= 0 ); }
        assertTrue( EVENTS.contains( "RecordingDriver.<init>" ) );
    }
}
