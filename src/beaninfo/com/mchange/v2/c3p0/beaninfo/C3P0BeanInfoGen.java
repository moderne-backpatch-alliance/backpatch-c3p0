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

package com.mchange.v2.c3p0.beaninfo;

import java.io.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import com.mchange.v2.codegen.bean.BeanInfoGen;

public final class C3P0BeanInfoGen
{
    final static Set excludedPropertyNames;
    final static Set excludedPropertyTypes;

    static
    {
        Set tmp0 = new HashSet();
        tmp0.add("connection");
        tmp0.add("pooledConnection");
        excludedPropertyNames = Collections.unmodifiableSet(tmp0);

        Set tmp1 = new HashSet();
        tmp1.add( java.sql.Connection.class );
        tmp1.add( javax.sql.PooledConnection.class );
        excludedPropertyTypes = Collections.unmodifiableSet(tmp1);
    }

    public static void main(String[] argv) throws Exception
    {
        int numFqcns = argv.length - 1;
        String baseOutputDirStr = argv[numFqcns];
        File baseOutputDir = new File(baseOutputDirStr);

        for (int i = 0; i < numFqcns; ++i)
        {
            String fqcn = argv[i];
            System.out.println("Generating bean info class for '" + fqcn + "'.");
            RelDirectoryFilename rdfn = new RelDirectoryFilename(fqcn);
            File relDir = new File(baseOutputDir, rdfn.relDirectory);
            relDir.mkdirs();
            File outputFile = new File(relDir, rdfn.filename);
            Class beanClass = Class.forName(fqcn);
            Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"));
            try
            {
                w.write(BeanInfoGen.explicitBeanInfoClassSourceForBeanClass( beanClass, excludedPropertyNames, excludedPropertyTypes ));
            }
            finally
            { w.close(); }
        }
        System.out.println("Bean info class generation complete.");
    }

    final static class RelDirectoryFilename
    {
        String relDirectory;
        String filename;

        RelDirectoryFilename(String fqcn)
        {
            int lastDot = fqcn.lastIndexOf('.');
            String dottedRelDir = fqcn.substring(0,lastDot);
            this.filename = fqcn.substring(lastDot + 1) + "BeanInfo.java";
            this.relDirectory = dottedRelDir.replace('.','/');
        }
    }
}
