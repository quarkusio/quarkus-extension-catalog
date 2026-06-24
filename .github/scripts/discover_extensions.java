///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.apache.maven:maven-model:3.9.9
//DEPS info.picocli:picocli:4.7.6
//JAVA 17

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Walks a GitHub repo's Maven module tree to discover Quarkus extensions.
 * A runtime extension is identified by having a sibling module whose
 * artifactId is {@code <runtimeArtifactId>-deployment}.
 *
 * Output: one {@code groupId:artifactId} line per runtime extension, sorted.
 */
@Command(name = "discover_extensions")
class discover_extensions implements Callable<Integer> {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final MavenXpp3Reader pomReader = new MavenXpp3Reader();
    private final String token = System.getenv("GH_TOKEN");

    @Parameters(index = "0", description = "GitHub repo (e.g. quarkiverse/quarkus-foo)")
    String repo;

    @Parameters(index = "1", defaultValue = "main", description = "Git ref (branch or tag)")
    String ref;

    public static void main(String... args) {
        int exitCode = new CommandLine(new discover_extensions()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        List<String[]> leafModules = new ArrayList<>();
        walkModules("", null, leafModules);

        // artifactId → groupId
        Map<String, String> byArtifact = new LinkedHashMap<>();
        for (String[] mod : leafModules) {
            byArtifact.put(mod[1], mod[0]);
        }

        List<String> runtimeArtifacts = byArtifact.keySet().stream()
                .filter(a -> !a.endsWith("-deployment"))
                .filter(a -> byArtifact.containsKey(a + "-deployment"))
                .sorted()
                .collect(Collectors.toList());

        for (String artifactId : runtimeArtifacts) {
            System.out.println(byArtifact.get(artifactId) + ":" + artifactId);
        }

        return 0;
    }

    private void walkModules(String basePath, String parentGroupId, List<String[]> collected) {
        String pomPath = basePath.isEmpty() ? "pom.xml" : basePath + "/pom.xml";
        Model model = fetchPom(pomPath);
        if (model == null) {
            return;
        }

        String groupId = model.getGroupId();
        if (groupId == null || groupId.startsWith("${")) {
            if (model.getParent() != null) {
                groupId = model.getParent().getGroupId();
            }
        }
        if (groupId == null || groupId.startsWith("${")) {
            groupId = parentGroupId;
        }

        List<String> modules = model.getModules();
        if (modules == null || modules.isEmpty()) {
            if (!"pom".equals(model.getPackaging())) {
                String artifactId = model.getArtifactId();
                if (artifactId != null && !artifactId.startsWith("${") && groupId != null) {
                    collected.add(new String[]{groupId, artifactId});
                }
            }
            return;
        }

        for (String module : modules) {
            String childPath = basePath.isEmpty() ? module : basePath + "/" + module;
            walkModules(childPath, groupId, collected);
        }
    }

    private Model fetchPom(String path) {
        try {
            String url = String.format(
                    "https://raw.githubusercontent.com/%s/%s/%s", repo, ref, path);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15));
            if (token != null && !token.isEmpty()) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<InputStream> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                return null;
            }
            try (InputStream is = response.body()) {
                return pomReader.read(is);
            }
        } catch (Exception e) {
            System.err.println("Warning: could not read " + path + ": " + e.getMessage());
            return null;
        }
    }
}
