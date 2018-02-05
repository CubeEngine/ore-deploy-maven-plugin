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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Properties;

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

    @Parameter(defaultValue = "${ore.deploy.apikey}", readonly = true)
    private String apiKey = null;

    @Parameter(defaultValue = "${ore.deploy.apikey-lookup}")
    private File apiKeyLookup = null;


    /**
     * {@inheritDoc}
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException
    {
        Artifact artifact = project.getArtifact();
        File artifactFile = artifact.getFile();
        File artifactSigFile = new File(artifactFile.getAbsolutePath() + ".asc");
        final String channel = artifact.isRelease() ? releaseChannel : snapshotChannel;

        String apiKey = this.apiKey;
        if (apiKey == null) {
            apiKey = project.getProperties().getProperty("ore.deploy.apikey." + pluginId, null);
        }
        if (apiKey == null && apiKeyLookup != null && apiKeyLookup.canRead()) {
            try (Reader lookupReader = new FileReader(apiKeyLookup)) {
                Properties lookup = new Properties();
                lookup.load(lookupReader);
                apiKey = lookup.getProperty(pluginId, null);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to load API key lookup table", e);
            }
        }
        if (apiKey == null) {
            throw new MojoFailureException("No API key found for the plugin id '" + pluginId + "'!");
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost("https://ore.spongepowered.org/api/projects/" + pluginId + "/versions/" + version);
            MultipartEntityBuilder entity = MultipartEntityBuilder.create();
            entity.addPart("apiKey", new StringBody(apiKey, ContentType.TEXT_PLAIN));
            entity.addPart("channel", new StringBody(channel, ContentType.TEXT_PLAIN));
            entity.addPart("pluginFile", new FileBody(artifactFile, ContentType.APPLICATION_OCTET_STREAM));
            entity.addPart("pluginSig", new FileBody(artifactSigFile, ContentType.APPLICATION_OCTET_STREAM));
            post.setEntity(entity.build());
            try (CloseableHttpResponse response = client.execute(post)) {
                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new MojoFailureException("Plugin upload failed because the remote endpoint returned an unsuccessful response: " + response.getStatusLine());
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Upload failed due to IO error!", e);
        }

    }
}