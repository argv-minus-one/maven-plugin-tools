package org.apache.maven.tools.plugin.extractor;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.maven.plugin.descriptor.InvalidPluginDescriptorException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.tools.plugin.DefaultPluginToolsRequest;
import org.apache.maven.tools.plugin.PluginToolsRequest;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

/**
 * @author jdcasey
 * @version $Id$
 */
public abstract class AbstractScriptedMojoDescriptorExtractor
    extends AbstractLogEnabled
    implements MojoDescriptorExtractor
{
    /** {@inheritDoc} */
    public List<MojoDescriptor> execute( MavenProject project, PluginDescriptor pluginDescriptor )
        throws ExtractionException, InvalidPluginDescriptorException
    {
        return execute( new DefaultPluginToolsRequest( project, pluginDescriptor ) );
    }
    
    /** {@inheritDoc} */
    public List<MojoDescriptor> execute( PluginToolsRequest request )
        throws ExtractionException, InvalidPluginDescriptorException
    {
        getLogger().debug( "Running: " + getClass().getName() );
        String metadataExtension = getMetadataFileExtension( request );
        String scriptExtension = getScriptFileExtension( request );
        
        MavenProject project = request.getProject();

        @SuppressWarnings( "unchecked" )
        Map<String, Set<File>> scriptFilesKeyedByBasedir =
            gatherFilesByBasedir( project.getBasedir(), project.getScriptSourceRoots(), scriptExtension, request );

        List<MojoDescriptor> mojoDescriptors;
        if ( !StringUtils.isEmpty( metadataExtension ) )
        {
            @SuppressWarnings( "unchecked" )
            Map<String, Set<File>> metadataFilesKeyedByBasedir =
                gatherFilesByBasedir( project.getBasedir(), project.getScriptSourceRoots(), metadataExtension,
                                      request );

            mojoDescriptors = extractMojoDescriptorsFromMetadata( metadataFilesKeyedByBasedir, request );
        }
        else
        {
            mojoDescriptors = extractMojoDescriptors( scriptFilesKeyedByBasedir, request );
        }

        copyScriptsToOutputDirectory( scriptFilesKeyedByBasedir, project.getBuild().getOutputDirectory(), request );

        return mojoDescriptors;
    }

    /**
     * @param scriptFilesKeyedByBasedir not null
     * @param outputDirectory not null
     * @throws ExtractionException if any
     */
    protected void copyScriptsToOutputDirectory( Map<String, Set<File>> scriptFilesKeyedByBasedir, String outputDirectory, PluginToolsRequest request )
        throws ExtractionException
    {
        File outputDir = new File( outputDirectory );

        if ( !outputDir.exists() )
        {
            outputDir.mkdirs();
        }

        for ( Map.Entry<String, Set<File>> entry : scriptFilesKeyedByBasedir.entrySet() )
        {
            File sourceDir = new File( entry.getKey() );

            Set<File> scripts = entry.getValue();

            for ( File scriptFile : scripts )
            {
                String relativePath = scriptFile.getPath().substring( sourceDir.getPath().length() );

                if ( relativePath.charAt( 0 ) == File.separatorChar )
                {
                    relativePath = relativePath.substring( 1 );
                }

                File outputFile = new File( outputDir, relativePath ).getAbsoluteFile();

                if ( !outputFile.getParentFile().exists() )
                {
                    outputFile.getParentFile().mkdirs();
                }

                try
                {
                    FileUtils.copyFile( scriptFile, outputFile );
                }
                catch ( IOException e )
                {
                    throw new ExtractionException(
                        "Cannot copy script file: " + scriptFile + " to output: " + outputFile, e );
                }
            }
        }
    }

    /**
     * @param basedir not null
     * @param directories not null
     * @param scriptFileExtension not null
     * @return map with subdirs paths as key
     */
    protected Map<String, Set<File>> gatherFilesByBasedir( File basedir, List<String> directories, String scriptFileExtension, PluginToolsRequest request )
    {
        Map<String, Set<File>> sourcesByBasedir = new TreeMap<String, Set<File>>();

        for ( String resourceDir : directories )
        {
            Set<File> sources = new HashSet<File>();

            getLogger().debug( "Scanning script dir: " + resourceDir + " with extractor: " + getClass().getName() );
            File dir = new File( resourceDir );
            if ( !dir.isAbsolute() )
            {
                dir = new File( basedir, resourceDir ).getAbsoluteFile();
            }

            resourceDir = dir.getPath();

            if ( dir.exists() )
            {
                DirectoryScanner scanner = new DirectoryScanner();

                scanner.setBasedir( dir );
                scanner.addDefaultExcludes();
                scanner.setIncludes( new String[]{"**/*" + scriptFileExtension} );
                scanner.scan();

                String[] relativePaths = scanner.getIncludedFiles();

                for ( int i = 0; i < relativePaths.length; i++ )
                {
                    String relativePath = relativePaths[i];
                    File scriptFile = new File( dir, relativePath ).getAbsoluteFile();

                    if ( scriptFile.isFile() && relativePath.endsWith( scriptFileExtension ) )
                    {
                        sources.add( scriptFile );
                    }
                }

                sourcesByBasedir.put( resourceDir, sources );
            }
        }

        return sourcesByBasedir;
    }

    /**
     * Should be implemented in the sub classes.
     *
     * @param metadataFilesKeyedByBasedir could be null
     * @param request The plugin request, never <code>null</code>.
     * @return always null
     * @throws ExtractionException if any
     * @throws InvalidPluginDescriptorException if any
     */
    protected List<MojoDescriptor> extractMojoDescriptorsFromMetadata( Map<String, Set<File>> metadataFilesKeyedByBasedir,
                                                       PluginToolsRequest request )
        throws ExtractionException, InvalidPluginDescriptorException
    {
        return null;
    }

    /**
     * Should be implemented in the sub classes.
     *
     * @return always null
     */
    protected String getMetadataFileExtension( PluginToolsRequest request )
    {
        return null;
    }

    /**
     * Should be implemented in the sub classes.
     *
     * @param scriptFilesKeyedByBasedir could be null
     * @param request The plugin request, never <code>null</code>.
     * @return always null
     * @throws ExtractionException if any
     * @throws InvalidPluginDescriptorException if any
     */
    protected List<MojoDescriptor> extractMojoDescriptors( Map<String, Set<File>> scriptFilesKeyedByBasedir, PluginToolsRequest request )
        throws ExtractionException, InvalidPluginDescriptorException
    {
        return null;
    }

    /**
     * @return the file extension like <code>.bsh</code> for BeanShell.
     */
    protected abstract String getScriptFileExtension( PluginToolsRequest request );

}
