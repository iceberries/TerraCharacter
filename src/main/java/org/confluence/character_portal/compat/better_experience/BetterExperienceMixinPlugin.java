package org.confluence.character_portal.compat.better_experience;

import net.neoforged.fml.loading.FMLLoader;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class BetterExperienceMixinPlugin implements IMixinConfigPlugin {
	private boolean enabled;
	@Override
	public void onLoad(String mixinPackage) {
		enabled = FMLLoader.getLoadingModList().getMods().stream()
				.anyMatch(mod -> mod.getModId().equals("better_experience"));
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return enabled && getClass().getClassLoader().getResource(targetClassName.replace('.', '/') + ".class") != null;
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
	public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
	@Override
	public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
