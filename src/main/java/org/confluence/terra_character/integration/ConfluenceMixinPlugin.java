package org.confluence.terra_character.integration;

import net.neoforged.fml.loading.FMLLoader;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Mixin discovery happens before ModList; use the loader's discovered mod list here. */
public final class ConfluenceMixinPlugin implements IMixinConfigPlugin {
	private boolean enabled;

	@Override
	public void onLoad(String mixinPackage) {
		enabled =
				FMLLoader.getLoadingModList().getMods().stream()
						.anyMatch(mod -> mod.getModId().equals("confluence"));
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return enabled;
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public void preApply(
			String targetClassName,
			ClassNode targetClass,
			String mixinClassName,
			IMixinInfo info) {}

	@Override
	public void postApply(
			String targetClassName,
			ClassNode targetClass,
			String mixinClassName,
			IMixinInfo info) {}
}
