package mekanism.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import org.lwjgl.glfw.GLFW;

public abstract class MekKeyHandler {

    /**
     * KeyBinding instances
     */
    private KeyBinding[] keyBindings;

    /**
     * Track which keys have been seen as pressed currently
     */
    private BitSet keyDown;

    /**
     * Whether keys send repeated KeyDown pseudo-messages
     */
    private BitSet repeatings;

    /**
     * Pass an array of keybindings and a repeat flag for each one
     *
     * @param bindings Bindings to set
     */
    public MekKeyHandler(Builder bindings) {
        keyBindings = bindings.getBindings();
        repeatings = bindings.getRepeatFlags();
        keyDown = new BitSet();
    }

    public static boolean getIsKeyPressed(KeyBinding keyBinding) {
        try {
            InputMappings.Input key = keyBinding.getKey();
            int keyCode = key.getKeyCode();
            if (keyCode != InputMappings.INPUT_INVALID.getKeyCode()) {
                long windowHandle = Minecraft.getInstance().getMainWindow().getHandle();
                if (key.getType() == InputMappings.Type.KEYSYM) {
                    return InputMappings.isKeyDown(windowHandle, keyCode);
                } else if (key.getType() == InputMappings.Type.MOUSE) {
                    return GLFW.glfwGetMouseButton(windowHandle, keyCode) == GLFW.GLFW_PRESS;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public void keyTick() {
        for (int i = 0; i < keyBindings.length; i++) {
            KeyBinding keyBinding = keyBindings[i];
            boolean state = keyBinding.isKeyDown();
            boolean lastState = keyDown.get(i);
            if (state != lastState || (state && repeatings.get(i))) {
                if (state) {
                    keyDown(keyBinding, state == lastState);
                } else {
                    keyUp(keyBinding);
                }
                keyDown.set(i, state);
            }
        }
    }

    public abstract void keyDown(KeyBinding kb, boolean isRepeat);

    public abstract void keyUp(KeyBinding kb);

    public static class Builder {

        private List<KeyBinding> bindings = new ArrayList<>(3);
        private BitSet repeatFlags = new BitSet();

        /**
         * Add a keybinding to the list
         *
         * @param k          the KeyBinding to add
         * @param repeatFlag true if keyDown pseudo-events continue to be sent while key is held
         */
        public Builder addBinding(KeyBinding k, boolean repeatFlag) {
            repeatFlags.set(bindings.size(), repeatFlag);
            bindings.add(k);
            return this;
        }

        protected BitSet getRepeatFlags() {
            return repeatFlags;
        }

        protected KeyBinding[] getBindings() {
            return bindings.toArray(new KeyBinding[0]);
        }
    }
}