/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import com.google.common.base.Suppliers;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.maven.MavenPublication;

import net.fabricmc.loom.api.ForgeExtensionAPI;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.api.MixinExtensionAPI;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.api.decompilers.architectury.ArchitecturyLoomDecompiler;
import net.fabricmc.loom.api.mappings.layered.spec.LayeredMappingSpecBuilder;
import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.launch.LaunchProviderSettings;
import net.fabricmc.loom.configuration.mods.ModVersionParser;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.providers.mappings.GradleMappingContext;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpec;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilderImpl;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingsDependency;
import net.fabricmc.loom.util.DeprecationHelper;
import net.fabricmc.loom.util.ModPlatform;

/**
 * This class implements the public extension api.
 */
public abstract class LoomGradleExtensionApiImpl implements LoomGradleExtensionAPI {
	private static final String FORGE_PROPERTY = "loom.forge";
	private static final String PLATFORM_PROPERTY = "loom.platform";

	protected final DeprecationHelper deprecationHelper;
	protected final ListProperty<LoomDecompiler> decompilers;
	protected final ListProperty<JarProcessor> jarProcessors;
	protected final ConfigurableFileCollection log4jConfigs;
	protected final RegularFileProperty accessWidener;
	protected final Property<Boolean> shareCaches;
	protected final Property<Boolean> remapArchives;
	protected final Property<String> customManifest;
	protected final Property<Boolean> setupRemappedVariants;
	protected final Property<Boolean> transitiveAccessWideners;
	protected final Property<String> intermediary;

	private final ModVersionParser versionParser;

	private NamedDomainObjectContainer<RunConfigSettings> runConfigs;

	// ===================
	//  Architectury Loom
	// ===================
	private final ListProperty<ArchitecturyLoomDecompiler> archDecompilers;
	private Provider<ModPlatform> platform;
	private boolean silentMojangMappingsLicense = false;
	public Boolean generateSrgTiny = null;
	private final List<String> tasksBeforeRun = Collections.synchronizedList(new ArrayList<>());
	public final List<Consumer<RunConfig>> settingsPostEdit = new ArrayList<>();
	private NamedDomainObjectContainer<LaunchProviderSettings> launchConfigs;

	protected LoomGradleExtensionApiImpl(Project project, LoomFiles directories) {
		this.runConfigs = project.container(RunConfigSettings.class,
				baseName -> new RunConfigSettings(project, baseName));
		this.decompilers = project.getObjects().listProperty(LoomDecompiler.class)
				.empty();
		this.jarProcessors = project.getObjects().listProperty(JarProcessor.class)
				.empty();
		this.log4jConfigs = project.files(directories.getDefaultLog4jConfigFile());
		this.accessWidener = project.getObjects().fileProperty();
		this.shareCaches = project.getObjects().property(Boolean.class)
				.convention(false);
		this.remapArchives = project.getObjects().property(Boolean.class)
				.convention(true);
		this.customManifest = project.getObjects().property(String.class);
		this.setupRemappedVariants = project.getObjects().property(Boolean.class)
				.convention(true);
		this.transitiveAccessWideners = project.getObjects().property(Boolean.class)
				.convention(true);
		this.transitiveAccessWideners.finalizeValueOnRead();
		this.intermediary = project.getObjects().property(String.class)
				.convention("https://maven.fabricmc.net/net/fabricmc/intermediary/%1$s/intermediary-%1$s-v2.jar");

		this.versionParser = new ModVersionParser(project);

		this.deprecationHelper = new DeprecationHelper.ProjectBased(project);
		this.platform = project.provider(Suppliers.memoize(() -> {
			Object platformProperty = project.findProperty(PLATFORM_PROPERTY);

			if (platformProperty != null) {
				return ModPlatform.valueOf(Objects.toString(platformProperty).toUpperCase(Locale.ROOT));
			}

			Object forgeProperty = project.findProperty(FORGE_PROPERTY);

			if (forgeProperty != null) {
				project.getLogger().warn("Project " + project.getPath() + " is using property " + FORGE_PROPERTY + " to enable forge mode. Please use '" + PLATFORM_PROPERTY + " = forge' instead!");
				return Boolean.parseBoolean(Objects.toString(forgeProperty)) ? ModPlatform.FORGE : ModPlatform.FABRIC;
			}

			return ModPlatform.FABRIC;
		})::get);
		this.launchConfigs = project.container(LaunchProviderSettings.class,
				baseName -> new LaunchProviderSettings(project, baseName));
		this.archDecompilers = project.getObjects().listProperty(ArchitecturyLoomDecompiler.class)
				.empty();
	}

	@Override
	public DeprecationHelper getDeprecationHelper() {
		return deprecationHelper;
	}

	@Override
	public RegularFileProperty getAccessWidenerPath() {
		return accessWidener;
	}

	@Override
	public Property<Boolean> getShareRemapCaches() {
		return shareCaches;
	}

	@Override
	public ListProperty<LoomDecompiler> getGameDecompilers() {
		return decompilers;
	}

	@Override
	public ListProperty<JarProcessor> getGameJarProcessors() {
		return jarProcessors;
	}

	@Override
	public Dependency layered(Action<LayeredMappingSpecBuilder> action) {
		LayeredMappingSpecBuilderImpl builder = new LayeredMappingSpecBuilderImpl(this);
		action.execute(builder);
		LayeredMappingSpec builtSpec = builder.build();
		return new LayeredMappingsDependency(getProject(), new GradleMappingContext(getProject(), builtSpec.getVersion().replace("+", "_").replace(".", "_")), builtSpec, builtSpec.getVersion());
	}

	protected abstract String getMinecraftVersion();

	@Override
	public Property<Boolean> getRemapArchives() {
		return remapArchives;
	}

	@Override
	public void runs(Action<NamedDomainObjectContainer<RunConfigSettings>> action) {
		action.execute(runConfigs);
	}

	@Override
	public NamedDomainObjectContainer<RunConfigSettings> getRunConfigs() {
		return runConfigs;
	}

	@Override
	public ConfigurableFileCollection getLog4jConfigs() {
		return log4jConfigs;
	}

	@Override
	public void mixin(Action<MixinExtensionAPI> action) {
		action.execute(getMixin());
	}

	@Override
	public Property<String> getCustomMinecraftManifest() {
		return customManifest;
	}

	@Override
	public Property<Boolean> getSetupRemappedVariants() {
		return setupRemappedVariants;
	}

	@Override
	public String getModVersion() {
		return versionParser.getModVersion();
	}

	@Override
	public Property<Boolean> getEnableTransitiveAccessWideners() {
		return transitiveAccessWideners;
	}

	protected abstract Project getProject();

	protected abstract LoomFiles getFiles();

	@Override
	public Property<String> getIntermediaryUrl() {
		return intermediary;
	}

	@Override
	public void disableDeprecatedPomGeneration(MavenPublication publication) {
		net.fabricmc.loom.configuration.MavenPublication.excludePublication(publication);
	}

	@Override
	public void silentMojangMappingsLicense() {
		this.silentMojangMappingsLicense = true;
	}

	@Override
	public boolean isSilentMojangMappingsLicenseEnabled() {
		return silentMojangMappingsLicense;
	}

	@Override
	public Provider<ModPlatform> getPlatform() {
		return platform;
	}

	@Override
	public void setGenerateSrgTiny(Boolean generateSrgTiny) {
		this.generateSrgTiny = generateSrgTiny;
	}

	@Override
	public boolean shouldGenerateSrgTiny() {
		if (generateSrgTiny != null) {
			return generateSrgTiny;
		}

		return isForge();
	}

	@Override
	public void launches(Action<NamedDomainObjectContainer<LaunchProviderSettings>> action) {
		action.execute(launchConfigs);
	}

	@Override
	public NamedDomainObjectContainer<LaunchProviderSettings> getLaunchConfigs() {
		return launchConfigs;
	}

	@Override
	public List<String> getTasksBeforeRun() {
		return tasksBeforeRun;
	}

	@Override
	public List<Consumer<RunConfig>> getSettingsPostEdit() {
		return settingsPostEdit;
	}

	@Override
	public void forge(Action<ForgeExtensionAPI> action) {
		action.execute(getForge());
	}

	@Override
	public ListProperty<ArchitecturyLoomDecompiler> getArchGameDecompilers() {
		return archDecompilers;
	}

	// This is here to ensure that LoomGradleExtensionApiImpl compiles without any unimplemented methods
	private final class EnsureCompile extends LoomGradleExtensionApiImpl {
		private EnsureCompile() {
			super(null, null);
			throw new RuntimeException();
		}

		@Override
		public DeprecationHelper getDeprecationHelper() {
			throw new RuntimeException("Yeah... something is really wrong");
		}

		@Override
		protected Project getProject() {
			throw new RuntimeException("Yeah... something is really wrong");
		}

		@Override
		protected LoomFiles getFiles() {
			throw new RuntimeException("Yeah... something is really wrong");
		}

		@Override
		public MixinExtension getMixin() {
			throw new RuntimeException("Yeah... something is really wrong");
		}

		@Override
		protected String getMinecraftVersion() {
			throw new RuntimeException("Yeah... something is really wrong");
		}

		@Override
		public ForgeExtensionAPI getForge() {
			throw new RuntimeException("Yeah... something is really wrong");
		}
	}
}