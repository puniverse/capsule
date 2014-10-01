/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. and Contributors. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import static capsule.DependencyManagerImpl.DEFAULT_LOCAL_MAVEN;
import static capsule.DependencyManagerImpl.emptyToNull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.crypto.DefaultSettingsDecrypter;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.ConservativeAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.cipher.PlexusCipherException;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

/**
 * Reads Maven's settings.xml
 */
final class UserSettings {
    private static final String SETTINGS_XML = "settings.xml";
    private static final String ENV_MAVEN_HOME = "M2_HOME";
    private static final String PROP_MAVEN_HOME = "maven.home";
    private static final String PROP_OS_NAME = "os.name";

    private static final Path MAVEN_HOME = getMavenHome();

    private final Settings settings;

    private static final UserSettings INSTANCE = new UserSettings();

    public static UserSettings getInstance() {
        return INSTANCE;
    }

    private UserSettings() {
        final DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
        request.setUserSettingsFile(DEFAULT_LOCAL_MAVEN.resolve(SETTINGS_XML).toFile());
        request.setGlobalSettingsFile(MAVEN_HOME != null ? MAVEN_HOME.resolve("conf").resolve(SETTINGS_XML).toFile() : null);
        request.setSystemProperties(getSystemProperties());

        try {
            this.settings = new DefaultSettingsBuilderFactory().newInstance().build(request).getEffectiveSettings();
            final SettingsDecryptionResult result = newDefaultSettingsDecrypter().decrypt(new DefaultSettingsDecryptionRequest(settings));

            settings.setServers(result.getServers());
            settings.setProxies(result.getProxies());
        } catch (SettingsBuildingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path getMavenHome() {
        String mhome = emptyToNull(System.getenv(ENV_MAVEN_HOME));
        if (mhome == null)
            mhome = System.getProperty(PROP_MAVEN_HOME);
        return mhome != null ? Paths.get(mhome) : null;
    }

    private static Properties getSystemProperties() {
        Properties props = new Properties();
        getEnvProperties(props);
        props.putAll(System.getProperties());
        return props;
    }

    private static Properties getEnvProperties(Properties props) {
        if (props == null)
            props = new Properties();

        boolean envCaseInsensitive = isWindows();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String key = entry.getKey();
            if (envCaseInsensitive)
                key = key.toUpperCase(Locale.ENGLISH);
            key = "env." + key;
            props.put(key, entry.getValue());
        }
        return props;
    }

    public ProxySelector getProxySelector() {
        final DefaultProxySelector selector = new DefaultProxySelector();

        for (Proxy proxy : settings.getProxies()) {
            AuthenticationBuilder auth = new AuthenticationBuilder();
            auth.addUsername(proxy.getUsername()).addPassword(proxy.getPassword());
            selector.add(new org.eclipse.aether.repository.Proxy(proxy.getProtocol(), proxy.getHost(),
                    proxy.getPort(), auth.build()),
                    proxy.getNonProxyHosts());
        }

        return selector;
    }

    public MirrorSelector getMirrorSelector() {
        final DefaultMirrorSelector selector = new DefaultMirrorSelector();

        for (Mirror mirror : settings.getMirrors())
            selector.add(String.valueOf(mirror.getId()), mirror.getUrl(), mirror.getLayout(), false, mirror.getMirrorOf(), mirror.getMirrorOfLayouts());

        return selector;
    }

    public AuthenticationSelector getAuthSelector() {
        final DefaultAuthenticationSelector selector = new DefaultAuthenticationSelector();

        for (Server server : settings.getServers()) {
            AuthenticationBuilder auth = new AuthenticationBuilder();
            auth.addUsername(server.getUsername()).addPassword(server.getPassword());
            auth.addPrivateKey(server.getPrivateKey(), server.getPassphrase());
            selector.add(server.getId(), auth.build());
        }

        return new ConservativeAuthenticationSelector(selector);
    }

    private static boolean isWindows() {
        return System.getProperty(PROP_OS_NAME).toLowerCase().startsWith("windows");
    }

    private static DefaultSettingsDecrypter newDefaultSettingsDecrypter() {
        /*
         * see:
         * http://git.eclipse.org/c/aether/aether-ant.git/tree/src/main/java/org/eclipse/aether/internal/ant/AntSettingsDecryptorFactory.java
         * http://git.eclipse.org/c/aether/aether-ant.git/tree/src/main/java/org/eclipse/aether/internal/ant/AntSecDispatcher.java
         */
        DefaultSecDispatcher secDispatcher = new DefaultSecDispatcher() {
            {
                _configurationFile = "~/.m2/settings-security.xml";
                try {
                    _cipher = new DefaultPlexusCipher();
                } catch (PlexusCipherException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        final DefaultSettingsDecrypter decrypter = new DefaultSettingsDecrypter();

        try {
            java.lang.reflect.Field field = decrypter.getClass().getDeclaredField("securityDispatcher");
            field.setAccessible(true);
            field.set(decrypter, secDispatcher);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }

        return decrypter;
    }
}
