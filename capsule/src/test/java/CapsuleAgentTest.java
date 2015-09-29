/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 *
 * This program and the accompanying materials are licensed under the terms
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
import capsule.test.Pair;
import co.paralleluniverse.capsule.Jar;
import co.paralleluniverse.capsule.test.CapsuleTestUtils;
import co.paralleluniverse.common.FlexibleClassLoader;
import co.paralleluniverse.common.JarClassLoader;
import co.paralleluniverse.common.PathClassLoader;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author circlespainter
 */
public class CapsuleAgentTest {
	@Test
	public void testWrapperCapsuleAgent() throws Exception {
		final Jar wrapper = new Jar()
				.setAttribute("Manifest-Version", "1.0")
				.setAttribute("Main-Class", "Capsule")
				.setAttribute("Premain-Class", "Capsule")
				.setAttribute("Capsule-Agent", "true")
				.setAttribute("Caplets", "MyCapsule")
				.addClass(TestCapsule.class)
				.addClass(Pair.class)
				.addClass(JarClassLoader.class)
				.addClass(PathClassLoader.class)
				.addClass(FlexibleClassLoader.class)
				.addClass(MyCapsule.class)
				.addClass(Capsule.class);

		final Jar app = new Jar()
				.setAttribute("Manifest-Version", "1.0")
				.setAttribute("Main-Class", Capsule.class.getName())
				.setAttribute("Premain-Class", Capsule.class.getName())
				.setAttribute("Capsule-Agent", "true")
				.setAttribute("Application-Class", MainTest.class.getName())
				.addClass(Capsule.class)
				.addClass(MainTest.class);

		final Path wrapperPath = Files.createTempFile("capsule-agent-test-wrapper", ".jar");
		CapsuleTestUtils.newCapsule(wrapper, wrapperPath); // Create
		final Path appPath = Files.createTempFile("capsule-agent-test-app", ".jar");
		CapsuleTestUtils.newCapsule(app, appPath); // Create
		final Path cacheDir = Files.createTempDirectory("capsule-agent-test-cache");

		final ProcessBuilder pb = new ProcessBuilder("java", "-jar", wrapperPath.toString(), appPath.toString());
		pb.environment().put("CAPSULE_CACHE_DIR", cacheDir.toAbsolutePath().toString());
		assertEquals(0, pb.start().waitFor());

		final Path appCache = cacheDir.resolve("apps").resolve(MainTest.class.getName());
		assertTrue(Files.exists(appCache.resolve(MyCapsule.WRAPPER_AGENT_OK_FNAME)));

		Files.delete(wrapperPath);
		Files.delete(appPath);
		Files.walkFileTree(cacheDir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
