/**
 * The MIT License
 * Copyright (c) 2018 CubeEngine
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.cubeengine.maven.plugins.oredeploy.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "deploy", threadSafe = true)
public class OreDeployMojo extends AbstractMojo
{

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project = null;

    @Parameter(defaultValue = "${ore.deploy.pluginid}", required = true, readonly = true)
    private String pluginId = null;

    @Parameter(defaultValue = "${ore.deploy.version}", required = true, readonly = true)
    private String version = null;

    @Parameter(defaultValue = "${ore.deploy.channel.release}", readonly = true)
    private String releaseChannel = "release";

    @Parameter(defaultValue = "${ore.deploy.channel.snapshot}", readonly = true)
    private String snapshotChannel = "snapshot";

    @Parameter(defaultValue = "${ore.deploy.apikey}", required = true, readonly = true)
    private String apiKey = null;


    /**
     * {@inheritDoc}
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException
    {
        String artifact = project.getArtifact().getFile().toPath().toString();
        String channel = project.getArtifact().isRelease() ? releaseChannel : snapshotChannel;
        getLog().info("curl \\"
                          + " -F \"apiKey=<apiKey>\" \\"
                          + " -F \"channel=" + channel + "\" \\"
                          + " -F \"pluginFile=@" + artifact + "\" \\"
                          + " -F \"pluginSig=@" + artifact + ".asc\" \\"
                          + " https://ore.spongepowered.org/api/projects/" + pluginId + "/versions/" + version);

    }
}