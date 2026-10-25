package org.confluence.terra_character.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;

/** A compact, keyboard-accessible wheel: selecting a card never executes its action. */
final class ActionCarousel extends AbstractWidget {
	private static final int CARD_HEIGHT = 40;
	private final List<Action> actions;
	private final Consumer<String> selection;
	private final List<Hit> hits = new ArrayList<>();
	private int selected, tooltipIndex = -1;
	private double position, target, scroll;
	private long lastFrame;

	record Action(String id, Component label, Icon icon, boolean enabled, Runnable execute) {
		Action {
			Objects.requireNonNull(id);
			Objects.requireNonNull(label);
			Objects.requireNonNull(icon);
			Objects.requireNonNull(execute);
		}
	}

	private record Hit(int index, int x, int y, int width, int height, double offset) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	enum Icon {
		CREATE(
				"create",
				0xFF85D38A,
				"  #####  ",
				"  #   #  ",
				"  #   #  ",
				"  #####  ",
				"   ###   ",
				" ##### # ",
				" #### ###",
				" ##### # ",
				"         "),
		PLAY(
				"play",
				0xFFFFD15C,
				"  #      ",
				"  ##     ",
				"  ###    ",
				"  ####   ",
				"  #####  ",
				"  ####   ",
				"  ###    ",
				"  ##     ",
				"  #      "),
		BACK(
				"back",
				0xFFBCCDE1,
				"    #    ",
				"   ##    ",
				"  ###    ",
				" ####### ",
				"######## ",
				" ####### ",
				"  ###    ",
				"   ##    ",
				"    #    "),
		TRASH(
				"trash",
				0xFFB7B7C5,
				"   ###   ",
				" ####### ",
				"         ",
				"  #####  ",
				"  # # #  ",
				"  # # #  ",
				"  # # #  ",
				"  #   #  ",
				"  #####  "),
		DELETE(
				"delete",
				0xFFE78787,
				"         ",
				" ##   ## ",
				"  ## ##  ",
				"   ###   ",
				"    #    ",
				"   ###   ",
				"  ## ##  ",
				" ##   ## ",
				"         "),
		RESTORE(
				"restore",
				0xFF85D38A,
				"     ### ",
				"    #   #",
				" # #    #",
				" ##     #",
				"#####   #",
				"        #",
				" #     # ",
				"  #####  ",
				"         "),
		RECOVER(
				"recover",
				0xFF84CBE8,
				"     ### ",
				"    #   #",
				" # #    #",
				" ##     #",
				"#####   #",
				"        #",
				" #     # ",
				"  #####  ",
				"         ");
		private final ResourceLocation sprite, texture;
		private final int color;
		private final String[] pixels;

		Icon(String id, int color, String... pixels) {
			this.sprite = ResourceLocation.parse("terra_character:actions/" + id);
			this.texture =
					ResourceLocation.parse(
							"terra_character:textures/gui/sprites/actions/" + id + ".png");
			this.color = color;
			this.pixels = pixels;
		}

		private void render(GuiGraphics g, int x, int y, int size, boolean enabled) {
			if (Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()) {
				g.setColor(1, 1, 1, enabled ? 1 : 0.4F);
				try {
					g.blitSprite(sprite, x, y, size, size);
				} finally {
					g.setColor(1, 1, 1, 1);
				}
				return;
			}
			for (int row = 0; row < pixels.length; row++)
				for (int column = 0; column < pixels[row].length(); column++)
					if (pixels[row].charAt(column) == '#') {
						int left = x + column * size / 9, top = y + row * size / 9;
						int right = x + (column + 1) * size / 9, bottom = y + (row + 1) * size / 9;
						g.fill(left + 1, top + 1, right + 1, bottom + 1, 0xFF242424);
						g.fill(left, top, right, bottom, enabled ? color : 0xFF777777);
					}
		}
	}

	ActionCarousel(
			int x,
			int y,
			int width,
			Collection<Action> actions,
			String initial,
			Consumer<String> selection) {
		super(x, y, width, 46, Component.empty());
		var unique = new LinkedHashMap<String, Action>();
		for (var action : actions)
			if (unique.putIfAbsent(action.id(), action) != null)
				throw new IllegalArgumentException("Duplicate carousel action: " + action.id());
		if (unique.isEmpty()) throw new IllegalArgumentException("Empty action carousel");
		this.actions = List.copyOf(unique.values());
		this.selection = Objects.requireNonNull(selection);
		for (int i = 0; i < this.actions.size(); i++)
			if (this.actions.get(i).id().equals(initial)) selected = i;
		position = target = selected;
		setMessage(this.actions.get(selected).label());
	}

	private void cycle(int step) {
		if (step == 0) return;
		selected = Math.floorMod(selected + step, actions.size());
		target += step;
		setMessage(actions.get(selected).label());
		selection.accept(actions.get(selected).id());
	}

	private void select(int index) {
		int step = index - selected;
		if (step > actions.size() / 2) step -= actions.size();
		else if (step < -actions.size() / 2) step += actions.size();
		cycle(step);
	}

	private void execute() {
		var action = actions.get(selected);
		if (!action.enabled()) return;
		playDownSound(Minecraft.getInstance().getSoundManager());
		action.execute().run();
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (button != 0 || !visible || !isMouseOver(x, y)) return false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			var hit = hits.get(i);
			if (!hit.contains(x, y)) continue;
			if (hit.index() == selected && Math.abs(hit.offset()) < 0.25) execute();
			else {
				select(hit.index());
				playDownSound(Minecraft.getInstance().getSoundManager());
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (!isMouseOver(x, y) || !visible) return false;
		scroll += Math.clamp(vertical != 0 ? -vertical : horizontal, -4, 4);
		int step = (int) scroll;
		scroll -= step;
		cycle(step);
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		if (!isFocused()) return false;
		if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP) {
			cycle(-1);
			return true;
		}
		if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_DOWN) {
			cycle(1);
			return true;
		}
		if (key == GLFW.GLFW_KEY_ENTER
				|| key == GLFW.GLFW_KEY_KP_ENTER
				|| key == GLFW.GLFW_KEY_SPACE) {
			execute();
			return true;
		}
		return super.keyPressed(key, scan, modifiers);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	@Override
	protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
		long now = Util.getMillis();
		double elapsed = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
		lastFrame = now;
		position += (target - position) * (1 - Math.exp(-elapsed / 65));
		if (Math.abs(target - position) < 0.001) position = target;
		hits.clear();
		int center = getX() + getWidth() / 2;
		for (int i = 0; i < actions.size(); i++) {
			double offset = i - position;
			offset -= Math.floor((offset + actions.size() / 2D) / actions.size()) * actions.size();
			if (Math.abs(offset) > 2.5) continue;
			float prominence = 1 - Math.min(1, (float) Math.abs(offset) / 2);
			int w = Math.round(Mth.lerp(prominence, 28, 44));
			int h = Math.round(Mth.lerp(prominence, 28, CARD_HEIGHT));
			int x = center + (int) Math.round(offset * 40) - w / 2;
			int y = getY() + Math.round((1 - prominence) * 6);
			hits.add(new Hit(i, x, y, w, h, offset));
		}
		hits.sort(Comparator.comparingDouble((Hit hit) -> Math.abs(hit.offset())).reversed());
		int hovered = -1;
		for (var hit : hits) {
			var action = actions.get(hit.index());
			boolean over = hit.contains(mouseX, mouseY);
			if (over) hovered = hit.index();
			EditorUi.frame(
					g, hit.x(), hit.y(), hit.width(), hit.height(), hit.index() == selected, over);
			if (!action.enabled())
				g.fill(
						hit.x() + 4,
						hit.y() + 4,
						hit.x() + hit.width() - 4,
						hit.y() + hit.height() - 4,
						0x55000000);
			int size = Math.abs(hit.offset()) < 0.5 ? 23 : 18;
			action.icon()
					.render(
							g,
							hit.x() + (hit.width() - size) / 2,
							hit.y() + (hit.height() - size) / 2,
							size,
							action.enabled());
		}
		int tip = hovered >= 0 ? hovered : selected;
		if (tooltipIndex != tip) {
			tooltipIndex = tip;
			var action = actions.get(tip);
			var text =
					action.label().copy().append("\n").append(ClientProfiles.text("actions.help"));
			if (!action.enabled())
				text.append("\n").append(ClientProfiles.text("actions.select_first"));
			setTooltip(Tooltip.create(text));
		}
		int dotsWidth = actions.size() * 5 - 2;
		for (int i = 0; i < actions.size(); i++)
			g.fill(
					center - dotsWidth / 2 + i * 5,
					getY() + 43,
					center - dotsWidth / 2 + i * 5 + 3,
					getY() + 45,
					i == selected ? EditorUi.GOLD : 0xFF777777);
	}
}
