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

import javax.lang.model.SourceVersion;
import javax.naming.Name;
import javax.naming.NamingException;
import com.mchange.v2.c3p0.cfg.C3P0Config;

/*
 *  c3p0's own copy of mchange-commons-java 0.4.0's NameGuard and its four implementations,
 *  because the mchange-commons-java this c3p0 declares (0.2.19) has none of them.
 */
abstract class JndiNameGuard
{
    final static String NAME_GUARD_CLASS_NAME_KEY = "com.mchange.v2.naming.nameGuardClassName";

    final static String APPARENTLY_LOCAL                                       = "com.mchange.v2.naming.ApparentlyLocalNameGuard";
    final static String FIRST_COMPONENT_IS_JAVA_IDENTIFIER                     = "com.mchange.v2.naming.FirstComponentIsJavaIdentifierNameGuard";
    final static String APPARENTLY_LOCAL_OR_FIRST_COMPONENT_IS_JAVA_IDENTIFIER = "com.mchange.v2.naming.ApparentlyLocalOrFirstComponentIsJavaIdentifierNameGuard";
    final static String ANY_NAME                                               = "com.mchange.v2.naming.AnyNameNameGuard";

    final static String DEFAULT_NAME_GUARD_CLASS_NAME = APPARENTLY_LOCAL;

    abstract boolean nameIsAcceptable( Name name );
    abstract boolean nameIsAcceptable( String name );

    // follows "names are only acceptable when "; no terminal period
    abstract String onlyAcceptableWhen();

    private final static JndiNameGuard apparentlyLocal = new JndiNameGuard()
    {
	boolean nameIsAcceptable( Name name )   { return !name.isEmpty() && name.get(0).startsWith("java:"); }
	boolean nameIsAcceptable( String name ) { return name.startsWith("java:"); }

	String onlyAcceptableWhen()
	{
	    return
		"they appear to be local to the JVM, that is if they are Strings, they begin with 'java:' " +
		"or they are Name objects their first component begins with 'java:'";
	}
    };

    private final static JndiNameGuard firstComponentIsJavaIdentifier = new JndiNameGuard()
    {
	boolean nameIsAcceptable( Name name )   { return !name.isEmpty() && SourceVersion.isName(name.get(0)); }

	// an escaped slash leaves its backslash in the first component, which then fails the identifier test, as it should
	boolean nameIsAcceptable( String name )
	{
	    int slashIndex = name.indexOf("/");
	    return SourceVersion.isName( slashIndex >= 0 ? name.substring(0,slashIndex) : name );
	}

	String onlyAcceptableWhen()
	{ return "their first component would be a valid Java identifier (e.g. jdbc or jms)"; }
    };

    private final static JndiNameGuard apparentlyLocalOrFirstComponentIsJavaIdentifier = new JndiNameGuard()
    {
	boolean nameIsAcceptable( Name name )
	{ return apparentlyLocal.nameIsAcceptable( name ) || firstComponentIsJavaIdentifier.nameIsAcceptable( name ); }

	boolean nameIsAcceptable( String name )
	{ return apparentlyLocal.nameIsAcceptable( name ) || firstComponentIsJavaIdentifier.nameIsAcceptable( name ); }

	String onlyAcceptableWhen()
	{ return apparentlyLocal.onlyAcceptableWhen() + " or " + firstComponentIsJavaIdentifier.onlyAcceptableWhen(); }
    };

    private final static JndiNameGuard anyName = new JndiNameGuard()
    {
	boolean nameIsAcceptable( Name name )   { return true; }
	boolean nameIsAcceptable( String name ) { return true; }

	String onlyAcceptableWhen() { return "they are, well, any name, all names are acceptable"; }
    };

    private static JndiNameGuard findForClassName( String fqcn )
    {
	if ( APPARENTLY_LOCAL.equals( fqcn ) )
	    return apparentlyLocal;
	else if ( FIRST_COMPONENT_IS_JAVA_IDENTIFIER.equals( fqcn ) )
	    return firstComponentIsJavaIdentifier;
	else if ( APPARENTLY_LOCAL_OR_FIRST_COMPONENT_IS_JAVA_IDENTIFIER.equals( fqcn ) )
	    return apparentlyLocalOrFirstComponentIsJavaIdentifier;
	else if ( ANY_NAME.equals( fqcn ) )
	    return anyName;
	else
	    return null;
    }

    static void assertAcceptableName( Object jndiName ) throws NamingException
    {
	String nameGuardClassName = C3P0Config.getMultiPropertiesConfig().getProperty( NAME_GUARD_CLASS_NAME_KEY );
	if ( nameGuardClassName != null )
	{
	    nameGuardClassName = nameGuardClassName.trim();
	    if ( nameGuardClassName.length() == 0 )
		nameGuardClassName = null;
	}

	JndiNameGuard nameGuard = findForClassName( nameGuardClassName == null ? DEFAULT_NAME_GUARD_CLASS_NAME : nameGuardClassName );
	if ( nameGuard == null )
	    throw new NamingException(
		"NameGuard '" + nameGuardClassName + "', configured via '" + NAME_GUARD_CLASS_NAME_KEY + "', is not one this c3p0 provides (" +
		APPARENTLY_LOCAL + ", " + FIRST_COMPONENT_IS_JAVA_IDENTIFIER + ", " + APPARENTLY_LOCAL_OR_FIRST_COMPONENT_IS_JAVA_IDENTIFIER + ", " + ANY_NAME +
		"), so no JNDI name is acceptable. Putative JNDI name: " + jndiName
	    );

	boolean acceptable;
	if ( jndiName instanceof String )
	    acceptable = nameGuard.nameIsAcceptable((String) jndiName);
	else if ( jndiName instanceof Name )
	    acceptable = nameGuard.nameIsAcceptable((Name) jndiName);
	else
	{
	    throw new NamingException(
		"Putative JNDI name of unexpected type. We expect String or javax.naming.Name. " +
		"We conservatively, redundantly, disallow any attempt to lookup of jndi names of unknown types. There is no API to do so. " +
		"Putative JNDI name: " + jndiName
	    );
	}

	if (!acceptable)
	{
	    String nameGuardDescription;
	    if (nameGuardClassName == null)
		nameGuardDescription = "default NameGuard '" + DEFAULT_NAME_GUARD_CLASS_NAME +"'";
	    else
		nameGuardDescription = "NameGuard '" + nameGuardClassName + "', currently configured via '" + NAME_GUARD_CLASS_NAME_KEY + "'";
	    throw new NamingException(
		"Under " + nameGuardDescription + ", names are only acceptable when " + nameGuard.onlyAcceptableWhen() + ". '" + jndiName + "' does not qualify."
	    );
	}
    }
}
