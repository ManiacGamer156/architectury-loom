/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
/**
 * This class is stored in the gradle extension, and should not be used outside of loom.
 * It contains data/info related to the current project
 */
public final class LoomProjectData {
	private final Project project;
	private final Map<String, NamedDomainObjectProvider<Configuration>> lazyConfigurations = new HashMap<>();

	public LoomProjectData(Project project) {
		this.project = Objects.requireNonNull(project);
	}

	public NamedDomainObjectProvider<Configuration> createLazyConfiguration(String name) {
		NamedDomainObjectProvider<Configuration> provider = project.getConfigurations().register(name);

		if (lazyConfigurations.containsKey(name)) {
			throw new IllegalStateException("Duplicate configuration name" + name);
		}

		lazyConfigurations.put(name, provider);

		return provider;
	}

	public NamedDomainObjectProvider<Configuration> getLazyConfigurationProvider(String name) {
		NamedDomainObjectProvider<Configuration> provider = lazyConfigurations.get(name);

		if (provider == null) {
			throw new NullPointerException("Could not find provider with name: " + name);
		}

		return provider;
	}
}