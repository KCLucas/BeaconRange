package com.kclucas.beaconrange.client;

import com.kclucas.beaconrange.client.onboarding.Onboarding;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Einstellungen als Liste: pro Zeile links Name und Erklärung, rechts der Schalter und ein Zurücksetzen-Knopf,
 * gegliedert durch Abschnitts-Überschriften.
 */
public class BeaconRangeConfigScreen extends Screen {

	private static final int CONTROL_WIDTH = 120;
	private static final int RESET_WIDTH = 20;
	private static final int GAP = 4;
	private static final int OPTION_HEIGHT = 38;
	private static final int SECTION_HEIGHT = 26;

	private static final int COLOR_ACCENT = 0xFF4FE3FF;
	private static final int COLOR_LABEL = 0xFFFFFFFF;
	private static final int COLOR_DESCRIPTION = 0xFF9AA0A6;
	private static final int COLOR_ROW_HOVER = 0x16FFFFFF;

	private static final Component ON = CommonComponents.OPTION_ON.copy().withStyle(ChatFormatting.GREEN);
	private static final Component OFF = CommonComponents.OPTION_OFF.copy().withStyle(ChatFormatting.RED);

	private final @Nullable Screen parent;
	/** Wird in {@link #init()} jedes Mal neu angelegt - sonst bliebe nach einem Neuaufbau die alte Liste darunter stehen. */
	private HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final List<Runnable> resetters = new ArrayList<>();
	private @Nullable OptionList list;

	public BeaconRangeConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("beaconrange.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		BeaconRangeConfig config = BeaconRangeClient.getConfig();
		BeaconRangeConfig defaults = new BeaconRangeConfig();
		this.resetters.clear();
		this.layout = new HeaderAndFooterLayout(this);

		this.layout.addTitleHeader(this.title, this.font);

		OptionList list = new OptionList(this.minecraft, this.width, this.layout);
		list.add(new SectionRow(Component.translatable("beaconrange.config.section.display")));
		list.add(toggle("beaconrange.config.show_on_look", config.showOnLook, defaults.showOnLook,
				value -> config.showOnLook = value));
		list.add(toggle("beaconrange.config.look_info", config.lookInfo, defaults.lookInfo,
				value -> config.lookInfo = value));
		list.add(option(Component.translatable("beaconrange.config.walls"), BeaconRangeConfig.WallStyle::description,
				CycleButton.<BeaconRangeConfig.WallStyle>builder(BeaconRangeConfig.WallStyle::label)
						.withValues(BeaconRangeConfig.WallStyle.values())
						.withInitialValue(config.walls),
				defaults.walls, value -> config.walls = value));
		Component opacityDescription = Component.translatable("beaconrange.config.opacity.desc");
		list.add(option(Component.translatable("beaconrange.config.opacity"), value -> opacityDescription,
				CycleButton.<BeaconRangeConfig.Opacity>builder(BeaconRangeConfig.Opacity::label)
						.withValues(BeaconRangeConfig.Opacity.values())
						.withInitialValue(config.opacity),
				defaults.opacity, value -> config.opacity = value));
		list.add(toggle("beaconrange.config.edges", config.edges, defaults.edges, value -> config.edges = value));
		list.add(toggle("beaconrange.config.height_line", config.heightLine, defaults.heightLine,
				value -> config.heightLine = value));

		list.add(new SectionRow(Component.translatable("beaconrange.config.section.colors")));
		list.add(option(Component.translatable("beaconrange.config.color_mode"), BeaconRangeConfig.ColorMode::description,
				CycleButton.<BeaconRangeConfig.ColorMode>builder(BeaconRangeConfig.ColorMode::label)
						.withValues(BeaconRangeConfig.ColorMode.values())
						.withInitialValue(config.colorMode),
				defaults.colorMode, value -> config.colorMode = value));
		list.add(levelColor(1, config.level1, defaults.level1, value -> config.level1 = value));
		list.add(levelColor(2, config.level2, defaults.level2, value -> config.level2 = value));
		list.add(levelColor(3, config.level3, defaults.level3, value -> config.level3 = value));
		list.add(levelColor(4, config.level4, defaults.level4, value -> config.level4 = value));

		list.add(new SectionRow(Component.translatable("beaconrange.config.section.conduits")));
		list.add(toggle("beaconrange.config.conduits", config.conduits, defaults.conduits, value -> config.conduits = value));
		Component conduitColorDescription = Component.translatable("beaconrange.config.conduit_color.desc");
		list.add(option(Component.translatable("beaconrange.config.conduit_color"), value -> conduitColorDescription,
				CycleButton.<BeaconRangeConfig.BoxColor>builder(BeaconRangeConfig.BoxColor::label)
						.withValues(BeaconRangeConfig.BoxColor.values())
						.withInitialValue(config.conduitColor),
				defaults.conduitColor, value -> config.conduitColor = value));
		list.add(toggle("beaconrange.config.conduit_attack", config.conduitAttack, defaults.conduitAttack,
				value -> config.conduitAttack = value));

		list.add(new SectionRow(Component.translatable("beaconrange.config.section.pinned")));
		list.add(toggle("beaconrange.config.auto_unpin", config.autoUnpin, defaults.autoUnpin,
				value -> config.autoUnpin = value));
		list.add(new PinsRow());

		list.add(new SectionRow(Component.translatable("beaconrange.config.section.help")));
		list.add(new TutorialRow());
		this.list = this.layout.addToContents(list);

		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("beaconrange.config.reset_all"),
				button -> this.resetters.forEach(Runnable::run)).width(150).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(150).build());

		this.layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	private OptionRow<Boolean> toggle(String key, boolean value, boolean defaultValue, Consumer<Boolean> setter) {
		Component description = Component.translatable(key + ".desc");
		return option(Component.translatable(key), v -> description, CycleButton.booleanBuilder(ON, OFF).withInitialValue(value),
				defaultValue, setter);
	}

	private OptionRow<BeaconRangeConfig.BoxColor> levelColor(int level, BeaconRangeConfig.BoxColor value,
			BeaconRangeConfig.BoxColor defaultValue, Consumer<BeaconRangeConfig.BoxColor> setter) {
		Component description = Component.translatable("beaconrange.config.level.desc", Beacons.range(level));
		return option(Component.translatable("beaconrange.config.level", level), v -> description,
				CycleButton.<BeaconRangeConfig.BoxColor>builder(BeaconRangeConfig.BoxColor::label)
						.withValues(BeaconRangeConfig.BoxColor.values())
						.withInitialValue(value),
				defaultValue, setter);
	}

	private <T> OptionRow<T> option(Component label, Function<T, Component> description,
			CycleButton.Builder<T> builder, T defaultValue, Consumer<T> setter) {
		OptionRow<T> row = new OptionRow<>(label, description, defaultValue, setter);
		row.control = builder.displayOnlyValue().create(0, 0, CONTROL_WIDTH, 20, label, (button, value) -> row.apply(value));
		row.refresh();
		this.resetters.add(row::reset);
		return row;
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}
	}

	/** Auch speichern, wenn etwas anderes den Bildschirm ersetzt (Verbindung weg, Tod) - dann gibt es kein onClose. */
	@Override
	public void removed() {
		BeaconRangeClient.getConfig().save();
	}

	@Override
	public void onClose() {
		BeaconRangeClient.getConfig().save();
		this.minecraft.setScreen(this.parent);
	}

	/**
	 * Die Liste mit Zeilen verschiedener Höhe (Überschriften sind niedriger). In 1.21.4 kennt die Liste nur eine feste
	 * Zeilenhöhe, deshalb rechnen wir die Lage jeder Zeile selbst aus - genau wie die Liste ab 1.21.9.
	 */
	private class OptionList extends ContainerObjectSelectionList<Row> {

		OptionList(Minecraft minecraft, int width, HeaderAndFooterLayout layout) {
			super(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight(), OPTION_HEIGHT);
		}

		void add(Row row) {
			addEntry(row);
		}

		@Override
		public int getRowWidth() {
			return Math.min(this.width - 40, 440);
		}

		/** Wie ab 1.21.9 acht Pixel rechts neben den Zeilen - dort beginnen die Zeilen zwei Pixel weiter links. */
		@Override
		protected int scrollBarX() {
			return getRowRight() + 6;
		}

		@Override
		protected int contentHeight() {
			int height = 4;
			for (Row row : children()) {
				height += row.height();
			}
			return height;
		}

		@Override
		public int getRowTop(int index) {
			int y = getY() + 2 - (int) scrollAmount();
			for (int i = 0; i < index; i++) {
				y += getEntry(i).height();
			}
			return y;
		}

		@Override
		public int getRowBottom(int index) {
			return getRowTop(index) + getEntry(index).height();
		}

		/** Jede Zeile bekommt ihre Lage; danach zeichnen wir die sichtbaren selbst (die Vorlage nimmt die feste Höhe). */
		private void positionRows() {
			int y = getRowTop(0);
			for (Row row : children()) {
				row.setBounds(getRowLeft() - 2, y, getRowWidth(), row.height());
				y += row.height();
			}
		}

		private @Nullable Row rowAt(double mouseX, double mouseY) {
			if (!isMouseOver(mouseX, mouseY)) {
				return null;
			}
			positionRows();
			for (Row row : children()) {
				if (row.isMouseOver(mouseX, mouseY)) {
					return row;
				}
			}
			return null;
		}

		@Override
		public Optional<GuiEventListener> getChildAt(double mouseX, double mouseY) {
			return Optional.ofNullable(rowAt(mouseX, mouseY));
		}

		@Override
		protected void renderListItems(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			Row hovered = rowAt(mouseX, mouseY);
			positionRows();
			for (Row row : children()) {
				if (row.y + row.height >= getY() && row.y <= getBottom()) {
					row.renderContent(graphics, mouseX, mouseY, row == hovered, partialTick);
				}
			}
		}

		/** Mit der Tastatur angesteuerte Zeile ganz ins Bild holen. */
		@Override
		protected void ensureVisible(Row row) {
			int index = children().indexOf(row);
			int top = getRowTop(index), bottom = getRowBottom(index);
			if (top < getY()) {
				setScrollAmount(scrollAmount() + top - getY());
			} else if (bottom > getBottom()) {
				setScrollAmount(scrollAmount() + bottom - getBottom());
			}
		}
	}

	/** Kreispfeil als Pixelgrafik – als Schriftzeichen wäre er im Spiel kaum zu erkennen. */
	private static final String[] RESET_ICON = {
			"..###.#",
			".#...##",
			"#...###",
			"#......",
			"#.....#",
			".#...#.",
			"..###..",
	};

	private static void drawResetIcon(GuiGraphics graphics, int x, int y, int color) {
		for (int row = 0; row < RESET_ICON.length; row++) {
			for (int col = 0; col < RESET_ICON[row].length(); col++) {
				if (RESET_ICON[row].charAt(col) == '#') {
					graphics.fill(x + col, y + row, x + col + 1, y + row + 1, color);
				}
			}
		}
	}

	/**
	 * Eine Zeile mit eigener Lage und Höhe - die Maße des Inhalts wie bei AbstractSelectionList.Entry ab 1.21.9
	 * (zwei Pixel Rand rundherum).
	 */
	private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
		private int x, y, width, height;

		abstract int height();

		abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick);

		void setBounds(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		/** Gezeichnet wird über {@link OptionList#renderListItems} mit den eigenen Maßen. */
		@Override
		public final void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovered, float partialTick) {
			renderContent(graphics, mouseX, mouseY, hovered, partialTick);
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
		}

		int getContentX() {
			return this.x + 2;
		}

		int getContentY() {
			return this.y + 2;
		}

		int getContentWidth() {
			return this.width - 4;
		}

		int getContentHeight() {
			return this.height - 4;
		}

		int getContentRight() {
			return getContentX() + getContentWidth();
		}

		int getContentBottom() {
			return getContentY() + getContentHeight();
		}

		int getContentXMiddle() {
			return getContentX() + getContentWidth() / 2;
		}
	}

	/** Überschrift eines Abschnitts: Titel in der Akzentfarbe, links und rechts eine auslaufende Linie. */
	private class SectionRow extends Row {
		private final Component title;

		SectionRow(Component title) {
			this.title = title.copy().withStyle(ChatFormatting.BOLD);
		}

		@Override
		int height() {
			return SECTION_HEIGHT;
		}

		@Override
		void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			Font font = BeaconRangeConfigScreen.this.font;
			int textWidth = font.width(this.title);
			int centerX = getContentXMiddle();
			int textY = getContentBottom() - 12;
			graphics.drawString(font, this.title, centerX - textWidth / 2, textY, COLOR_ACCENT, true);

			int lineY = textY + 4;
			int inner = textWidth / 2 + 8;
			fadeLine(graphics, getContentX(), centerX - inner, lineY, true);
			fadeLine(graphics, centerX + inner, getContentRight(), lineY, false);
		}

		/** Waagerechte Linie, die zum Rand hin ausblendet. */
		private static void fadeLine(GuiGraphics graphics, int from, int to, int y, boolean fadeIn) {
			int steps = 12;
			float span = (to - from) / (float) steps;
			for (int i = 0; i < steps; i++) {
				float strength = fadeIn ? (i + 1) / (float) steps : (steps - i) / (float) steps;
				int alpha = (int) (strength * 0xC0);
				int x1 = from + Math.round(span * i);
				int x2 = from + Math.round(span * (i + 1));
				graphics.fill(x1, y, x2, y + 1, (alpha << 24) | (COLOR_ACCENT & 0xFFFFFF));
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}
	}

	/** Gemeinsames Aussehen aller Einstellungs-Zeilen: Text links, Bedienelement und Zurücksetzen-Knopf rechts. */
	private void renderOption(Row row, GuiGraphics graphics, Component label, Component description,
			AbstractWidget control, @Nullable Button resetButton, int mouseX, int mouseY, boolean hovered,
			float partialTick) {
		if (hovered) {
			graphics.fill(row.getContentX() - 4, row.getContentY() - 1,
					row.getContentRight() + 4, row.getContentBottom() + 1, COLOR_ROW_HOVER);
		}

		// Auch Zeilen ohne Zurücksetzen-Knopf halten dessen Platz frei, damit alle Bedienelemente in einer Spalte stehen.
		int buttonY = row.getContentY() + (row.getContentHeight() - 20) / 2;
		int resetX = row.getContentRight() - RESET_WIDTH;
		control.setX(resetX - GAP - CONTROL_WIDTH);
		control.setY(buttonY);
		if (resetButton != null) {
			resetButton.setX(resetX);
			resetButton.setY(buttonY);
		}

		int textWidth = control.getX() - 10 - row.getContentX();
		List<FormattedCharSequence> lines = this.font.split(description, textWidth);
		if (lines.size() > 2) {
			// Passt nicht in zwei Zeilen (schmales Fenster): nach ganzen Wörtern kürzen und mit "…" enden.
			String text = description.getString();
			int cut = text.length();
			while (cut > 0 && this.font.split(Component.literal(text.substring(0, cut).stripTrailing() + "…"), textWidth).size() > 2) {
				cut = text.lastIndexOf(' ', cut - 1);
			}
			lines = this.font.split(Component.literal(text.substring(0, Math.max(cut, 0)).stripTrailing() + "…"), textWidth);
		}
		int shown = Math.min(lines.size(), 2);
		int textY = row.getContentY() + (row.getContentHeight() - (11 + shown * 9)) / 2;
		graphics.drawString(this.font, label, row.getContentX(), textY, COLOR_LABEL, true);
		for (int i = 0; i < shown; i++) {
			graphics.drawString(this.font, lines.get(i), row.getContentX(), textY + 11 + i * 9, COLOR_DESCRIPTION, false);
		}

		control.render(graphics, mouseX, mouseY, partialTick);
		if (resetButton != null) {
			resetButton.render(graphics, mouseX, mouseY, partialTick);
			drawResetIcon(graphics, resetButton.getX() + (RESET_WIDTH - 7) / 2, buttonY + 6,
					resetButton.active ? COLOR_LABEL : 0xFF6B6F75);
		}
	}

	private Button createResetButton(Runnable action) {
		return Button.builder(Component.empty(), button -> action.run())
				.size(RESET_WIDTH, 20)
				.tooltip(Tooltip.create(Component.translatable("beaconrange.config.reset")))
				.build();
	}

	/** Zeigt die kurze Tour durch die Mod noch einmal. */
	private class TutorialRow extends Row {
		private final Component label = Component.translatable("beaconrange.config.tutorial");
		private final Component description = Component.translatable("beaconrange.config.tutorial.desc");
		private final Button showButton = Button.builder(Component.translatable("beaconrange.config.tutorial.show"),
				button -> Onboarding.open(BeaconRangeClient.MOD_ID)).size(CONTROL_WIDTH, 20).build();

		@Override
		int height() {
			return OPTION_HEIGHT;
		}

		@Override
		void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			renderOption(this, graphics, this.label, this.description, this.showButton, null,
					mouseX, mouseY, hovered, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(this.showButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(this.showButton);
		}
	}

	/** Wie viele Beacons angepinnt sind, und ein Knopf, der alle in dieser Welt löst. */
	private class PinsRow extends Row {
		private final Component label = Component.translatable("beaconrange.config.pins");
		private final Button clearButton = Button.builder(Component.translatable("beaconrange.config.pins.clear"),
				button -> confirmClear()).size(CONTROL_WIDTH, 20).build();

		/** Alles lösen lässt sich nicht rückgängig machen - erst fragen. */
		private void confirmClear() {
			Minecraft client = BeaconRangeConfigScreen.this.minecraft;
			int count = BeaconRangeClient.getPins().countWorld(client);
			client.setScreen(new ConfirmScreen(yes -> {
				if (yes) {
					BeaconRangeClient.getPins().clearWorld(client);
				}
				client.setScreen(BeaconRangeConfigScreen.this);
			}, Component.translatable("beaconrange.config.pins.clear.title"),
					Component.translatable("beaconrange.config.pins.clear.question", count)));
		}

		@Override
		int height() {
			return OPTION_HEIGHT;
		}

		@Override
		void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			Minecraft client = BeaconRangeConfigScreen.this.minecraft;
			PinStore pins = BeaconRangeClient.getPins();
			Component description;
			if (PinStore.worldKey(client) != null) {
				int here = pins.countWorld(client);
				description = Component.translatable("beaconrange.config.pins.world", here, pins.countTotal());
				this.clearButton.active = here > 0;
			} else {
				description = Component.translatable("beaconrange.config.pins.menu", pins.countTotal());
				this.clearButton.active = false;
			}
			renderOption(this, graphics, this.label, description, this.clearButton, null,
					mouseX, mouseY, hovered, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(this.clearButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(this.clearButton);
		}
	}

	/** Eine Einstellung: Name und Erklärung links, Schalter und Zurücksetzen-Knopf rechts. */
	private class OptionRow<T> extends Row {
		private final Component label;
		private final Function<T, Component> description;
		private final T defaultValue;
		private final Consumer<T> setter;
		private final Button resetButton;
		private CycleButton<T> control;

		OptionRow(Component label, Function<T, Component> description, T defaultValue, Consumer<T> setter) {
			this.label = label;
			this.description = description;
			this.defaultValue = defaultValue;
			this.setter = setter;
			this.resetButton = createResetButton(this::reset);
		}

		void apply(T value) {
			this.setter.accept(value);
			refresh();
		}

		void reset() {
			this.control.setValue(this.defaultValue);
			apply(this.defaultValue);
		}

		/** Der Zurücksetzen-Knopf ist nur aktiv, wenn es etwas zurückzusetzen gibt. */
		void refresh() {
			this.resetButton.active = !Objects.equals(this.control.getValue(), this.defaultValue);
		}

		@Override
		int height() {
			return OPTION_HEIGHT;
		}

		@Override
		void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			renderOption(this, graphics, this.label, this.description.apply(this.control.getValue()),
					this.control, this.resetButton, mouseX, mouseY, hovered, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(this.control, this.resetButton);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(this.control, this.resetButton);
		}
	}
}
