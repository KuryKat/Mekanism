package mekanism.client.gui.element.button;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.ObjIntConsumer;
import mekanism.api.text.EnumColor;
import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.content.filter.FilterManager;
import mekanism.common.content.filter.IFilter;
import mekanism.common.content.filter.IItemStackFilter;
import mekanism.common.content.filter.IMaterialFilter;
import mekanism.common.content.filter.IModIDFilter;
import mekanism.common.content.filter.ITagFilter;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MovableFilterButton extends FilterButton {

    private final FilterSelectButton upButton;
    private final FilterSelectButton downButton;

    public MovableFilterButton(IGuiWrapper gui, int x, int y, int index, IntSupplier filterIndex, FilterManager<?> filterManager, IntConsumer upButtonPress,
          IntConsumer downButtonPress, ObjIntConsumer<IFilter<?>> onPress, IntConsumer toggleButtonPress, Function<IFilter<?>, List<ItemStack>> renderStackSupplier) {
        this(gui, x, y, TEXTURE_WIDTH, TEXTURE_HEIGHT / 2, index, filterIndex, filterManager, upButtonPress, downButtonPress, onPress, toggleButtonPress, renderStackSupplier);
    }

    public MovableFilterButton(IGuiWrapper gui, int x, int y, int width, int height, int index, IntSupplier filterIndex, FilterManager<?> filterManager,
          IntConsumer upButtonPress, IntConsumer downButtonPress, ObjIntConsumer<IFilter<?>> onPress, IntConsumer toggleButtonPress,
          Function<IFilter<?>, List<ItemStack>> renderStackSupplier) {
        super(gui, x, y, width, height, index, filterIndex, filterManager, onPress, toggleButtonPress, renderStackSupplier);
        int arrowX = relativeX + width - 12;
        upButton = addPositionOnlyChild(new FilterSelectButton(gui, arrowX, relativeY + 1, false,
              () -> upButtonPress.accept(getActualIndex()), getOnHover(MekanismLang.MOVE_UP)));
        downButton = addPositionOnlyChild(new FilterSelectButton(gui, arrowX, relativeY + height - 8, true,
              () -> downButtonPress.accept(getActualIndex()), getOnHover(MekanismLang.MOVE_DOWN)));
    }

    @Override
    protected int getToggleXShift() {
        return 13;
    }

    @Override
    protected int getToggleYShift() {
        return 1;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (upButton.isMouseOver(mouseX, mouseY)) {
            upButton.onClick(mouseX, mouseY);
        } else if (downButton.isMouseOver(mouseX, mouseY)) {
            downButton.onClick(mouseX, mouseY);
        } else {
            super.onClick(mouseX, mouseY);
        }
    }

    @Override
    public void renderForeground(PoseStack matrix, int mouseX, int mouseY) {
        int xAxis = mouseX - getGuiLeft(), yAxis = mouseY - getGuiTop();
        if (upButton.isMouseOverCheckWindows(mouseX, mouseY)) {
            upButton.renderToolTip(matrix, xAxis, yAxis);
        } else if (downButton.isMouseOverCheckWindows(mouseX, mouseY)) {
            downButton.renderToolTip(matrix, xAxis, yAxis);
        }
        super.renderForeground(matrix, mouseX, mouseY);
    }

    @Override
    protected void setVisibility(boolean visible) {
        super.setVisibility(visible);
        if (visible) {
            updateButtonVisibility();
        } else {
            //Ensure the subcomponents are not marked as visible
            upButton.visible = false;
            downButton.visible = false;
        }
    }

    private void updateButtonVisibility() {
        int index = getActualIndex();
        IFilter<?> filter = getFilter();
        upButton.visible = filter != null && index > 0;
        downButton.visible = filter != null && index < filterManager.count() - 1;
    }

    @Override
    public void drawBackground(@NotNull PoseStack matrix, int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(matrix, mouseX, mouseY, partialTicks);
        IFilter<?> filter = getFilter();
        EnumColor color;
        if (filter instanceof IItemStackFilter) {
            color = EnumColor.INDIGO;
        } else if (filter instanceof ITagFilter) {
            color = EnumColor.BRIGHT_GREEN;
        } else if (filter instanceof IMaterialFilter) {
            color = EnumColor.PINK;
        } else if (filter instanceof IModIDFilter) {
            color = EnumColor.RED;
        } else {
            color = null;
        }
        if (color != null) {
            GuiUtils.fill(matrix, x, y, width, height, MekanismRenderer.getColorARGB(color, 0.5F));
            MekanismRenderer.resetColor();
        }
        updateButtonVisibility();
        //Render our sub buttons and our slot
        upButton.onDrawBackground(matrix, mouseX, mouseY, partialTicks);
        downButton.onDrawBackground(matrix, mouseX, mouseY, partialTicks);
    }

    @Override
    protected int getMaxLength() {
        return super.getMaxLength() - 12;
    }
}