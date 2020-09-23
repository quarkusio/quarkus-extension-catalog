//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS io.quarkus:quarkus-platform-descriptor-resolver-json:1.7.0.Final
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.1

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.platform.descriptor.QuarkusPlatformDescriptor;
import io.quarkus.platform.descriptor.resolver.json.QuarkusJsonPlatformDescriptorResolver;
import io.quarkus.registry.RepositoryIndexer;
import io.quarkus.registry.builder.RegistryBuilder;
import io.quarkus.registry.catalog.model.Extension;
import io.quarkus.registry.catalog.model.Platform;
import io.quarkus.registry.catalog.model.Repository;
import io.quarkus.registry.catalog.spi.ArtifactResolver;
import io.quarkus.registry.model.ImmutableExtension;
import io.quarkus.registry.model.ImmutablePlatform;
import io.quarkus.registry.model.ImmutableRegistry;
import io.quarkus.registry.model.Registry;
import io.quarkus.registry.model.Release;
import org.apache.maven.artifact.versioning.ComparableVersion;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Command(name = "quarkusindexcatalog", mixinStandardHelpOptions = true, version = "quarkusindexcatalog 0.1",
        description = "Indexes a catalog repository to a format read by DefaultExtensionRegistry")
class quarkusindexcatalog implements Callable<Integer> {

    @Option(names = {"-p", "--repository-path"}, description = "The repository path to index", required = true)
    private Path repositoryPath;

    @Option(names = {"-o", "--output-file", "--output-path"}, description = "The output file/directory. A directory if --split is true, a file otherwise", required = true)
    private Path outputPath;

    @Option(names = {"-s", "--split"}, description = "Split into versioned directories")
    private boolean split;

    public static void main(String... args) {
        int exitCode = new CommandLine(new quarkusindexcatalog()).execute(args);
        System.exit(exitCode);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Integer call() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);
        Repository repository = Repository.parse(repositoryPath, mapper);
        RepositoryIndexer indexer = new RepositoryIndexer(new DefaultArtifactResolver());
        RegistryBuilder builder = new RegistryBuilder();
        indexer.index(repository, builder);
        // Make sure the parent directory exists
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Registry registry = builder.build();
        if (split) {
            // Split into smaller files per version
            Files.createDirectories(outputPath);
            // Write the full output
            mapper.writeValue(outputPath.resolve("registry.json").toFile(), registry);
            mapper.writeValue(outputPath.resolve("versions.json").toFile(), registry.getCoreVersions());
            for (Map.Entry<ComparableVersion, Map<String, String>> entry : registry.getCoreVersions().entrySet()) {
                String version = entry.getKey().toString();
                Path versionedDir = Files.createDirectory(outputPath.resolve(version));
                ImmutableRegistry.Builder versionedRegistryBuilder = ImmutableRegistry.builder();
                List<ImmutableExtension> extensions = registry.getExtensions().stream()
                        .filter(e -> e.getReleases().stream().anyMatch(r -> version.equals(r.getRelease().getQuarkusCore())))
                        .map(e -> ImmutableExtension.builder().from(e)
                                .releases(e.getReleases().stream().filter(r -> version.equals(r.getRelease().getQuarkusCore())).collect(toList())).build())
                        .collect(toList());
                Set<String> categoriesIds = extensions.stream()
                        .map(e -> (List<String>) e.getMetadata().get("categories"))
                        .filter(Objects::nonNull)
                        .flatMap(Collection::stream)
                        .collect(toSet());
                ImmutableRegistry newRegistry = versionedRegistryBuilder.putCoreVersions(entry)
                        .addAllCategories(registry.getCategories().stream().filter(c -> categoriesIds.contains(c.getId())).collect(toList()))
                        .addAllPlatforms(registry.getPlatforms().stream()
                                                 .filter(p -> p.getReleases().stream().anyMatch(r -> version.equals(r.getQuarkusCore())))
                                                 .map(p -> ImmutablePlatform.builder().from(p)
                                                         .releases(p.getReleases().stream().filter(r -> version.equals(r.getQuarkusCore())).collect(toList()))
                                                         .build())
                                                 .collect(toList()))
                        .addAllExtensions(extensions)
                        .build();
                mapper.writeValue(versionedDir.resolve("registry.json").toFile(), newRegistry);
            }
        } else {
            // Just write the output
            mapper.writeValue(outputPath.toFile(), registry);
        }
        return 0;
    }


    class DefaultArtifactResolver implements ArtifactResolver {

        private final ObjectMapper yamlReader;

        private final QuarkusJsonPlatformDescriptorResolver resolver;

        private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

        public DefaultArtifactResolver() {
            this.yamlReader = new ObjectMapper(new YAMLFactory())
                    .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);
            this.resolver = QuarkusJsonPlatformDescriptorResolver.newInstance();
        }

        @Override
        public QuarkusPlatformDescriptor resolvePlatform(Platform platform, Release release) throws IOException {
            return resolver.resolveFromBom(platform.getGroupId(), platform.getArtifactId(), release.getVersion());
        }

        @Override
        public io.quarkus.dependencies.Extension resolveExtension(Extension extension, Release release) throws IOException {
            URL extensionJarURL = getExtensionJarURL(extension, release);
            try {
                return yamlReader.readValue(extensionJarURL, io.quarkus.dependencies.Extension.class);
            } catch (FileNotFoundException e) {
                // META-INF/quarkus-extension.yaml does not exist in JAR
                return new io.quarkus.dependencies.Extension(extension.getGroupId(), extension.getArtifactId(),
                                                             release.getVersion());
            }
        }

        URL getPlatformJSONURL(Platform platform, Release release) {
            try {
                return new URL(MessageFormat.format("{0}{1}/{2}/{3}/{2}-{3}.json",
                                                    Objects.toString(release.getRepositoryURL(), MAVEN_CENTRAL),
                                                    platform.getGroupIdJson().replace('.', '/'),
                                                    platform.getArtifactIdJson(),
                                                    release.getVersion()));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Error while building JSON URL", e);
            }
        }

        URL getExtensionJarURL(Extension extension, Release release) {
            try {
                return new URL(MessageFormat.format("jar:{0}{1}/{2}/{3}/{2}-{3}.jar!/META-INF/quarkus-extension.yaml",
                                                    Objects.toString(release.getRepositoryURL(), MAVEN_CENTRAL),
                                                    extension.getGroupId().replace('.', '/'),
                                                    extension.getArtifactId(),
                                                    release.getVersion()));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Error while building JSON URL", e);
            }
        }
    }
}
