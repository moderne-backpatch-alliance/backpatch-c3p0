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

import java.beans.PropertyVetoException;
import javax.naming.*;

import junit.framework.TestCase;

import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;

// The JNDI name sections (4, 6, 8) of upstream's test; the other sections test mchange-commons-java 0.4.0+ API.
public final class DeserializationGadgetMitigationsJUnitTestCase extends TestCase
{
    // ======================================================================
    //  4. JNDI name validation — remote names rejected by default
    // ======================================================================

    /**
     * Setting a remote JNDI name (e.g. ldap://, rmi://) on JndiRefForwardingDataSource
     * should be vetoed by the VetoableChangeListener.
     */
    public void testRemoteLdapJndiNameRejected() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        try
        {
            ds.setJndiName( "ldap://attacker.example.com:1389/exploit" );
            fail( "Remote LDAP JNDI name should be rejected" );
        }
        catch ( PropertyVetoException expected )
        {
            // expected
        }
    }

    public void testRemoteRmiJndiNameRejected() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        try
        {
            ds.setJndiName( "rmi://attacker.example.com:1099/exploit" );
            fail( "Remote RMI JNDI name should be rejected" );
        }
        catch ( PropertyVetoException expected )
        {
            // expected
        }
    }

    public void testRemoteIiopJndiNameRejected() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        try
        {
            ds.setJndiName( "iiop://attacker.example.com/exploit" );
            fail( "Remote IIOP JNDI name should be rejected" );
        }
        catch ( PropertyVetoException expected )
        {
            // expected
        }
    }

    public void testRemoteDnsJndiNameRejected() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        try
        {
            ds.setJndiName( "dns://attacker.example.com/exploit" );
            fail( "Remote DNS JNDI name should be rejected" );
        }
        catch ( PropertyVetoException expected )
        {
            // expected
        }
    }

    /**
     * A bare name (not starting with "java:") should also be rejected by
     * the default ApparentlyLocalNameGuard.
     */
    public void testBareJndiNameRejected() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        try
        {
            ds.setJndiName( "jdbc/MyDataSource" );
            fail( "Bare JNDI name (not java: prefixed) should be rejected by default" );
        }
        catch ( PropertyVetoException expected )
        {
            // expected
        }
    }

    /**
     * A local JNDI name (starting with "java:") should be accepted.
     */
    public void testLocalJavaJndiNameAccepted() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        // This should not throw — "java:comp/env/jdbc/MyDB" is a local name
        ds.setJndiName( "java:comp/env/jdbc/MyDB" );
        assertEquals( "Local JNDI name should be accepted", "java:comp/env/jdbc/MyDB", ds.getJndiName() );
    }

    public void testLocalJavaColonJndiNameAccepted() throws Exception
    {
        JndiRefConnectionPoolDataSource ds = new JndiRefConnectionPoolDataSource();
        ds.setJndiName( "java:/jdbc/TestDS" );
        assertEquals( "java: prefixed name should be accepted", "java:/jdbc/TestDS", ds.getJndiName() );
    }

    /**
     * The C3P0ImplUtils.jndiAssertNameIsAcceptable helper should throw NamingException
     * for remote names. This is the same method used at lookup time as a second line of defense.
     */
    public void testJndiAssertNameIsAcceptableRejectsRemote() throws Exception
    {
        try
        {
            C3P0ImplUtils.jndiAssertNameIsAcceptable( "ldap://evil.example.com/payload" );
            fail( "jndiAssertNameIsAcceptable should reject remote JNDI name" );
        }
        catch ( NamingException expected )
        {
            // expected
        }
    }

    public void testJndiAssertNameIsAcceptableAcceptsLocal() throws Exception
    {
        // Should not throw
        C3P0ImplUtils.jndiAssertNameIsAcceptable( "java:comp/env/jdbc/MyDB" );
    }

    // ======================================================================
    //  6. JNDI name validation on javax.naming.Name objects (not just Strings)
    // ======================================================================

    /**
     * The NameGuard should also reject Name objects with remote-looking components.
     */
    public void testJndiAssertNameIsAcceptableRejectsRemoteNameObject() throws Exception
    {
        CompositeName remoteName = new CompositeName( "ldap://evil.example.com/payload" );
        try
        {
            C3P0ImplUtils.jndiAssertNameIsAcceptable( remoteName );
            fail( "jndiAssertNameIsAcceptable should reject remote javax.naming.Name" );
        }
        catch ( NamingException expected )
        {
            // expected
        }
    }

    public void testJndiAssertNameIsAcceptableAcceptsLocalNameObject() throws Exception
    {
        CompositeName localName = new CompositeName( "java:comp/env/jdbc/MyDB" );
        // Should not throw
        C3P0ImplUtils.jndiAssertNameIsAcceptable( localName );
    }

    // ======================================================================
    //  8. JNDI name validation covers JBoss and MBean entry points
    // ======================================================================

    /**
     * Verify that the central jndiAssertNameIsAcceptable rejects names of
     * unexpected types (neither String nor Name) as a defense-in-depth measure.
     */
    public void testJndiAssertNameIsAcceptableRejectsUnexpectedType() throws Exception
    {
        try
        {
            C3P0ImplUtils.jndiAssertNameIsAcceptable( Integer.valueOf(42) );
            fail( "jndiAssertNameIsAcceptable should reject non-String, non-Name objects" );
        }
        catch ( NamingException expected )
        {
            assertTrue(
                "Error should mention unexpected type",
                expected.getMessage().contains( "unexpected type" )
            );
        }
    }
}
