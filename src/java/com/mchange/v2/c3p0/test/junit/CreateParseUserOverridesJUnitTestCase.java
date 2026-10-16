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

import java.util.*;
import junit.framework.TestCase;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;

public final class CreateParseUserOverridesJUnitTestCase extends TestCase
{
    public void testEmptyMapRoundTrip() throws Exception
    {
        Map original = new HashMap();
        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Empty map should roundtrip to empty map", original, restored );
    }

    public void testSingleUserSinglePropertyRoundTrip() throws Exception
    {
        Map props = new HashMap();
        props.put("maxPoolSize", "15");

        Map original = new HashMap();
        original.put("alice", props);

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Single user with single property should roundtrip", original, restored );
    }

    public void testSingleUserMultiplePropertiesRoundTrip() throws Exception
    {
        Map props = new HashMap();
        props.put("maxPoolSize", "15");
        props.put("minPoolSize", "3");
        props.put("checkoutTimeout", "5000");

        Map original = new HashMap();
        original.put("bob", props);

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Single user with multiple properties should roundtrip", original, restored );
    }

    public void testMultipleUsersRoundTrip() throws Exception
    {
        Map aliceProps = new HashMap();
        aliceProps.put("maxPoolSize", "15");
        aliceProps.put("minPoolSize", "3");

        Map bobProps = new HashMap();
        bobProps.put("maxPoolSize", "25");
        bobProps.put("checkoutTimeout", "10000");
        bobProps.put("idleConnectionTestPeriod", "60");

        Map original = new HashMap();
        original.put("alice", aliceProps);
        original.put("bob", bobProps);

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Multiple users should roundtrip", original, restored );
    }

    public void testValuesWithSpecialCharacters() throws Exception
    {
        Map props = new HashMap();
        props.put("description", "a value with \"quotes\" inside");
        props.put("url", "jdbc:postgresql://localhost:5432/mydb?ssl=true&option=val");
        props.put("note", "line1\nline2");
        props.put("commaVal", "one,two,three");

        Map original = new HashMap();
        original.put("tricky\"user", props);

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Values with special characters (quotes, commas, newlines) should roundtrip", original, restored );
    }

    public void testUserWithEmptyProperties() throws Exception
    {
        Map emptyProps = new HashMap();

        Map otherProps = new HashMap();
        otherProps.put("maxPoolSize", "10");

        Map original = new HashMap();
        original.put("emptyuser", emptyProps);
        original.put("otheruser", otherProps);

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "User with empty property map should roundtrip", original, restored );
    }

    public void testEmptyStringValues() throws Exception
    {
        Map props = new HashMap();
        props.put("password", "");
        props.put("maxPoolSize", "5");

        Map original = new HashMap();
        original.put("user1", props);

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Empty string property values should roundtrip", original, restored );
    }

    public void testParseEmptyStringReturnsEmptyMap() throws Exception
    {
        Map restored = C3P0ImplUtils.parseUserOverridesAsString("");
        assertTrue( "Parsing empty string should return empty map", restored.isEmpty() );
    }

    public void testManyUsersRoundTrip() throws Exception
    {
        Map original = new HashMap();
        for (int i = 0; i < 20; i++)
        {
            Map props = new HashMap();
            props.put("maxPoolSize", String.valueOf(10 + i));
            props.put("minPoolSize", String.valueOf(i));
            original.put("user_" + i, props);
        }

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Many users should roundtrip", original, restored );
    }

    public void testNullParsesToEmptyMap() throws Exception
    {
        Map parsed = C3P0ImplUtils.parseUserOverridesAsString(null);
        assertTrue( "Parsing null should return empty map", parsed.isEmpty() );
    }
}
