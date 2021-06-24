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

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "deploy", threadSafe = true)
public class OreDeployMojo extends AbstractMojo
{
    private static final String ORE_BASE_URL = "https://ore.spongepowered.org";
//    private static final String ORE_BASE_URL = "https://staging-ore.spongeproject.net";
    private static final String ORE_DEPLOY_ENDPOINT = "/api/v2/projects/%s/versions";
    private static final String ORE_AUTH_ENDPOINT = "/api/v2/authenticate";

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project = null;

    @Parameter(defaultValue = "${ore.deploy.pluginId}", required = true, readonly = true)
    private String pluginId = null;

    @Parameter(defaultValue = "${ore.deploy.channel.release}", readonly = true)
    private String releaseChannel = "release";

    @Parameter(defaultValue = "${ore.deploy.channel.snapshot}", readonly = true)
    private String snapshotChannel = "snapshot";

    @Parameter(defaultValue = "${ore.deploy.apikey}", readonly = true)
    private String apiKey = null;

    @Parameter(defaultValue = "${project.build.finalName}.jar")
    private String fileName = null;

    @Parameter(defaultValue = "${ore.deploy.classifier}")
    private String classifier = null;

    @Parameter(defaultValue = "${ore.deploy.fallbackToMainArtifact}")
    private boolean fallbackToMainArtifact = true;

    @Parameter(defaultValue = "${ore.deploy.url}", readonly = true)
    private String oreUrl = null;


    /**
     * {@inheritDoc}
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException
    {
        Artifact jarArtifact = lookupArtifact(project, classifier, "jar");
        if (oreUrl == null)
        {
            oreUrl = ORE_BASE_URL;
        }

        if (jarArtifact == null)
        {
            if (fallbackToMainArtifact)
            {
                jarArtifact = project.getArtifact();
            }
            else
            {
                throw new MojoFailureException("Artifact with classifier '" + classifier + "' was not found!");
            }
        }

        boolean isSnapshot = jarArtifact.isSnapshot();
        final String channel = isSnapshot ? snapshotChannel : releaseChannel;

        File artifactJarFile = jarArtifact.getFile();

        if (!artifactJarFile.isFile() || !artifactJarFile.canRead())
        {
            throw new MojoFailureException("Unable to read the jar artifact: " + artifactJarFile.getPath());
        }

        String apiKey = this.apiKey;
        if (apiKey == null)
        {
            apiKey = project.getProperties().getProperty("ore.deploy.apikey", null);
        }

        final String authUrl = oreUrl + ORE_AUTH_ENDPOINT;
        getLog().info("Authenticating with ore " + authUrl + "...");
        String session;
        try (CloseableHttpClient client = HttpClients.createDefault())
        {
            HttpPost post = new HttpPost(authUrl);
            post.setHeader("Authorization", "OreApi apikey=\"" + apiKey + "\"");
            try (CloseableHttpResponse response = client.execute(post)) {
                if (response.getStatusLine().getStatusCode() != 200)
                {
                    String responseString = EntityUtils.toString(response.getEntity());
                    throw new MojoFailureException("Authentication failed because the remote endpoint returned an unsuccessful response: " + response.getStatusLine() + "\n" + responseString);
                }
                final String responseString = EntityUtils.toString(response.getEntity());
                final Pattern pattern = Pattern.compile("\"session\":\"(.+)\",\"expires\"");
                final Matcher matcher = pattern.matcher(responseString);
                if (matcher.find())
                {
                    session = matcher.group(1);
                }
                else
                {
                    throw new IllegalStateException("Authentication failed could not read session key");
                }
            }
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Authentication failed due to IO error!", e);
        }

        final String url = oreUrl + String.format(ORE_DEPLOY_ENDPOINT, pluginId);
        final String jarFileName = fileName;
        final String tags = String.format("{\"Channel\":\"%s\"}", channel);
        getLog().info(String.format("Uploading plugin %s v%s (%s) to %s in channel: %s", pluginId, jarArtifact.getBaseVersion(), jarFileName, url, channel));

        try (CloseableHttpClient client = HttpClients.createDefault())
        {
            HttpPost post = new HttpPost(url);
            MultipartEntityBuilder entity = MultipartEntityBuilder.create();
            String deployVersionInfo = "{" + String.format("\"create_forum_post\":%s,", "false") +
                String.format("\"description\":\"%s\",", "")+
                String.format("\"tags\": %s", tags)+
                "}";
            getLog().info(deployVersionInfo);
            entity.addPart("plugin-info", stringBody(deployVersionInfo));

            entity.addPart("plugin-file", fileBody(artifactJarFile, jarFileName));
            post.setEntity(entity.build());
            post.setHeader("Authorization", "OreApi session=\"" + session + "\"");
            try (CloseableHttpResponse response = client.execute(post))
            {
                if (response.getStatusLine().getStatusCode() != 201)
                {
                    String responseString = EntityUtils.toString(response.getEntity());
                    throw new MojoFailureException("Plugin upload failed because the remote endpoint returned an unsuccessful response: " + response.getStatusLine() + "\n" + responseString);
                }
            }
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Upload failed due to IO error!", e);
        }

    }

    private FileBody fileBody(File artifactJarFile, String jarFileName)
    {
        return new FileBody(artifactJarFile, ContentType.APPLICATION_OCTET_STREAM, jarFileName);
    }

    private StringBody stringBody(String channel)
    {
        return new StringBody(channel, ContentType.TEXT_PLAIN);
    }

    private static Artifact lookupArtifact(MavenProject project, String classifier, String type)
    {
        for (Artifact artifact : project.getAttachedArtifacts())
        {

            if (Objects.equals(artifact.getClassifier(), classifier) && artifact.getType().equals(type))
            {
                return artifact;
            }
        }
        return null;
    }
}
